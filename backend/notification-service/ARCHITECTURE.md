# Notification Service Architecture

## Service Overview

The Notification Service is a high-performance, horizontally-scalable component that delivers real-time ride updates to drivers and riders through WebSocket connections. It consumes Kafka events, maintains connection state in Redis, and guarantees message delivery with sub-100ms latency.

## Design Principles

### 1. Separation of Concerns

**Controllers** (`WebSocketController`)
- Handle only WebSocket frame parsing and lifecycle
- Extract user ID and manage session attributes
- Delegate business logic to services

**Services** (Tier 1)
- `NotificationService`: Orchestrates all operations
- `WebSocketConnectionManager`: Manages in-memory connection registry
- `ConnectionStateService`: Persists state to Redis
- `MessageRouter`: Routes events to subscribers

**Utilities**
- `MessageDeduplicator`: Prevents message duplication
- `MetricsRecorder`: Records operational metrics

**Event Processing**
- `KafkaEventListener`: Consumes and transforms Kafka events

### 2. Dependency Injection

All dependencies injected via **constructor** for immutability and explicit dependencies:

```java
@Service
@RequiredArgsConstructor  // Constructor injection via Lombok
public class NotificationService {
    private final WebSocketConnectionManager connectionManager;
    private final ConnectionStateService connectionStateService;
    private final MessageRouter messageRouter;
    private final MetricsRecorder metricsRecorder;
}
```

### 3. Data Flow

```
┌─────────────────┐
│  Kafka Event    │
└────────┬────────┘
         │
         ▼
┌──────────────────────────┐
│ KafkaEventListener       │
│ - Parse JSON             │
│ - Create NotificationEvent
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│ NotificationService      │
│ - processKafkaEvent()    │
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│ MessageRouter            │
│ - routeEvent()           │
│ - Check deduplication    │
│ - Get subscribers        │
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│ WebSocketConnectionMgr   │
│ - getUserSessions()      │
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│ Send TextMessage         │
│ via WebSocketSession     │
└──────────────────────────┘
```

## Core Components

### NotificationService (Orchestrator)

**Responsibilities:**
- Coordinate connection management, subscriptions, and event processing
- Provide single entry point for all notification operations
- Manage connection lifecycle (connect, subscribe, disconnect)
- Handle graceful shutdown

**Key Methods:**
```java
String handleConnection(String userId, WebSocketSession session)
void handleDisconnection(String connectionId)
void handleSubscribe(String connectionId, String rideId)
void handleUnsubscribe(String connectionId, String rideId)
void handleHeartbeat(String connectionId)
int processKafkaEvent(NotificationEvent event)
void gracefulShutdown()
```

### WebSocketConnectionManager (In-Memory Registry)

**Responsibilities:**
- Track active WebSocket connections in memory
- Map connections to users for quick lookup
- Manage connection lifecycle and cleanup
- Broadcast messages to user's sessions

**Data Structure:**
```java
Map<String, Map<String, WebSocketSession>> userConnections
  └─ userId
      └─ connectionId → WebSocketSession

Map<String, String> connectionToUserId
  └─ connectionId → userId
```

**Performance:** O(1) connection lookup

### ConnectionStateService (Redis Persistence)

**Responsibilities:**
- Persist connection state to Redis with TTL (30 minutes)
- Enable connection recovery across service instances
- Track user subscriptions per connection
- Support graceful timeout and cleanup

**Redis Keys:**
```
conn:{connectionId} → ConnectionState
  {
    connectionId: string,
    userId: string,
    instanceId: string,
    activeRides: Set<string>,
    connectedAt: Instant,
    lastHeartbeat: Instant
  }
user:conns:{userId} → Set<connectionId>
```

**Recovery Flow:**
1. Mobile client reconnects
2. New instance checks Redis for previous connection state
3. Restores subscriptions from `activeRides`
4. Delivers pending messages (if implemented)

### MessageRouter (Event Dispatcher)

**Responsibilities:**
- Maintain subscription registry (ride → subscribers)
- Route events to subscribed users
- Deduplicate messages
- Record delivery metrics

**Data Structure:**
```java
Map<String, Set<String>> rideSubscriptions
  └─ rideId
      └─ userId
```

**Features:**
- Deduplication via `MessageDeduplicator`
- Metrics recording via `MetricsRecorder`
- Synchronized access for thread-safety

### KafkaEventListener (Event Consumption)

**Responsibilities:**
- Consume from 5 Kafka topics
- Parse JSON and create DTOs
- Delegate to `NotificationService`
- Handle processing errors with logging

**Topics:**
- `ride.matched` → `ride.matched` event
- `ride.status_changed` → `ride.status_changed` event
- `location.changed` → `driver.location_updated` event
- `eta.updated` → `eta_updated` event
- `ride.cancelled` → `ride.cancelled` event

### WebSocketController (HTTP Handler)

**Responsibilities:**
- Handle WebSocket connection lifecycle
- Parse incoming messages
- Route to appropriate handlers
- Extract and store user ID in session attributes

**Message Types:**
- `subscribe` → handleSubscribe()
- `unsubscribe` → handleUnsubscribe()
- `ping` → handleHeartbeat()

## State Management

### In-Memory (WebSocketConnectionManager)
- **Scope**: Single service instance
- **Lifetime**: Connection duration
- **Loss**: Lost on restart (acceptable - clients reconnect)
- **Concurrency**: ConcurrentHashMap

### Redis (ConnectionStateService)
- **Scope**: Cluster-wide
- **Lifetime**: 30 minutes (TTL)
- **Loss**: Lost after 30 minutes inactivity
- **Recovery**: Mobile clients auto-reconnect

### Redis (MessageDeduplicator)
- **Scope**: Cluster-wide
- **Lifetime**: 1 hour (TTL)
- **Purpose**: Prevent duplicate delivery within 1-hour window
- **Format**: Set<messageId> per ride

## Scalability Considerations

### Horizontal Scaling
```
Load Balancer
    ↓
┌─────────────────┬─────────────────┬─────────────────┐
│ Notification-1  │ Notification-2  │ Notification-3  │
│ (WebSocket)     │ (WebSocket)     │ (WebSocket)     │
└────────┬────────┴────────┬────────┴────────┬────────┘
         └────────────────┬──────────────────┘
                          ↓
                    Shared Redis
                 (Connection State)
                    Shared Kafka
                  (Event Stream)
```

### Connection Recovery
When client reconnects to different instance:

1. New instance receives WebSocket connection
2. `handleConnection()` calls `connectionStateService.saveConnection()`
3. Check Redis for previous connection state
4. Restore subscriptions from `activeRides`
5. Client subscribes to new rides
6. Resume real-time updates

### Kafka Scaling
- **Consumer Group**: `notification-service` (automatic rebalancing)
- **Partitions**: One per topic (can increase)
- **Concurrency**: Configured per topic
- **Guarantee**: At-least-once delivery with manual ACK

## Thread Safety

### Synchronized Resources

**WebSocketConnectionManager:**
```java
Map<String, Map<String, WebSocketSession>> userConnections = new ConcurrentHashMap<>();
Map<String, String> connectionToUserId = new ConcurrentHashMap<>();
```

**MessageRouter:**
```java
Map<String, Set<String>> rideSubscriptions = new HashMap<>();  // Synchronized access in methods
```

**Kafka Listeners:**
- Automatic thread-pool management via Spring Kafka
- Manual acknowledgment for ordering guarantees

## Message Ordering

### Per-Ride Ordering
Messages for the same ride maintain order via:
1. Kafka topic partition key (rideId)
2. Single listener thread per partition
3. Sequence number tracking in deduplicator

### Cross-Ride Independence
Different rides process independently → no artificial ordering constraints

## Error Handling

### Kafka Processing
```java
private void processEvent(String messageJson, String eventType) {
    try {
        NotificationEvent event = objectMapper.readValue(messageJson, NotificationEvent.class);
        notificationService.processKafkaEvent(event);
    } catch (Exception e) {
        log.error("Failed to process Kafka event", e);
        // Message NACKed → Kafka retries (configurable backoff)
    }
}
```

### WebSocket Delivery
```java
private boolean deliverMessage(WebSocketSession session, NotificationEvent event) {
    if (!session.isOpen()) {
        return false;  // Skip closed sessions
    }
    try {
        session.sendMessage(new TextMessage(messageJson));
        return true;
    } catch (Exception e) {
        log.warn("Failed to deliver message", e);
        return false;  // Log but continue
    }
}
```

## Configuration

### Development (application.yaml)
- Kafka: localhost:9092
- Redis: localhost:6379
- Port: 8081
- Logging: DEBUG
- Concurrency: Low (CPU count / 2)

### Production (application-prod.yaml)
- Kafka: Cluster (ENV variable)
- Redis: Cluster with SSL (ENV variable)
- Port: Configurable (ENV variable)
- Logging: INFO/WARN
- Concurrency: High (scaled to load)
- Compression: Enabled (snappy)

## Performance Optimization

### Message Delivery (<100ms p99)

1. **Async WebSocket Send** (Spring default)
   - Non-blocking I/O
   - Buffered output stream

2. **In-Memory Connection Lookup** (O(1))
   - Direct HashMap access
   - No database queries

3. **Deduplication** (<10ms)
   - Redis SET lookup
   - Single round-trip

4. **JSON Serialization** (Jackson)
   - Streaming serialization
   - Pre-configured ObjectMapper

### Connection Recovery (<2 seconds)

1. **Redis Connection State** (O(1) read)
   - Direct key lookup
   - No deserialization overhead

2. **Subscription Restoration** (O(n) where n = rides)
   - Batch subscribe operations
   - Linear in active rides

### Kafka Processing

1. **Manual Acknowledgment**
   - Message processed before ACK
   - Guaranteed delivery

2. **Configurable Concurrency**
   - 1 thread per partition (guarantees ordering)
   - Multiple threads per broker (parallel processing)

## Metrics and Observability

### Prometheus Metrics

**Counters:**
- `websocket.connections.opened` - Total connections created
- `websocket.connections.closed` - Total connections closed
- `websocket.connections.reconnected` - Total reconnections
- `websocket.messages.delivered[type]` - Messages by type
- `websocket.messages.duplicates` - Duplicate detections
- `kafka.events.received[type]` - Events by type

**Gauges:**
- `websocket.connections.active` - Current active connections
- `websocket.subscriptions.total` - Current active subscriptions

**Timers:**
- `websocket.message.delivery.latency` - Delivery time distribution
- Individual operation timers (optional)

### Health Checks

**Kubernetes Probes:**
```
GET /actuator/health/liveness  → Service running?
GET /actuator/health/readiness → Ready to serve?
```

**Components:**
- Redis connectivity
- Kafka consumer lag
- WebSocket handler availability

## Testing Strategy

### Unit Tests (>80% coverage)

**Service Tests:**
- `NotificationServiceTest` - Orchestration logic
- `WebSocketConnectionManagerTest` - Connection lifecycle
- `MessageRouterTest` - Event routing and deduplication

**Utility Tests:**
- Deduplication logic
- Metrics recording

### Integration Tests

**Full Flow Tests:**
- `WebSocketIntegrationTest`
  - Connection → Subscribe → Event → Delivery
  - Multiple subscribers
  - Duplicate handling
  - Disconnection cleanup

**Test Infrastructure:**
- Mock WebSocketSession
- In-memory collections
- Test-specific configuration

### Test Coverage

```
Target: >80% line coverage
Excluded: Test classes, Configuration classes

Areas covered:
- Happy path (connect → subscribe → receive → disconnect)
- Error paths (unknown connection, invalid messages)
- Edge cases (duplicate detection, concurrent access)
- Recovery scenarios (reconnection, state restoration)
```

## Deployment Checklist

- [ ] Redis cluster configured and running
- [ ] Kafka topics created and replicated
- [ ] SSL certificates installed (production)
- [ ] Environment variables configured
- [ ] Monitoring/alerting set up
- [ ] Graceful shutdown configured
- [ ] Health checks passing
- [ ] Load testing completed
- [ ] Rollback plan documented

## Future Enhancements

1. **Message Queueing** - Queue messages during client offline
2. **Presence Broadcasting** - Notify when drivers/riders come online
3. **Typing Indicators** - Show when driver is responding to chat
4. **Push Notifications** - Fallback to FCM/APNs when WebSocket unavailable
5. **Event Prioritization** - Route critical events with higher priority
6. **Rate Limiting** - Prevent message floods per user/ride
7. **Compression** - Compress large payloads (>1KB)

## References

- [WebSocket Protocol (RFC 6455)](https://tools.ietf.org/html/rfc6455)
- [Spring WebSocket Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#websocket)
- [Redis Stream for Message Queues](https://redis.io/docs/data-types/streams/)
- [Kafka Consumer Groups](https://kafka.apache.org/documentation/#consumerconfigs)
