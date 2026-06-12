# Location Service Architecture

## Overview

The Location Service is engineered for **high-throughput, low-latency location data processing** capable of handling 100k+ driver location updates per second.

## Design Principles

### 1. Extreme Throughput Focus
- **Batching at Core**: Every operation is batched (500 updates or 100ms)
- **Pipelining**: Redis operations use pipelined commands for efficiency
- **Async I/O**: Database writes are asynchronous (5-minute batches)
- **Fire-and-Forget**: Event publishing is non-blocking

### 2. Low Latency for Reads
- **Redis Geo Index**: Sub-millisecond location lookups
- **In-Memory Batch**: BlockingQueue for fast enqueue (< 10ms)
- **Cached Status**: Driver online/offline status cached in Redis

### 3. Data Consistency
- **Eventual Consistency**: Redis is source of truth, PostgreSQL is audit trail
- **Event Sourcing**: Kafka publishes every update for replay capability
- **Idempotent Operations**: Location updates are idempotent (replace, not append to Geo)

### 4. Operational Safety
- **Circuit Breaker Pattern**: Queue overflow drops updates gracefully
- **Separate Thread Pools**: DB writes don't block request processing
- **Bounded Resources**: Fixed-size queues and thread pools

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Driver Apps                               │
│                      (100k+ drivers)                              │
└──────────────────────┬──────────────────────────────────────────┘
                       │ PUT /drivers/location (100k+/sec)
                       ↓
        ┌──────────────────────────────┐
        │   LocationController          │
        │ - Validate request             │
        │ - Apply Spring Validation     │
        └──────────┬───────────────────┘
                   │
                   ↓
        ┌──────────────────────────────┐
        │   LocationService             │
        │ - Business orchestration      │
        │ - Handle location updates    │
        └──────────┬───────────────────┘
                   │
                   ↓
        ┌──────────────────────────────┐
        │  LocationBatchProcessor (*)   │  ← CRITICAL COMPONENT
        │ ┌──────────────────────────┐ │
        │ │ BlockingQueue (10k)       │ │
        │ │ - Enqueue incoming updates│ │
        │ │ - Non-blocking (< 10ms)   │ │
        │ └──────────┬────────────────┘ │
        │            │                  │
        │ ┌──────────▼────────────────┐ │
        │ │ Batch Accumulator         │ │
        │ │ - Size: 500 or 100ms      │ │
        │ │ - Triggers: On either     │ │
        │ │   condition met           │ │
        │ └──────────┬────────────────┘ │
        │            │                  │
        │ ┌──────────▼────────────────┐ │
        │ │ Flush Coordination        │ │
        │ │ - Redis: Immediate        │ │
        │ │ - DB: Async (5 min)       │ │
        │ │ - Events: Per-update      │ │
        │ └──────────┬────────────────┘ │
        └────────────┼──────────────────┘
                     │
          ┌──────────┼──────────────────────┐
          │          │                      │
          ↓          ↓                      ↓
    ┌──────────┐  ┌──────────────┐  ┌──────────────────┐
    │  Redis   │  │ PostgreSQL   │  │  Kafka Topic:    │
    │   Geo    │  │  (Audit Log) │  │ location.changed │
    │          │  │              │  │                  │
    │ GEOADD   │  │ BATCH INSERT │  │ LocationChanged  │
    │ (pipe)   │  │  (async)     │  │ Event            │
    └────┬─────┘  └──────────────┘  └─────────┬────────┘
         │                                      │
         │                                      │
    ┌────▼─────────────────────────────────────▼──────────┐
    │          Spatial Queries & Analytics                  │
    │                                                       │
    │  GET /nearby (Matching Engine)                      │
    │  ↓ GEORADIUS (< 200ms p99)                          │
    │                                                       │
    │  Kafka Consumers:                                   │
    │  ├─ Matching Engine (driver discovery)              │
    │  ├─ Notification Service (real-time updates)        │
    │  └─ Analytics (historical analysis)                 │
    └───────────────────────────────────────────────────────┘
```

## Component Details

### 1. LocationController
**Responsibility**: HTTP request/response handling only

```java
@RestController
public class LocationController {
    @PutMapping("/drivers/location")
    public ResponseEntity<Void> updateDriverLocation(@Valid LocationUpdateRequest req) {
        // No business logic - delegate immediately
        locationService.updateLocation(req);
        return ResponseEntity.accepted().build();  // 202
    }
}
```

**Design Decisions**:
- Returns **202 Accepted** (not 200) to signal async processing
- Validation via `@Valid` and Spring validation framework
- Thin controller = single responsibility principle

### 2. LocationService
**Responsibility**: Business logic orchestration

```java
@Service
public class LocationService {
    public void updateLocation(LocationUpdateRequest request) {
        // Delegate to batch processor for async handling
        batchProcessor.enqueueUpdate(request);
    }

    public NearbyDriverResponse findNearbyDrivers(double lat, double lng, int radiusKm) {
        // Synchronous - must complete < 200ms for Matching Engine
        return redisGeoService.findNearbyDrivers(lat, lng, radiusKm, 100);
    }
}
```

**Design Decisions**:
- Separates business logic from HTTP handling
- Coordinates between batch processor, Redis, and database
- All throws delegate to GlobalExceptionHandler

### 3. LocationBatchProcessor (**CRITICAL**)
**Responsibility**: Batching, flushing, coordination

```
Input Stream (100k+/sec)
    ↓
[BlockingQueue] 10k capacity
    ↓
[Batch Accumulator] Synchronized list
    ├─ Trigger 1: Size ≥ 500 → Flush
    └─ Trigger 2: Time ≥ 100ms → Flush
    ↓
[Flush Coordinator]
    ├─ Redis: Pipelined GEOADD (immediate)
    ├─ Events: Publish to Kafka (async)
    ├─ DB: Queue async write (5 min interval)
    └─ Status: Update driver status (Redis)
```

**Key Implementation Details**:

```java
private final BlockingQueue<LocationUpdateRequest> inputQueue;
private final List<LocationUpdate> batch;  // Synchronized
private volatile long lastFlushTime;

public void enqueueUpdate(LocationUpdateRequest request) {
    if (!inputQueue.offer(request)) {  // Non-blocking
        failedCount.increment();  // Circuit breaker
        // Drop update gracefully
    }
}

private void flushBatch() {
    // Pipelined Redis write
    redisGeoService.addLocationsBatch(batch);

    // Async DB write
    dbWriter.submit(() -> locationRepository.saveAll(batch));

    // Event publishing (per-update)
    batch.forEach(u -> eventPublisher.publishLocationChanged(u));
}
```

**Why This Design**:
1. **Throughput**: Batching reduces Redis commands 500x (500 → 1 pipeline)
2. **Latency**: Input enqueue is < 10ms (queue.offer is O(1))
3. **Safety**: Queue overflow triggers circuit breaker (drop gracefully)
4. **Async**: DB writes don't block incoming requests

### 4. RedisGeoService
**Responsibility**: High-performance Redis Geo operations

```java
public void addLocationsBatch(List<LocationUpdate> updates) {
    // Pipelined: send all commands in one round-trip
    redisTemplate.executePipelined((connection) -> {
        for (LocationUpdate u : updates) {
            connection.geoAdd(key, point.getX(), point.getY(), driverId);
        }
        return null;
    });
}

public NearbyDriverResponse findNearbyDrivers(double lat, double lng, int radiusKm) {
    // Single Redis query: GEORADIUS
    Set<GeoLocation<String>> results = redisTemplate.opsForGeo()
        .radius(key, new Circle(new Point(lng, lat), new Distance(radiusKm, KILOMETERS)),
            args().withCoord().withDist().count(100).sortDescending());

    // Convert to response DTOs
    return buildResponse(results);
}
```

**Redis Commands Used**:
- `GEOADD key lng lat member` - Add location (idempotent)
- `GEORADIUS key lng lat radius unit [WITHCOORD] [WITHDIST]` - Spatial query
- `GEOPOS key member` - Get current location
- `GEODEL key member` - Remove driver (offline)

**Performance**:
- `GEOADD` (pipelined 500): < 5ms
- `GEORADIUS` (5km radius, 100 results): < 50ms (p99)

### 5. DriverStatusService
**Responsibility**: Online/offline status tracking

```
Fast Path (Hot):
  Redis Cache → TTL 5 min → "online"

Slow Path (Cold):
  PostgreSQL → driver_status table → Latest update
```

**Design**:
```java
public boolean isOnline(String driverId) {
    // Check Redis cache first (< 1ms)
    if (redisGeoService.isDriverOnlineCached(driverId)) return true;

    // Fall back to database
    return driverStatusRepository.findByDriverId(driverId)
        .map(DriverStatus::getIsOnline)
        .orElse(false);
}
```

## Data Flow Scenarios

### Scenario 1: Location Update (100k+/sec)
```
1. Driver sends: PUT /drivers/location
   ↓ (Validated by Spring @Valid)
2. LocationController.updateDriverLocation()
   ↓
3. LocationService.updateLocation(request)
   ↓
4. LocationBatchProcessor.enqueueUpdate()
   ├─ Offer to BlockingQueue (non-blocking)
   ├─ If full → drop, increment failedCount
   └─ If success → continue to processor thread
   ↓
5. Processor thread (continuous loop):
   ├─ Poll from queue (10ms timeout)
   ├─ Convert to entity
   ├─ Add to batch (synchronized list)
   ├─ Publish event immediately
   ├─ Update driver status
   └─ Check flush conditions (size or time)
   ↓
6. On flush (size ≥ 500 OR time ≥ 100ms):
   ├─ Pipelined Redis: GEOADD × 500 (< 5ms)
   ├─ Async DB: Queue 500 inserts (separate thread)
   └─ Update lastFlushTime
   ↓
7. Result: User gets 202 Accepted in < 10ms
   Data in Redis Geo in < 15ms
   Data in PostgreSQL in 5-300 seconds (batched)
```

### Scenario 2: Find Nearby Drivers (Matching Engine)
```
1. Matching Engine sends: GET /nearby?lat=40.7128&lng=-74.0060&radiusKm=5
   ↓
2. LocationController.findNearbyDrivers()
   ├─ Validate coordinates
   └─ Call service
   ↓
3. LocationService.findNearbyDrivers()
   ↓
4. RedisGeoService.findNearbyDrivers()
   ├─ Create Point(lng, lat)
   ├─ Create Circle(center, 5km)
   ├─ Execute GEORADIUS command
   ├─ Convert results to DTOs
   └─ Add online status from cache
   ↓
5. Return 200 OK with 42 nearby drivers
   Latency: < 50ms (p99)
```

### Scenario 3: 5-Minute Database Flush
```
Background task (DbWriter thread):
Every 300 seconds (5 minutes):
  1. Collect all accumulated updates since last flush
  2. Execute batch INSERT (Flyway-managed schema)
     INSERT INTO driver_locations (...) VALUES (...), (...), ...
  3. Record latency metric
  4. Update lastDbFlushTime

Example: 30M updates in 5 min = 100k/sec
         After 5 min: 30M rows inserted in ~500ms
         Rate: 60k inserts/sec
```

## Scalability Considerations

### Single Instance Limits
- **Throughput**: ~100k updates/sec (CPU-bound)
- **Memory**: ~500MB base + 10MB per 100k drivers in Redis
- **Network**: ~2 Mbps (compressed Kafka + Redis)

### Multi-Instance Scaling

**Batching Issue**: Each instance batches independently
- Fix: Distributed batching via Redis Streams (v2)

**Redis Geo Index**: Shared across all instances
- All instances read/write same Redis cluster
- No coordination needed

**Kafka Event Distribution**: Each consumer group gets all events
- Matching Engine: single partition consumer (ordered by driverId)
- Notification: broadcast via Redis Pub/Sub

**Load Balancing**:
```
[Load Balancer]
    ├─ Instance 1 (33k updates/sec)
    ├─ Instance 2 (33k updates/sec)
    └─ Instance 3 (33k updates/sec)
    ↓ All → Shared Redis Geo + PostgreSQL
```

## Failure Modes & Resilience

### 1. Redis Connection Lost
```
Impact: Can't index locations (hot path blocked)
Recovery:
  - Circuit breaker: Queue fills up → drops updates
  - Retry: Automatic reconnection (Lettuce)
  - Fallback: Can still persist to PostgreSQL (slow path)
```

### 2. PostgreSQL Connection Lost
```
Impact: Can't persist audit trail (cold path blocked)
Recovery:
  - Async batch writer catches exception
  - Locations stay in Redis Geo (hot path still works)
  - Manual recovery: Replay from Kafka topic location.changed
  - Event log: Ensures no data loss
```

### 3. Kafka Producer Timeout
```
Impact: Events don't publish
Recovery:
  - Non-blocking fire-and-forget (doesn't fail request)
  - Retry: Kafka producer automatically retries
  - Location data still indexed in Redis (primary path)
```

### 4. Input Queue Overflow
```
Impact: New location updates rejected
Trigger: Queue at 10k capacity + sustained > 100k/sec
Recovery:
  - gradual scaling (add more instances)
  - Or reduce batch window (100ms → 50ms) to flush faster
  - Monitor: Metrics show failedCount increasing
```

## Testing Strategy

### Unit Tests (80%+ coverage)
- Batch processor: enqueue, flush conditions, stats
- Redis service: add, find, remove operations
- Service: business logic, error handling
- Controller: validation, response codes

### Integration Tests
- Real PostgreSQL (TestContainers)
- Real Redis (TestContainers)
- Real Kafka (TestContainers)
- End-to-end flow validation

### Load Tests
- Sequential: 10k updates, measure throughput
- Concurrent: 100 drivers, 100 updates each
- Sustained: 50 drivers for 5 seconds, measure latency
- Burst: 1000 updates in 100ms

### Performance Benchmarks
- Redis batch latency: < 5ms for 500 updates
- PostgreSQL batch insert: < 500ms for 30M rows
- Kafka publish: < 10ms per event
- Nearby driver query: < 50ms (p99)

## Monitoring & Observability

### Prometheus Metrics
```
location_updates_total          # Total updates (counter)
location_batch_flushes_total    # Batch flushes (counter)
location_nearby_queries_total   # Geo queries (counter)
location_nearby_query_latency   # p50, p95, p99 (histogram)
location_redis_batch_latency    # Redis write latency (histogram)
location_db_batch_latency       # DB write latency (histogram)
```

### Logs
```
DEBUG: Location update received for driver X
INFO:  Batch flushed: 500 updates in 5ms to Redis
WARN:  Input queue full, dropped location update for driver Y
ERROR: Failed to publish location event for driver Z
```

### Alerts
```
- Input queue size > 5000 (capacity = 10k) → Scale up
- Redis batch latency p99 > 10ms → Redis bottleneck
- DB flush latency > 5 seconds → PostgreSQL slow
- Event publish failure rate > 0.1% → Kafka issues
```

## Security Considerations

1. **Authentication**: Clients authenticated by API Gateway
2. **Authorization**: Drivers can only update their own location
3. **Rate Limiting**: Gateway enforces limits (100 req/sec per driver)
4. **Data Encryption**: TLS for all communication, encryption at rest for Redis
5. **SQL Injection**: Parameterized queries (JPA)
6. **Input Validation**: Spring validation + custom validators

## Next Steps & Future Optimizations

### Phase 2
- [ ] Distributed batching (Redis Streams)
- [ ] Auto-expire offline drivers (TTL)
- [ ] Geo-sharding by region
- [ ] Duplicate detection (client-side throttling)

### Phase 3
- [ ] Machine learning (ETA prediction from historical data)
- [ ] Heatmaps (hot zones detection)
- [ ] Anomaly detection (unusual movement patterns)
- [ ] Location privacy (GeoHashing, differential privacy)

## References

- Redis Geo: https://redis.io/commands#geo
- Spring Data Redis: https://spring.io/projects/spring-data-redis
- Kafka Design: https://kafka.apache.org/design
- JPA Batch Insert: https://docs.jboss.org/hibernate/orm/5.4/userguide/html_single/Hibernate_User_Guide.html#batch
