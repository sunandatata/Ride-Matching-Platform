# Data Flow Design

This document details the complete data flows for the four critical paths: ride request, driver location updates, matching, and ride completion.

---

## 1. Ride Request Flow

### Scenario
A rider opens the app, enters pickup and dropoff locations, and requests a ride.

### End-to-End Flow Diagram

```
Rider App                API Gateway           Ride Service              Event Stream
   │                          │                      │                        │
   ├─ POST /api/v1/rides ────>│                      │                        │
   │   {                       │                      │                        │
   │    "pickup": {lat, lng},  │                      │                        │
   │    "dropoff": {lat, lng}, │                      │                        │
   │    "passenger_count": 1   │                      │                        │
   │   }                       │                      │                        │
   │                           ├─ Route to Rider ───>│                        │
   │                           │  Service             │                        │
   │                           │                 [Validate Rider]             │
   │                           │                 [Estimate Fare]              │
   │                           │                 [Create Ride Record]         │
   │                           │                 [Set Status: REQUESTED]      │
   │                           │                 [Generate Ride ID]           │
   │                           │                      ├─ Publish Event ──────>│
   │                           │                      │  RideRequested        │
   │                           │<─ 201 Created ──────│   {ride_id, rider_id, │
   │<─ 201 OK ────────────────│  with ride_id        │    pickup, dropoff,    │
   │  {                        │                      │    estimated_fare}     │
   │   "ride_id": "R123",      │                      │                        │
   │   "status": "REQUESTED",  │                      │                        │
   │   "estimated_fare": 12.50 │                      │                        │
   │  }                        │                      │                        │
   │                           │                      │                        │
   │ [WebSocket Connection]    │                      │                        │
   ├─ SUBSCRIBE /rides/R123 ──────────────────────────────────────────────────>│
   │                           │                      │                        │
```

### Detailed Request Processing

**1. API Gateway Validation (Synchronous)**
- Authenticate rider (JWT token validation)
- Rate limit check (prevent abuse)
- Input validation (valid lat/lng, passenger count > 0)
- Route to Ride Service

**2. Ride Service Processing (Synchronous)**
```
Input: CreateRideRequest {
  rider_id: string (from JWT)
  pickup_location: {latitude, longitude, address}
  dropoff_location: {latitude, longitude, address}
  passenger_count: integer
  payment_method_id: string
}

Processing:
1. Verify rider exists and is not already on an active ride
2. Validate pickup/dropoff locations (not too close, within service area)
3. Call ETA Service to estimate trip duration and fare
4. Create ride record in database:
   - ride_id (UUID)
   - rider_id
   - driver_id (null initially)
   - status (REQUESTED)
   - pickup_location, dropoff_location
   - estimated_fare, estimated_duration
   - created_at, updated_at
   - expires_at (30 seconds timeout for matching)
5. Commit transaction
6. Publish RideRequested event

Output: CreateRideResponse {
  ride_id: string
  status: REQUESTED
  estimated_fare: number
  estimated_duration: integer (seconds)
  expires_at: timestamp
}
```

**3. Event Emission (Asynchronous)**
```
Event: RideRequested {
  event_id: UUID
  ride_id: string
  rider_id: string
  pickup_location: {lat, lng, address}
  dropoff_location: {lat, lng, address}
  estimated_fare: number
  estimated_duration: integer
  passenger_count: integer
  service_type: ECONOMY | PREMIUM | SHARED
  timestamp: ISO8601
}

Published to: Kafka topic "ride-events" with partition key = ride_id
Subscribers:
  - Matching Engine (listens for RideRequested)
  - Notification Service (caches for offline delivery)
  - Analytics Pipeline (metrics, demand signals)
```

### Failure Scenarios & Handling

| Scenario | Action | State |
|----------|--------|-------|
| Rider validation fails | Return 401 Unauthorized | No ride created |
| Duplicate ride request (within 1s) | Deduplicate via idempotency key | Return existing ride_id |
| Invalid locations | Return 400 Bad Request | No ride created |
| ETA Service timeout | Use fallback estimate + fallback duration | Ride created (users see "Calculating..." UI) |
| Database write fails | Retry with exponential backoff, then circuit breaker | Alert operations; user sees error |
| Event publish fails | Dead-letter queue; alert operations | Ride stuck in REQUESTED; operator manual intervention |

---

## 2. Driver Location Update Flow

### Scenario
A driver (online and available) moves, and their location must be updated in real-time for matching queries.

### High-Throughput Update Flow

```
Driver App              Location Service        Redis Geo             Event Stream
   │                          │                   Store                    │
   │                          │                     │                      │
   ├─ PUT /locations ───────>│                     │                      │
   │  {driver_id, lat,        │                     │                      │
   │   lng, heading,          │                     │                      │
   │   speed, timestamp}      │                     │                      │
   │                     [Validate Request]        │                      │
   │                     [Check Rate Limit]        │                      │
   │                     [Batch & Queue]           │                      │
   │                          │                     │                      │
   │<─ 202 Accepted ────────┬─┤                     │                      │
   │  (Location queued)     │                      │                      │
   │                        │                      │                      │
   │                    [Async Worker]             │                      │
   │                    (every 100ms)              │                      │
   │                        │                      │                      │
   │                        ├─ GEOADD ────────────>│                      │
   │                        │  driver_id,          │                      │
   │                        │  lat, lng            │                      │
   │                        │<─ OK (batched) ──────┤                      │
   │                        │                      │                      │
   │                        ├─ Publish Event ─────────────────────────────>│
   │                        │ DriverLocationChanged                       │
   │                        │ {driver_id, lat, lng,                       │
   │                        │  timestamp}                                 │
   │                        │                      │                      │
```

### Design Rationale

**Why Batch & Queue?**
- Driver sends updates frequently (every 1-2 seconds)
- 100k+ drivers × 1 update/sec = 100k+ requests/sec
- Batching reduces Redis writes and network overhead
- 100ms batch window introduces negligible latency (acceptable for location accuracy)

**Location Service Processing**
```
Input: LocationUpdate {
  driver_id: string
  latitude: float
  longitude: float
  heading: integer (0-360)
  speed: integer (mph)
  timestamp: ISO8601
  accuracy: integer (meters) [optional]
}

Processing:
1. Validate driver exists and is online
2. Queue update in in-memory buffer (keyed by driver_id)
3. Return 202 Accepted immediately

Batch Processing (every 100ms):
1. Retrieve all buffered updates
2. Execute Redis GEOADD commands (bulk operation)
3. Publish LocationChanged events for each driver
4. Clear buffer
5. Metrics: record batch size, latency histogram

Why Redis GEOADD?
  - O(log N) insertion in spatial index
  - O(N log N) for radius query (GEORADIUS)
  - 100ms batching = max 100 updates per driver in worst case
  - Memory efficient: (lat, lng) = ~13 bytes per driver
```

### Event Emission

```
Event: DriverLocationChanged {
  event_id: UUID
  driver_id: string
  latitude: float
  longitude: float
  heading: integer
  speed: integer
  timestamp: ISO8601
  accuracy: integer
}

Published to: Kafka topic "location-events" with partition key = driver_id
Subscribers:
  - Matching Engine (queries for nearby drivers during ride requests)
  - Notification Service (sends driver updates to rider's WebSocket)
  - Location Analytics (heatmaps, congestion detection)
```

### Failure & Recovery

| Scenario | Action | Recovery |
|----------|--------|----------|
| Location update rejected | Return 4xx error | Driver logs error; app retries after 5s backoff |
| Batch worker crashes | In-memory buffer lost | Drivers continue sending; updates resume when recovered |
| Redis unavailable | Circuit breaker opens | Matching Engine falls back to cached location snapshots; stale by ≤ 1 min |
| Driver goes offline | Last-known location stored | Location expires after 5 minutes of inactivity |

---

## 3. Matching Flow

### Scenario
After a ride is requested, the Matching Engine finds the best available driver and assigns the ride.

### Complete Matching Sequence

```
Event Stream           Matching Engine              Location Service          Driver Service
   │                       │                              │                        │
   ├─ RideRequested Event──>│                              │                        │
   │                    [Enqueue Request]                  │                        │
   │                        │                              │                        │
   │                    [Query Nearby Drivers]             │                        │
   │                        ├─ GEORADIUS ────────────────>│                        │
   │                        │  (pickup_lat, pickup_lng,   │                        │
   │                        │   radius=10km)               │                        │
   │                        │<─ [driver_id_1, ...]────────┤                        │
   │                        │                              │                        │
   │                    [Filter Drivers]                   │                        │
   │                    - Not on active ride              │                        │
   │                    - Vehicle not full                │                        │
   │                    - Acceptance rate > 60%           │                        │
   │                        │                              │                        │
   │                    [Fetch Driver Details]             │                        │
   │                        ├─ GET /drivers/{id} ────────────────────────────────>│
   │                        │  (rating, acceptance_rate,  │                        │
   │                        │   vehicle capacity)          │                        │
   │                        │<─ {details} ─────────────────────────────────────────┤
   │                        │                              │                        │
   │                    [Calculate ETA & Rank]            │                        │
   │                    For each candidate:                │                        │
   │                    - ETA(driver->pickup)              │                        │
   │                    - ETA(pickup->dropoff)             │                        │
   │                    - Driver rating                    │                        │
   │                    - Acceptance rate                  │                        │
   │                    - Distance score                   │                        │
   │                        │                              │                        │
   │                    [Select Top Driver]                │                        │
   │                    [Assign Ride]                      │                        │
   │                    [Update Ride Status: MATCHED]      │                        │
   │                    [Publish RideMatched Event]        │                        │
   │<─ RideMatched Event ───┤                              │                        │
   │  {ride_id, driver_id,  │                              │                        │
   │   driver_location,     │                              │                        │
   │   eta_to_pickup}       │                              │                        │
```

### Matching Algorithm Details

**Phase 1: Spatial Discovery**
```
Input: ride_id, pickup_location {lat, lng}

Execution:
1. Query Redis GEORADIUS:
   - Center: pickup_location
   - Radius: 10 km (adjustable; longer waits in rural areas)
   - Returns: [driver_id_1, driver_id_2, ..., driver_id_N]
   - Complexity: O(N log N) where N = drivers in radius (~50-500 in typical urban area)

Output: List of nearby driver IDs (unsorted)
Latency: <10ms
```

**Phase 2: Filter & Enrich**
```
Input: [driver_id_1, ..., driver_id_N]

Execution:
1. Fetch driver details (parallel via thread pool or batch HTTP):
   GET /drivers/{id} (fields: rating, acceptance_rate, vehicle_capacity, current_passengers)

2. Filter criteria (all must pass):
   - status = AVAILABLE (not on ride, vehicle not full)
   - acceptance_rate >= 60% (exclude problematic drivers)
   - passenger_count + ride.passenger_count <= vehicle_capacity

3. Result: M drivers pass filter (typically M = 5-20 drivers)

Latency: <50ms (parallel fetch)
```

**Phase 3: ETA Calculation**
```
Input: M filtered drivers, ride pickup & dropoff

For each driver:
1. Calculate ETA(driver_location → pickup_location):
   - Use routing service (Google Maps API, OSRM)
   - Cache routes in 1km x 1km grid cells (Redis)
   - Hit rate: ~80% in dense urban areas

2. Estimate ETA(pickup → dropoff):
   - Cache from ETA Service

Result per driver:
  {
    driver_id,
    eta_to_pickup (seconds),
    eta_trip (seconds),
    distance_to_pickup (meters)
  }

Latency: <30ms (cached) or <100ms (API call)
```

**Phase 4: Ranking & Selection**
```
Ranking Formula (normalized scoring):

SCORE(driver) =
  w1 * DISTANCE_SCORE(driver) +
  w2 * RATING_SCORE(driver) +
  w3 * ACCEPTANCE_SCORE(driver) +
  w4 * ETA_SCORE(driver)

Where:
  DISTANCE_SCORE = 1 - (distance_to_pickup / max_distance)  [0 = furthest, 1 = closest]
  RATING_SCORE = driver_rating / 5.0                         [0-1, higher is better]
  ACCEPTANCE_SCORE = acceptance_rate / 100                   [0-1, higher is better]
  ETA_SCORE = 1 - (eta_to_pickup / 600)                     [0 = >10min away, 1 = immediate]

Weights (configurable):
  w1 = 0.4 (distance is most important for rider wait time)
  w2 = 0.2 (rating indicates quality)
  w3 = 0.2 (acceptance rate indicates reliability)
  w4 = 0.2 (ETA as tiebreaker)
  (w1 + w2 + w3 + w4 = 1.0)

Sort drivers by SCORE descending.
Select top 1 driver for assignment.

Alternative (multi-offer):
- Select top 3 drivers
- Offer ride to all simultaneously
- Assign to first responder (race condition resolution)
```

**Phase 5: Assignment & Persistence**
```
Input: selected_driver_id

Execution:
1. Transactional update:
   UPDATE rides
   SET driver_id = selected_driver_id,
       status = 'MATCHED',
       assigned_at = NOW()
   WHERE ride_id = ? AND status = 'REQUESTED'

2. If update succeeds:
   - Publish RideMatched event
   - Send notification to driver and rider

3. If update fails (race condition):
   - Another request matched this ride simultaneously
   - Return failure; client retries ride request

Latency: <5ms (database write)
```

### End-to-End Matching Latency Budget

```
Phase 1: Spatial Discovery          10ms
Phase 2: Filter & Enrich            50ms
Phase 3: ETA Calculation            30ms  (assuming 80% cache hit)
Phase 4: Ranking                     5ms
Phase 5: Assignment                  5ms
─────────────────────────────────────────
Total                              100ms (target: <200ms)

Contingencies:
- ETA Service cache miss: +70ms (add 15% probability) → avg +10.5ms
- Slow driver service: +20ms (add 5% probability) → avg +1ms
─────────────────────────────────────────
95th percentile latency:           120ms ✓
```

### Failure Scenarios

| Scenario | Action |
|----------|--------|
| No drivers within radius | Expand radius and retry; show "searching" UI |
| All nearby drivers rejected | Fallback to next tier (lower rating threshold, expanded radius) |
| ETA Service timeout | Use fallback distance-based estimate |
| Race condition (ride matched twice) | One gets 409 Conflict; retry with new ride_id |
| Driver goes offline mid-match | Assignment fails; triggers cascade retry |
| Rider cancels during matching | Cancel ride request; abort matching flow |

---

## 4. Ride Completion Flow

### Scenario
Driver arrives, picks up rider, drives to destination, and completes the ride.

### State Transitions & Events

```
Ride Service              Driver App / Location Service       Event Stream
   │                             │                                 │
   │ Status: MATCHED              │                                 │
   │                             │                                 │
   │                        [Driver navigates to pickup]           │
   │                        [Location updates sent to Rider]       │
   │                             │                                 │
   │                        [Driver arrives at pickup]             │
   │                        PUT /rides/{id}/started               │
   │<─────────────────────────────┤                                 │
   │ Status: STARTED              │                                 │
   │ [Publish RideStarted Event] ─────────────────────────────────>│
   │                             │                                 │
   │                        [Driver navigates to dropoff]          │
   │                        [Location updates continue]            │
   │                             │                                 │
   │                        [Driver arrives at dropoff]            │
   │                        PUT /rides/{id}/completed             │
   │<─────────────────────────────┤                                 │
   │ Status: COMPLETED            │                                 │
   │ [Calculate & Store Fare]     │                                 │
   │ [Publish RideCompleted Event] ───────────────────────────────>│
   │                             │                                 │
```

### Detailed State Machine

```
REQUESTED
  ↓
MATCHED (driver assigned, waiting for driver to navigate)
  ├─ → CANCELLED (rider/driver cancel)
  ├─ → EXPIRED (match expires after 30s with no acceptance)
  │
STARTED (driver picked up rider, in transit)
  ├─ → CANCELLED (rider/driver cancel in-trip)
  │
COMPLETED (arrived at destination)
  ├─ → PAYMENT_PENDING (awaiting payment processing)
  │
PAYMENT_COMPLETED
  ├─ → CLOSED (ride archived)
```

### Ride Completion Processing

```
Event: RideStarted
Input from driver: PUT /rides/{ride_id}/started
Processing:
  - Verify driver is at pickup location (within 100m)
  - Update ride status: MATCHED → STARTED
  - Record start time, start location
  - Publish RideStarted event
  - Notify rider and driver

─────────────────────────────────────────

Event: RideCompleted
Input from driver: PUT /rides/{ride_id}/completed
{
  end_location: {lat, lng},
  end_time: ISO8601,
  distance_traveled: meters,
  duration: seconds
}

Processing:
  1. Verify driver is at dropoff location (within 500m)
  2. Update ride status: STARTED → COMPLETED
  3. Record end time, end location
  4. Calculate final fare:
     - Base fare: $2.00
     - Distance: $1.25 per km
     - Time: $0.45 per minute (wait time)
     - Surge multiplier: 1.0-3.0x (demand-based)
     Final = (base + distance + time) * surge
  5. Apply promotions, referral credits
  6. Store final_fare in database
  7. Publish RideCompleted event
  8. Queue payment processing
  9. Notify rider and driver
  10. Archive ride to cold storage (after 24h)

─────────────────────────────────────────

Event: PaymentProcessed
Processing:
  - Process payment from rider's payment method
  - Allocate payout to driver (80% of fare typically)
  - Record transaction in accounting system
  - Publish PaymentProcessed event
  - Update ride status: PAYMENT_COMPLETED → CLOSED
```

### Failure Scenarios in Completion

| Scenario | Action |
|----------|--------|
| Driver doesn't start ride within 5 min | Ride auto-cancelled; rider refunded |
| Driver starts from wrong location | Warn driver; allow retry |
| Distance/duration mismatch | Manual review by ops; adjust fare if needed |
| Payment failure | Charge retry after 24h; send payment reminder to rider |
| Ride never marked completed | Manual closure by ops after 24h; default to estimated fare |

---

## 5. Event Schema Registry

### Core Events

```
Event: RideRequested
{
  "event_id": "uuid",
  "event_type": "ride.requested",
  "version": "1.0",
  "timestamp": "2026-06-02T14:30:00Z",
  "ride_id": "R123456",
  "rider_id": "U789",
  "pickup_location": {
    "latitude": 40.7128,
    "longitude": -74.0060,
    "address": "123 Main St, NYC"
  },
  "dropoff_location": {
    "latitude": 40.7580,
    "longitude": -73.9855,
    "address": "Empire State Building, NYC"
  },
  "estimated_fare": 12.50,
  "estimated_duration_seconds": 900,
  "passenger_count": 1,
  "service_type": "ECONOMY"
}

Event: RideMatched
{
  "event_id": "uuid",
  "event_type": "ride.matched",
  "version": "1.0",
  "timestamp": "2026-06-02T14:30:05Z",
  "ride_id": "R123456",
  "driver_id": "D456",
  "driver_location": {latitude, longitude},
  "eta_to_pickup_seconds": 240,
  "acceptance_score": 0.85
}

Event: DriverLocationChanged
{
  "event_id": "uuid",
  "event_type": "driver.location_changed",
  "version": "1.0",
  "timestamp": "2026-06-02T14:30:06Z",
  "driver_id": "D456",
  "latitude": 40.7200,
  "longitude": -74.0050,
  "heading": 90,
  "speed_mph": 25,
  "accuracy_meters": 8
}

Event: RideStarted
{
  "event_id": "uuid",
  "event_type": "ride.started",
  "version": "1.0",
  "timestamp": "2026-06-02T14:35:00Z",
  "ride_id": "R123456",
  "driver_id": "D456",
  "start_time": "2026-06-02T14:35:00Z",
  "start_location": {latitude, longitude}
}

Event: RideCompleted
{
  "event_id": "uuid",
  "event_type": "ride.completed",
  "version": "1.0",
  "timestamp": "2026-06-02T14:50:00Z",
  "ride_id": "R123456",
  "driver_id": "D456",
  "rider_id": "U789",
  "end_time": "2026-06-02T14:50:00Z",
  "end_location": {latitude, longitude},
  "distance_meters": 3200,
  "duration_seconds": 900,
  "final_fare": 15.75,
  "surge_multiplier": 1.0,
  "driver_rating_by_rider": 4.5,
  "rider_rating_by_driver": 5.0
}
```

---

## Message Ordering & Consistency Guarantees

### Kafka Partitioning Strategy

```
Topic: ride-events
  Partition: ride_id % 8
  → All events for a single ride go to same partition
  → Guarantees in-order delivery: REQUESTED → MATCHED → STARTED → COMPLETED
  → Benefits: No race conditions in state machine

Topic: location-events
  Partition: driver_id % 32
  → All location updates for a driver go to same partition
  → Enables per-driver state consistency in consumers
  → Higher parallelism (32 partitions) for location throughput

Topic: payment-events
  Partition: rider_id % 16
  → All payments for a rider go to same partition
  → Prevents race conditions in billing
```

### Idempotency & Deduplication

```
Consumer: Ride Service
- Idempotency key: event_id (unique UUID per event)
- On processing: Store (event_id, result) in PostgreSQL
- On duplicate event: Return cached result
- Query: SELECT result FROM processed_events WHERE event_id = ?
```

---

## Summary: Data Flow Latency Targets

| Flow | Phase | Latency Target | Remarks |
|------|-------|-----------------|---------|
| Ride Request | Validation → DB Write | 50-100ms | User sees confirmation |
| Location Update | App → Queue → Redis | 200-500ms | Batching + async |
| Matching | Enqueue → Assignment | <200ms | Query+Filter+Rank+Assign |
| Ride Completion | Driver action → Event | 100-500ms | Async fare calculation |
| WebSocket Update | Event → Client | <100ms | Real-time notification |

---

**Next**: Database Design, API Contracts, Matching Engine Details
