# Matching Engine Design

This document details the matching algorithm, spatial indexing strategy, ETA integration, and scalability approach for handling 10,000+ concurrent rides.

---

## 1. Matching Engine Overview

### Purpose
Transform a ride request into a driver assignment within <200ms latency, considering:
- Spatial proximity (drivers near pickup location)
- Driver quality (rating, acceptance rate)
- Vehicle capacity (can accommodate passengers)
- ETA to pickup (minimize rider wait time)
- Demand-supply balance (surge pricing signals)

### Architecture

```
Event Stream (Kafka)          Matching Engine                  Dependent Services
       │                            │                                 │
       ├─ RideRequested Event      │                                 │
       │─────────────────────────>├─ Query Location Service         │
       │                          │  (find nearby drivers)           │
       │                          │                                  │
       │                          ├─ Fetch Driver Details ────────>│Driver Service
       │                          │  (rating, capacity)             │
       │                          │                                 │
       │                          ├─ Calculate ETAs ──────────────>│ETA Service
       │                          │  (distance-based estimates)     │
       │                          │                                 │
       │                          ├─ Rank Drivers                  │
       │                          │  (apply scoring formula)        │
       │                          │                                 │
       │                          ├─ Assign Top Driver             │
       │                          │  (transactional update)         │
       │                          │                                 │
       │                    [Publish RideMatched Event]            │
       │<─────────────────────────┤                                 │
```

### Key Constraints

- **Latency**: <200ms (95th percentile, <150ms 50th percentile)
- **Throughput**: 10,000 concurrent ride requests
- **Scale**: 100,000+ online drivers
- **Availability**: 99.99% uptime (no single point of failure)

---

## 2. Spatial Discovery (Find Nearby Drivers)

### Strategy: Redis Geo Index

**Why Redis Geo?**
- Geographic sorted set: O(log N) insertion, O(N log N) range queries
- Memory resident: Sub-millisecond query latency
- Atomic operations: No race conditions
- Cluster mode: Horizontal scaling

**Data Structure**
```
Redis Key: driver_locations
Members: driver_id with (latitude, longitude)

Example:
GEOADD driver_locations 40.7128 -74.0060 D123
GEOADD driver_locations 40.7150 -74.0050 D456
...

Query:
GEORADIUS driver_locations 40.7128 -74.0060 5 km
  → Returns [D456, D789, D012, ...]  (drivers within 5km radius)
```

### Discovery Algorithm

```
Input:
  ride_id, pickup_location {lat, lng}, service_type

Execution:
1. Determine search radius (km):
   - Urban (population density > 1000/km²): 5 km
   - Suburban (100-1000/km²): 10 km
   - Rural (< 100/km²): 20 km
   - Adjust based on queue wait time (if queue growing, expand radius)

2. Query Redis:
   GEORADIUS driver_locations {lat} {lng} {radius} km
     WITHCOORD         (return lat/lng)
     WITHDIST          (return distance in km)
     COUNT 100         (return top 100 drivers)

3. Filter candidates (all must pass):
   - status = 'AVAILABLE' (not on ride, online)
   - service_type match (ECONOMY can serve SHARED, but not vice versa)
   - vehicle_capacity >= ride.passenger_count
   - acceptance_rate >= MIN_THRESHOLD (configurable, e.g., 60%)

4. Return: M drivers (typically 10-50 in urban, 5-20 in rural)

Latency budget: 10ms
```

### Handling Scale

**Problem**: 100k+ drivers, each with frequent location updates.

**Solution**: Redis Cluster with geohash partitioning

```
Cluster Setup:
  - 16 Redis nodes (geohash-partitioned)
  - Each node handles ~6,250 drivers
  - Replication: 1 primary + 1 replica per node

Query Coordination:
  - Client: Calculate which Redis nodes cover the search radius
  - Parallel query all nodes
  - Merge results (union of drivers from all nodes)
  - Sort by distance globally
  - Return top 100

Example:
  Radius: 5km from (40.7128, -74.0060)
  Geohash covers area: ~4.89m² precision
  Nodes queried: 8-12 nodes (covering the 5km radius)
  Query latency: max(8 serial) × 2ms = 16ms (acceptable)
```

**Alternative: Sharding by Location Grid**

```
Instead of Redis Cluster, use location grid sharding:

Grid cell size: 1km x 1km
Cell index: (lat // 0.01) + ',' + (lng // 0.01)

Example:
  Point (40.7128, -74.0060)
  Cell: (4071, -7401)
  Redis key: loc:4071:-7401

Search:
  radius = 5km
  Query cells within 5km of center
  Cells = 5² = ~25 cells to query
  Merge results from 25 Redis keys

Advantage:
  - Simpler than geohash partitioning
  - Easier to debug (cell coordinates visible)
  - Cache-friendly (cells don't change frequently)

Disadvantage:
  - Edge cases (query spanning cell boundaries)
  - More keys to manage
```

---

## 3. Driver Filtering & Enrichment

### Availability Check

```
Input: [D123, D456, D789, ...] (from spatial discovery)

For each driver:
  1. Check real-time status (from cache/DB)
     - status = 'AVAILABLE'?
     - NOT on active ride?
     - NOT vehicle full (current_passengers < capacity)?
     - NOT on break?

  2. Check static info (from driver service, cached)
     - acceptance_rate >= 60%?
     - license valid?
     - vehicle inspection valid?
     - background check valid?

  3. Apply business rules
     - service_type match?
     - passenger accessibility requirements?
     - driver preferred zones?

Filter pass rate: ~60-80% of nearby drivers
Result: M drivers (10-50)

Latency: ~20-30ms (mostly network roundtrips)
```

### Parallel Enrichment

**Problem**: Fetching details for 50 drivers sequentially = 50 × 20ms = 1000ms ❌

**Solution: Batch HTTP + Thread Pool**

```
Implementation:
1. Batch drivers into groups of 10
2. Launch parallel HTTP requests (thread pool, size = 8)
3. Concurrent requests: 8 drivers at a time
4. Total: 50 / 8 ≈ 7 batches × 20ms = 140ms ❌ (still too slow)

Better solution: Implement driver service batch endpoint

GET /drivers/batch
Body: {driver_ids: [D123, D456, ...]}
Response: [{driver}, ...]

Latency: 1 × 30ms for 50 drivers ✓
```

**Cache Strategy**

```
Cache key: cache:driver:D123
Value: {
  driver_id, rating, acceptance_rate,
  vehicle_capacity, vehicle_type,
  background_check_valid, license_valid
}
TTL: 5 minutes (after update, invalidate)
Cache hit rate: ~95% (driver details change infrequently)

If cache miss:
  - Fetch from driver service
  - Update cache
  - Return result
```

---

## 4. ETA Calculation & Caching

### ETA Service Integration

**What ETAs do we need?**
1. ETA from driver location → pickup location
2. ETA from pickup → dropoff location
3. Both used in ranking (minimize rider wait)

### Fast Path: Route Cache

```
Approach: Pre-calculate & cache common routes

Grid-based caching:
  - Divide service area into 1km × 1km cells
  - For every cell pair, store average travel time
  - Update every hour with traffic data

Route cache key:
  from_cell: (lat // 0.01, lng // 0.01)
  to_cell: (lat // 0.01, lng // 0.01)
  time_of_day: 0-23
  day_of_week: 0-6

Example lookup:
  from_cell = (4071, -7401)
  to_cell = (4075, -7395)
  time = 14:30 (hour = 14)
  day = Wednesday (2)
  Key: route:4071:-7401:4075:-7395:14:2
  Value: 12 minutes (average)

Cache hit rate: ~80% in dense urban areas
Cache miss cost: Call ETA service (Google Maps, OSRM)

Latency:
  Cache hit: <1ms
  Cache miss: 50-100ms (API call)
  Weighted: 0.8 × 1ms + 0.2 × 75ms = 16ms
```

### Fallback: Distance-based Estimate

```
If cache miss AND ETA service unavailable:
  Use distance-based estimate

Assumption: Average speed = 25 mph (40 km/h)

Example:
  Distance: 3 km
  Time: 3 km / 40 km/h = 0.075 hours = 4.5 minutes

Accuracy:
  ±30% error in real-world conditions
  Acceptable for ranking (relative ordering is more important)
```

### ETA Ranking Impact

```
Ranking formula (simplified):
SCORE = w1 * distance_score + w2 * eta_score + w3 * rating_score

Example:
  Driver A: 2km away, 8 min ETA, 4.8 rating
  Driver B: 1km away, 5 min ETA, 4.2 rating

  distance_score_A = 1 - (2/10) = 0.8
  distance_score_B = 1 - (1/10) = 0.9

  eta_score_A = 1 - (8/20) = 0.6
  eta_score_B = 1 - (5/20) = 0.75

  rating_score_A = 4.8/5 = 0.96
  rating_score_B = 4.2/5 = 0.84

  SCORE_A = 0.4×0.8 + 0.3×0.6 + 0.3×0.96 = 0.32 + 0.18 + 0.288 = 0.788
  SCORE_B = 0.4×0.9 + 0.3×0.75 + 0.3×0.84 = 0.36 + 0.225 + 0.252 = 0.837

  Result: Driver B selected (closer, shorter ETA)
```

---

## 5. Ranking Algorithm

### Scoring Formula

```
Final Score = w1*D + w2*R + w3*A + w4*E + w5*S

Where:
  D = Distance Score (normalized 0-1, 1 = closest)
  R = Rating Score (driver_rating / 5.0)
  A = Acceptance Score (acceptance_rate / 100)
  E = ETA Score (normalized 0-1, 1 = fastest)
  S = Surge Impact Score (adjustment for demand)

Weights (configurable):
  w1 = 0.4   (distance is primary factor for rider experience)
  w2 = 0.15  (quality)
  w3 = 0.15  (reliability)
  w4 = 0.2   (ETA to minimize wait)
  w5 = 0.1   (surge adjustment)
  Sum = 1.0

Implementation:
```python
def calculate_score(driver, ride_request):
    # Normalize distance (0-1)
    distance = driver.distance_to_pickup_km
    max_distance = 10  # km
    distance_score = 1 - (distance / max_distance)
    distance_score = max(0, distance_score)  # Clamp to [0, 1]

    # Rating score (already normalized)
    rating_score = driver.average_rating / 5.0

    # Acceptance score (already normalized)
    acceptance_score = driver.acceptance_rate / 100.0

    # ETA score (0-1)
    eta_minutes = driver.eta_to_pickup_minutes
    max_eta = 20  # minutes
    eta_score = 1 - (eta_minutes / max_eta)
    eta_score = max(0, eta_score)

    # Surge adjustment (if demand high)
    surge_multiplier = ride_request.surge_multiplier  # 1.0 - 3.0
    surge_score = (surge_multiplier - 1) / 2  # Map 1.0->0, 3.0->1.0

    # Weighted sum
    final_score = (
        0.4 * distance_score +
        0.15 * rating_score +
        0.15 * acceptance_score +
        0.2 * eta_score +
        0.1 * surge_score
    )

    return final_score
```

### Selection Strategy: Single vs. Multiple Offers

**Option A: Single Driver Assignment (simpler)**
```
1. Rank drivers by score
2. Select top driver
3. Attempt assignment in database (transactional)
4. If success: notify driver
5. If failure (race condition): retry with next best driver
```

**Option B: Multi-Offer (higher acceptance, slower)**
```
1. Rank drivers by score
2. Select top 3 drivers
3. Send offer to all 3 simultaneously
4. Assign to first responder (who accepts)
5. Cancel offers to others

Pros:
  - Higher acceptance rate (3x more likely someone accepts)
  - Better user experience (faster assignment)
  - Handles driver rejections gracefully

Cons:
  - More complex state management
  - More notifications sent
  - Potential race condition (multiple drivers accept)

Recommended: Multi-offer for better UX
```

---

## 6. Assignment & Persistence

### Transactional Assignment

```sql
-- Atomic transaction: prevent double assignment

BEGIN TRANSACTION;

UPDATE rides
SET driver_id = $1,
    status = 'MATCHED',
    matched_at = NOW(),
    expires_at = NOW() + INTERVAL '30 seconds'
WHERE ride_id = $2
  AND status = 'REQUESTED'
  AND expires_at > NOW();  -- Ride not expired

IF rows_updated == 0:
  ROLLBACK;
  RETURN CONFLICT (another driver beat us)

ELSE:
  INSERT INTO ride_events (...)
    VALUES ('RideMatched', ...)

  COMMIT;
  RETURN SUCCESS
```

**Race Condition Handling**
```
Scenario: 2 matching engines simultaneously select different drivers for same ride

Timeline:
  T0: Matching Engine A queries ride.status = 'REQUESTED'
  T1: Matching Engine B queries ride.status = 'REQUESTED'
  T2: Engine A executes UPDATE → Success (rows_updated = 1)
  T3: Engine B executes UPDATE → Conflict (rows_updated = 0)

Result:
  Engine A: Success, driver D123 assigned
  Engine B: Failure, return error to client
  Client (via Ride Service): Detects 409, initiates new ride request

Recovery:
  - Rider automatically prompted to request new ride
  - No duplicate charges (each ride is separate request)
```

---

## 7. Scalability: Handling 10k+ Concurrent Rides

### The Challenge

```
Peak load: 10,000 concurrent ride requests
Processing time per ride: 100ms (total across all phases)
Throughput needed: 10,000 / 0.1s = 100,000 requests/sec

Single Matching Engine instance:
  - CPU cores: 16 (optimal for I/O bound tasks)
  - Latency per request: 100ms
  - Throughput: 16 * 10 = 160 req/sec ❌ (way too low)

Solution: Horizontal scaling via request sharding
```

### Horizontal Scaling: Request Sharding

**Strategy 1: Shard by Ride ID**

```
Distributed Setup:
  - 64 matching engine instances
  - Shard key: ride_id % 64
  - Request router: API Gateway determines shard

Throughput:
  Per instance: 100,000 / 64 ≈ 1,562 req/sec per instance
  Available parallelism: 16 cores per instance
  Req/sec per core: 1,562 / 16 ≈ 98 req/sec (achievable)

Advantage:
  - Linear scaling
  - No cross-shard communication
  - Simple implementation (hash-based)

Disadvantage:
  - Each shard needs independent Redis Geo access (or central Redis bottleneck)
```

**Architecture with Request Sharding**

```
API Gateway                 Matching Engine Shards
    │                                │
    ├─ POST /rides ──────────┐       │
    │  ride_id = R123        │       │
    │                        │       │
    │  Shard = R123 % 64     │       │
    │  = 15                  │       │
    │                        │       │
    │                        └──────>├─ Shard 15
    │                               │
    │                          [Matching Logic]
    │                               │
    │                          [Update DB]
    │                               │
    │                          [Publish Event]
    │                               │
    │<──────────────────────────────┤
```

### Database Sharding (Ride Table)

```
8 ride database shards + 64 matching engine shards:

Mapping:
  ride_id % 64 → Matching engine shard (request routing)
  ride_id % 8  → Ride database shard (persistence)

Why different numbers?
  - 64 matching engines needed for throughput
  - 8 database shards sufficient for write volume (64 / 8 = 8 matching engines per DB shard)
  - Trade-off between compute and storage shards
```

### Redis Geo Access Pattern

**Shared Redis (Central Bottleneck)**
```
Problem: All 64 matching engines query central Redis
Result: Redis becomes bottleneck (can't handle 100k req/sec)

Solution: Not viable
```

**Redis Cluster (Distributed)**
```
Setup: 16 Redis cluster nodes, geohash partitioned
Each matching engine:
  1. Calculate which Redis nodes cover search radius
  2. Parallel query all nodes (async)
  3. Merge results
  4. Continue ranking

Advantage:
  - Redis scales horizontally
  - Query parallelism hides latency
  - No single bottleneck
```

### Latency Under Load

```
Scenario: 10k concurrent riders, 100k online drivers

Per-Request Timeline:
  Spatial Discovery (Redis Geo):        15ms  (parallel query 8 Redis nodes)
  Filter & Enrich (batch API call):     30ms  (parallel fetch drivers)
  ETA Calculation (cache hit 80%):      10ms  (mostly cached)
  Ranking (in-memory sort):              2ms
  Assignment (DB write):                 5ms
  Event Publishing (async):             <1ms  (non-blocking)
─────────────────────────────────────────────
  Total P50 latency:                    63ms ✓
  Total P95 latency:                   130ms ✓ (target < 200ms)
  Total P99 latency:                   180ms ✓

Bottleneck Analysis:
  Filter & Enrich dominates (30ms)
  → Opportunity: Cache driver details better, pre-fetch hot drivers
```

### Concurrency & Resource Management

```
Thread Pool Configuration (per Matching Engine instance):

1. Request Handler Thread Pool:
   Size: 256 threads (I/O bound)
   Queue: Unbounded (backpressure via API Gateway rate limiting)

2. Redis Query Thread Pool:
   Size: 32 threads (connection pooling)
   Connections per node: 4 (to 16 Redis nodes = 64 connections)

3. Driver Service Thread Pool:
   Size: 16 threads (HTTP calls)
   Batch size: 10 drivers per request

4. ETA Service Thread Pool:
   Size: 8 threads (external API calls)
   Cache reduces call volume by 80%

Memory:
  Per request: ~1 MB (ride data, driver list, scoring)
  1,000 concurrent requests: ~1 GB
  Heap size: 4 GB (with 3x headroom)
```

---

## 8. Failure Modes & Resilience

### Failure Scenario 1: Location Service Unavailable

```
Detection: Redis Geo query timeout (>5sec)
Action:
  1. Circuit breaker trips (fail-open after 5 consecutive failures)
  2. Fallback: Query driver table directly (slow but works)
  3. Fallback query: SELECT * FROM drivers WHERE status='ONLINE'
  4. Filter by distance: Calculate distance in-app (vs. Redis)
  5. Latency: 100-500ms (acceptable degradation)

Recovery:
  - Redis restored
  - Circuit breaker reset after 30 seconds
  - Normal path resumes
```

### Failure Scenario 2: ETA Service Timeout

```
Detection: ETA API call timeout (>2sec)
Action:
  1. Cancel ETA fetch (abandon slow API call)
  2. Use fallback distance-based estimate
  3. Continue ranking with estimated ETA
  4. Latency impact: Minimal (1-2ms)

Recovery:
  - ETA Service recovers
  - Ranking accuracy improves (cached results used)
```

### Failure Scenario 3: Driver Service Slow

```
Detection: Batch driver fetch timeout (>3sec)
Action:
  1. Timeout individual driver fetches
  2. Use cached data for slow drivers
  3. Continue ranking with partial data
  4. Exclude drivers without cache hits (conservative)
  5. Latency impact: 10-20ms

Recovery:
  - Driver Service recovers
  - Cache hits increase
```

### Failure Scenario 4: Database Write Failure

```
Scenario: Ride shard unreachable; UPDATE fails

Detection: DB timeout or connection refused
Action:
  1. Log failure
  2. Return 503 Service Unavailable to client
  3. Queue assignment request in Redis (backup queue)
  4. Background job processes queue when DB recovers

Recovery:
  - DB shard recovered
  - Background job processes queued assignments
  - Client retries ride request
```

### Monitoring & Alerting

```
Key Metrics (per Matching Engine):

1. Matching Latency (P50, P95, P99):
   Target: P95 < 200ms
   Alert: P95 > 300ms for 5 minutes

2. Success Rate:
   Target: > 99.5%
   Alert: < 99% for 1 minute

3. Driver Assignment Rate:
   Target: > 90% (ratio of matched rides to requests)
   Alert: < 80% (indicates insufficient drivers)

4. Circuit Breaker Status:
   Track: Open/Closed state
   Alert: Open for > 30 seconds

5. Resource Utilization:
   CPU: Alert > 80%
   Memory: Alert > 85%
   Heap: Alert > 80%
```

---

## 9. Surge Pricing Integration

### Surge Calculation

```
Surge Multiplier = f(demand_supply_ratio)

demand_supply_ratio = active_ride_requests / online_drivers

Example:
  1,000 active requests / 10,000 drivers = 0.1 → multiplier = 1.0x (normal)
  5,000 active requests / 10,000 drivers = 0.5 → multiplier = 1.5x
  8,000 active requests / 10,000 drivers = 0.8 → multiplier = 2.5x

Surge Configuration:
  ratio < 0.2: 1.0x (no surge)
  0.2 - 0.4: 1.0x - 1.5x (gradual)
  0.4 - 0.6: 1.5x - 2.0x (moderate)
  0.6 - 0.8: 2.0x - 2.5x (high)
  > 0.8:    2.5x - 3.0x (extreme, hard cap)

Application in Matching:
  Surge impacts driver incentives (payout increases)
  → Encourages drivers to come online during peak
  → Gradually reduces demand/supply imbalance
```

---

## 10. Performance Optimization Techniques

### 1. Request Batching

```
Instead of processing 1 ride request at a time:
  - Batch 10 ride requests
  - Fetch driver details once for all 10
  - Calculate ETAs in batch
  - Parallel ranking

Trade-off:
  Latency: 100ms → 110ms (10ms batching overhead)
  Throughput: 10x (1 request → 10 requests processed together)

Acceptable because latency < 200ms target
```

### 2. Caching Hot Drivers

```
Observation: Few drivers get matched most of the time
  - Top 10% of drivers: 40% of matches
  - These drivers are queried repeatedly

Optimization:
  - Pre-fetch top 1,000 drivers (hot set) into Redis cache
  - Refresh every 5 minutes
  - Hit rate: 60-70% of queries

Result:
  - Driver fetch latency: 1ms (cache) vs. 30ms (API)
  - Matching latency: 30ms improvement
```

### 3. Early Filtering

```
Instead of fetching all 50 drivers then filtering:
  - Filter by acceptance_rate in Redis query (Lua script)
  - Return only 20 pre-filtered drivers
  - Reduce HTTP payload by 60%
  - Fetch latency: 20ms → 12ms
```

### 4. Predictive Caching

```
Observation: ETAs requested for same origin/destination frequently
  - 40% of ETA requests are cache hits naturally
  - 20% could be predicted (historical patterns)

Optimization:
  - Pre-calculate common routes (hotspots)
  - Update cache every hour
  - Hit rate: 40% → 60%

Result:
  - Average ETA latency: 16ms → 10ms
  - Matching latency: 6ms improvement
```

---

## Summary: Matching Engine Design Decisions

| Decision | Rationale | Trade-off |
|----------|-----------|-----------|
| **Redis Geo for spatial index** | Sub-millisecond queries; in-memory; scales well | Extra cache layer; complexity |
| **Batch driver fetch endpoint** | 50x faster than sequential | Requires API design change |
| **Route cache (1km x 1km grid)** | 80% hit rate; huge latency gain | Storage; maintenance |
| **Multi-offer strategy** | Higher acceptance; better UX | Complexity; more notifications |
| **64 matching engine shards** | Linear throughput scaling | Complex routing; debugging |
| **Circuit breakers** | Fault isolation; fast failure | Cascading failures possible |
| **Surge multiplier in ranking** | Incentivizes drivers during peak | User cost increases; trust issues |

---

**Next**: Real-Time Communication, Folder Structure, Non-Functional Requirements
