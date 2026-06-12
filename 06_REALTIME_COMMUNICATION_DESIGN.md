# Real-Time Communication Design

This document specifies WebSocket architecture, event flows, and state synchronization for real-time updates during active rides.

---

## 1. Real-Time Communication Overview

### Requirements

1. **Live Driver Location**: Rider sees driver moving in real-time
2. **Live ETA Updates**: Both rider and driver see updated arrival times
3. **Status Changes**: Instant notifications (matched, started, completed)
4. **Bidirectional Communication**: Driver can signal arrival, rider can communicate
5. **Low Latency**: < 100ms for most messages
6. **High Reliability**: No missed updates (at-least-once delivery)

### Architecture Options

#### Option A: WebSocket (Selected)

```
Pros:
  - Persistent connection (no polling overhead)
  - Low latency (<100ms)
  - Bidirectional (client → server, server → client)
  - Efficient (header compression, binary frames)

Cons:
  - Stateful service (harder to scale)
  - Connection management complexity
  - Mobile reconnection handling
```

#### Option B: Server-Sent Events (SSE)

```
Pros:
  - Simpler server-side (stateless)
  - Native browser support

Cons:
  - Unidirectional (server → client only)
  - Polling required for client → server
  - Higher latency than WebSocket
```

#### Option C: Traditional Polling

```
Pros:
  - Stateless server
  - Simple implementation

Cons:
  - High latency (1-5 second poll interval)
  - Wasted bandwidth
  - Poor UX
```

**Recommendation: WebSocket** (best balance of latency, efficiency, and complexity)

---

## 2. WebSocket Architecture

### Connection Lifecycle

```
Client (Driver/Rider)      WebSocket Gateway      Notification Service
         │                        │                        │
         ├─ TCP Connect ────────>│                        │
         │                        │                        │
         ├─ Upgrade to WS ──────>│                        │
         │  (with auth token)     │                        │
         │                        ├─ Validate JWT ────────│
         │                        │<─ Valid ───────────────┤
         │<─ 101 Switching ───────┤                        │
         │  Protocols             │ ├─ Store connection   │
         │                        │ │  {user_id, conn_id} │
         │                        │                        │
         ├─ {"type": "ping"} ────>│                        │
         │<─ {"type": "pong"} ────┤                        │
         │                        │                        │
         │  [Messages flow...]    │                        │
         │                        │                        │
         ├─ Close connection ────>│                        │
         │                        ├─ Cleanup connection  │
```

### Message Structure

**Standard Message Format**:
```json
{
  "message_id": "uuid",
  "type": "driver.location_updated|ride.status_changed|eta_updated|...",
  "ride_id": "R123456",
  "timestamp": "2026-06-02T14:30:00Z",
  "data": {
    // Type-specific payload
  }
}
```

### Connection State Management

```
Notification Service State:

Map[UserId] = {
  connection_id: "conn-uuid",
  user_type: "rider|driver",
  active_rides: [R123, R456],
  websocket: WebSocketConnection,
  authenticated: true,
  connected_at: timestamp,
  last_heartbeat: timestamp
}

Example:
  Map["U789"] = {
    connection_id: "conn-123",
    user_type: "rider",
    active_rides: ["R123456"],
    websocket: <active connection>,
    authenticated: true,
    connected_at: "2026-06-02T14:30:00Z",
    last_heartbeat: "2026-06-02T14:30:15Z"
  }
```

### Scalability: Sticky Sessions vs. Redis Pub/Sub

#### Option 1: Sticky Sessions (Simpler)

```
Setup:
  - Load Balancer routes all requests for a user to same Notification Service instance
  - Sticky key: user_id (from JWT token)
  - TTL: Connection lifetime

Pros:
  - No shared state needed
  - Single hop for messages
  - Simple implementation

Cons:
  - Uneven load distribution (some users more active)
  - Failover loss (if service dies, connection lost)
  - Doesn't scale beyond ~100k connections per instance
```

#### Option 2: Redis Pub/Sub (Recommended)

```
Setup:
  - Each Notification Service instance subscribes to Redis channels
  - Channels per user: "user:{user_id}:events"
  - Channels per ride: "ride:{ride_id}:events"

Message Flow:
  1. Event published to Kafka (from Ride Service, Location Service)
  2. Event Processor subscribes to Kafka, transforms to Redis message
  3. Redis PUBLISH to "ride:{ride_id}:events"
  4. All Notification Services subscribed receive message
  5. Service identifies local connections for ride
  6. Sends WebSocket message to connected users

Pros:
  - Decoupled from client connections
  - Easy horizontal scaling
  - Failover resilience
  - Multi-instance fan-out

Cons:
  - Extra Redis dependency
  - Message ordering (single Redis instance bottleneck if not clustered)
  - Slightly higher latency (Kafka → Redis → WebSocket)

Recommended: Use Redis Cluster for high throughput
```

### Recommended Architecture: Redis Pub/Sub + Sticky Sessions Hybrid

```
Setup:
  - Primary: Sticky sessions for connected users
  - Fallback: Redis Pub/Sub for multi-instance coordination

  - Most messages: Direct WebSocket (low latency)
  - Cross-instance messages: Redis Pub/Sub (e.g., rider sends message to driver on different instance)

Implementation:
  1. User connects to Notification Service instance N1
  2. N1 stores connection in local map
  3. Location events published to Kafka
  4. N1 subscribes to "ride:{ride_id}:events"
  5. Location event arrives:
     a. If user connected to N1: Direct WebSocket send
     b. If user on different instance: Redis Pub/Sub (N1 sends via Redis to discover N2)

Latency:
  - Rider & driver on same instance: <10ms (direct)
  - Rider & driver on different instances: <30ms (via Redis)
```

---

## 3. Event Types & Message Flows

### Event 1: Ride Matched

**Published**: Matching Engine → Kafka

**Message**:
```json
{
  "message_id": "uuid",
  "type": "ride.matched",
  "ride_id": "R123456",
  "timestamp": "2026-06-02T14:30:05Z",
  "data": {
    "driver_id": "D456",
    "driver_name": "Jane Doe",
    "driver_rating": 4.8,
    "driver_photo_url": "https://...",
    "vehicle": {
      "make": "Tesla",
      "model": "Model 3",
      "color": "Black",
      "license_plate": "ABC123"
    },
    "eta_to_pickup_seconds": 240
  }
}
```

**Recipients**: Rider, Driver
**Latency Target**: <100ms from matching decision to client notification

---

### Event 2: Driver Location Updated

**Published**: Location Service → Kafka (every 5 seconds while en route)

**Message**:
```json
{
  "message_id": "uuid",
  "type": "driver.location_updated",
  "ride_id": "R123456",
  "timestamp": "2026-06-02T14:30:30Z",
  "data": {
    "driver_id": "D456",
    "latitude": 40.7150,
    "longitude": -74.0050,
    "heading": 90,
    "speed_mph": 25,
    "accuracy_meters": 8
  }
}
```

**Recipients**: Rider (only during active ride)
**Frequency**: Every 5 seconds (configurable)
**Batching**:
  - Driver sends 1 location update/sec
  - Location Service batches into 1 event every 5 secs
  - Notification Service sends to rider every 5 secs

**Optimization**:
```
Problem: 5 sec frequency × 100k active rides = 500k messages/sec ❌

Solution: Selective distribution
  - Only send to riders currently viewing ride (in-app)
  - Send to drivers always (navigation)
  - Use Redis Pub/Sub for selective subscriptions
```

---

### Event 3: ETA Updated

**Published**: ETA Service (periodically, when ETA changes by >30 seconds)

**Message**:
```json
{
  "message_id": "uuid",
  "type": "eta.updated",
  "ride_id": "R123456",
  "timestamp": "2026-06-02T14:35:00Z",
  "data": {
    "eta_to_pickup_seconds": 180,
    "eta_pickup_to_dropoff_seconds": 720,
    "eta_total_seconds": 900,
    "reason": "traffic|route_change|speed_change"
  }
}
```

**Recipients**: Rider, Driver
**Frequency**: Every 30-60 seconds (or when ETA changes significantly)
**Latency Target**: <1 second

---

### Event 4: Ride Status Changed (STARTED)

**Published**: Ride Service (when driver marks pickup)

**Message**:
```json
{
  "message_id": "uuid",
  "type": "ride.status_changed",
  "ride_id": "R123456",
  "timestamp": "2026-06-02T14:35:00Z",
  "data": {
    "status": "STARTED",
    "started_at": "2026-06-02T14:35:00Z",
    "start_location": {
      "latitude": 40.7128,
      "longitude": -74.0060
    }
  }
}
```

**Recipients**: Rider, Driver, Support System
**Latency Target**: <100ms

---

### Event 5: Ride Completed

**Published**: Ride Service (when driver marks dropoff)

**Message**:
```json
{
  "message_id": "uuid",
  "type": "ride.completed",
  "ride_id": "R123456",
  "timestamp": "2026-06-02T14:50:00Z",
  "data": {
    "status": "COMPLETED",
    "completed_at": "2026-06-02T14:50:00Z",
    "end_location": {
      "latitude": 40.7580,
      "longitude": -73.9855
    },
    "final_fare": 15.75,
    "distance_meters": 3200,
    "duration_seconds": 900,
    "driver_earnings": 11.81
  }
}
```

**Recipients**: Rider, Driver
**Post-Completion**: Rating prompt shown to rider

---

### Event 6: Ride Cancelled

**Published**: Ride Service (by rider or driver)

**Message**:
```json
{
  "message_id": "uuid",
  "type": "ride.cancelled",
  "ride_id": "R123456",
  "timestamp": "2026-06-02T14:35:00Z",
  "data": {
    "cancelled_by": "RIDER|DRIVER|SYSTEM",
    "cancellation_reason": "Driver taking too long|...",
    "refund_amount": 5.00,
    "refund_status": "PENDING|COMPLETED"
  }
}
```

**Recipients**: Rider, Driver
**Latency Target**: <500ms

---

## 4. Message Delivery Guarantees

### At-Least-Once Delivery

**Problem**: Network failures, server crashes might lose messages.

**Solution**: Message IDs + Deduplication

```
Server Persistence:
1. Store message_id in Redis (set):
   SET message_delivered:{user_id}:{message_id} true EX 3600
   (expires after 1 hour)

2. On client connection (after reconnect):
   Client sends last_received_message_id
   Server fetches messages since that ID from event log
   Resend any missing messages

3. Client-side:
   Store last_received_message_id in localStorage
   On reconnect, send to server
   Server replies with backlog

Example:
  Client disconnects at T=14:35:00 (after 5 messages received)
  Server continues publishing (rider gets 3 more messages while offline)
  Client reconnects at T=14:35:30
  Client sends: "last_message_id: msg-5"
  Server responds: Messages 6, 7, 8 (backlog)
  Client processes backlog, UI updates to current state
```

**Durability**:
```
Message log stored in:
  - Kafka topic: "notification-events" (7 day retention)
  - Redis: Recent messages (1 hour)

If user offline > 1 hour:
  - Query Kafka for messages
  - If Kafka also expired (> 7 days):
    - Message lost (acceptable for ride events, transient)
    - Ride status available in database

Acceptable trade-off:
  - Recent offline periods (< 1 hour): Full recovery
  - Extended offline (> 1 hour): Partial recovery
```

---

## 5. Heartbeat & Connection Management

### Heartbeat Mechanism

```
Interval: 30 seconds
Direction: Bidirectional

Client sends (every 30s):
  {"type": "ping"}

Server responds (immediately):
  {"type": "pong"}

Timeout detection:
  - If no pong received within 5 seconds: Assume dead
  - Client closes connection
  - Initiates reconnection with exponential backoff

Server-side timeout:
  - If no message received from client for 60 seconds:
    - Mark connection stale
    - Send close frame
    - Clean up resources
```

### Connection State Recovery

```
Scenario: Network blip (5 second dropout)

Timeline:
  T=14:35:00 - Normal communication
  T=14:35:05 - Network interruption
  T=14:35:35 - Client detects missing pong
  T=14:35:36 - Client reconnects (retry logic)
  T=14:35:40 - Reconnection succeeds
  T=14:35:41 - Client sends last_message_id: msg-10
  T=14:35:42 - Server responds with messages 11-15 (backlog from 5s outage)

Result: User sees seamless real-time experience, no missed messages
```

### Mobile Reconnection

**Challenge**: Mobile apps frequently lose connectivity (WiFi→4G handoff, sleep mode)

**Solution**: Exponential backoff reconnection

```
Retry strategy:
  Attempt 1: Immediate
  Attempt 2: 1 second
  Attempt 3: 2 seconds
  Attempt 4: 4 seconds
  Attempt 5: 8 seconds
  Attempt 6: 16 seconds
  Attempt 7: 32 seconds
  Max interval: 60 seconds
  Max attempts: Unlimited (retry indefinitely)

Jitter: Add 0-1 second random jitter to avoid thundering herd

Implementation (pseudo-code):
```javascript
let backoff = 1000; // 1 second
let maxBackoff = 60000; // 60 seconds

async function reconnect() {
  try {
    await connectWebSocket();
    backoff = 1000; // Reset on success
  } catch (error) {
    await sleep(backoff + Math.random() * 1000);
    backoff = Math.min(backoff * 2, maxBackoff);
    reconnect(); // Retry
  }
}
```

---

## 6. Message Routing & Subscriptions

### Subscription Management

```
Users subscribe to specific ride channels:

Rider:
  - Subscribes to: "ride:{ride_id}:events"
  - Receives: All ride events (matched, status, completed, cancelled)
  - Receives: Driver location, ETA updates

Driver:
  - Subscribes to: "ride:{ride_id}:events"
  - Receives: All ride events, rider location (if enabled)
  - May receive: Ping/pong for connection maintenance

Admin/Support:
  - Subscribes to: "ride:{ride_id}:events" (for active tickets)
  - Receives: All events (for dispute resolution)

Implementation (Redis Pub/Sub):
```
SUBSCRIBE ride:R123456:events
SUBSCRIBE user:U789:notifications

When event published:
PUBLISH ride:R123456:events {"type": "driver.location_updated", ...}

All subscribers receive message
Service identifies which connections match {ride_id, user_id}
Sends WebSocket frame to matching connections
```

### Channel Naming Convention

```
ride:{ride_id}:events              - All events for a ride
user:{user_id}:notifications       - User-specific notifications (promo, etc.)
user:{user_id}:rides               - Ride-specific updates for user
system:broadcast                   - System-wide announcements
```

---

## 7. Scalability Considerations

### Connection Limits

```
Notification Service Instance:
  - Max concurrent connections: 100,000 (per instance)
  - Memory per connection: ~10 KB
  - Total memory: 100,000 × 10 KB = 1 GB (acceptable)

Deployment (10k concurrent rides):
  - Assuming 2 users per ride (rider + driver) = 20k connections
  - At 100k connections per instance: 1 instance sufficient
  - For redundancy + headroom: Deploy 3 instances
  - Load balancer: Sticky sessions by user_id
```

### Message Throughput

```
Volume calculation:
  10k concurrent rides × 2 updates/sec (location + other) = 20k messages/sec

Per Notification Service Instance:
  100k connections / 3 instances ≈ 33k connections per instance
  20k messages × 2 directions (send + ack) = 40k msg/sec per instance
  Each message: 100-200 bytes
  Network throughput: 40k × 200 bytes = 8 MB/sec per instance
  Available bandwidth (10 Gbps): Plenty of headroom ✓

Message Queue Depth (Redis):
  Target: < 100ms latency
  If 20k msg/sec arrives and 40k msg/sec capacity:
  Queue depth: ~500 messages (< 50ms backlog) ✓
```

### Resource Limits

```
CPU:
  Per message: ~1ms processing (parse, route, send)
  20k msg/sec → 20 sec CPU-seconds
  Available: 8 cores × 1000 ms = 8000 ms
  Utilization: 20 / 8000 = 0.25% ✓ (Very light)

Memory:
  Connections: 33k × 10 KB = 330 MB
  Message buffer: 1,000 pending messages × 200 bytes = 200 KB
  Heap: 2 GB (comfortable headroom)

Network (per instance):
  Incoming: Kafka + Redis Pub/Sub: ~2 MB/sec
  Outgoing: WebSocket frames: ~8 MB/sec
  Total: ~10 MB/sec (available: 10 Gbps = 1,250 MB/sec)
  Utilization: 10 / 1,250 = 0.8% ✓
```

---

## 8. Testing Strategy

### Unit Tests

```
Test cases:
  1. Message serialization/deserialization
  2. Message ID deduplication
  3. Subscription filtering (ride_id matches)
  4. Heartbeat timeout detection
  5. Exponential backoff calculation
```

### Integration Tests

```
Test cases:
  1. Full ride lifecycle (request → completion)
  2. Location updates flowing rider → driver
  3. ETA updates with various traffic scenarios
  4. Mobile reconnection after network loss
  5. Message backlog delivery after offline period
  6. Multiple active rides (user switching between rides)
  7. Concurrent connections (load test)
```

### Load Testing

```
Scenario: 10k concurrent rides, 100k total messages/sec

Tools: Apache JMeter, Gatling
Setup:
  - Spin up 1,000 concurrent WebSocket connections
  - Simulate 10 sec ride duration
  - Verify:
    - All messages delivered
    - Latency < 100ms (P95)
    - No connection drops
    - CPU/Memory stable
```

---

## 9. Failure Scenarios

| Scenario | Impact | Recovery |
|----------|--------|----------|
| **Notification Service crashes** | All connected users lose WebSocket | Reconnect via exponential backoff (< 10 sec) |
| **Redis Pub/Sub down** | Cross-instance messaging fails (if not co-located) | Sticky sessions handle local messages; global recovery after Redis up |
| **Network partition (rider ↔ server)** | Rider can't receive updates | Mobile app detects, initiates reconnection (< 1 min) |
| **Kafka lag (> 1 min)** | Real-time updates delayed | Still within acceptable range; riders tolerate lag up to 5 min |
| **Message ID collision** | Duplicate messages sent | Idempotency key prevents client-side duplicate processing |

---

## Summary: Real-Time Communication Decisions

| Decision | Rationale | Trade-off |
|----------|-----------|-----------|
| **WebSocket** | Low latency; bidirectional; efficient | Stateful service; connection management |
| **Redis Pub/Sub** | Decoupled; scales horizontally | Extra dependency; consistency challenges |
| **At-least-once delivery** | No lost messages | Possible duplicates (deduped client-side) |
| **30-second heartbeat** | Detect stale connections; reduce battery drain | Adds 20% overhead |
| **Exponential backoff reconnection** | Graceful degradation; prevents thundering herd | Longer initial reconnect times (up to 32s) |
| **Message ID deduplication** | Idempotency; recovery from network loss | Extra storage; cleanup complexity |

---

**Next**: Folder Structure, Non-Functional Requirements
