# Notification Service

Real-time WebSocket notifications with Kafka event streaming and Redis connection state persistence.

## Overview

The Notification Service provides real-time ride updates to drivers and riders through WebSocket connections. It consumes events from Kafka, deduplicates messages, maintains connection state in Redis, and delivers updates with sub-100ms latency.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      External Systems                            │
├─────────────────────────────────────────────────────────────────┤
│ Kafka Topics:                    Browser/Mobile Clients:         │
│ - ride.matched                   - WebSocket connections         │
│ - ride.status_changed            - Subscribe to rides            │
│ - location.changed               - Receive location updates      │
│ - eta.updated                    - Receive status changes        │
│ - ride.cancelled                                                 │
└─────────────────────────────────────────────────────────────────┘
            │                                      │
            ▼                                      ▼
┌──────────────────────────┐      ┌──────────────────────────┐
│  KafkaEventListener      │      │  WebSocketController     │
│  - Consumes events       │      │  - Handle connections    │
│  - Transforms to DTOs    │      │  - Parse messages        │
└──────────────────────────┘      └──────────────────────────┘
            │                                      │
            └──────────────────┬───────────────────┘
                               ▼
                    ┌──────────────────────────┐
                    │  NotificationService     │
                    │  - Orchestration         │
                    └──────────────────────────┘
                               │
            ┌──────────────────┼──────────────────┐
            ▼                  ▼                  ▼
    ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
    │ MessageRouter    │  │ WebSocket        │  │ ConnectionState  │
    │ - Subscriptions  │  │ ConnectionMgr    │  │ Service          │
    │ - Routing        │  │ - In-memory      │  │ - Redis persist  │
    │ - Deduplication  │  │ - Lifecycle      │  │ - Recovery       │
    └──────────────────┘  └──────────────────┘  └──────────────────┘
            │                  │                       │
            └──────────────────┼───────────────────────┘
                               ▼
                    ┌──────────────────────────┐
                    │  Connected WebSocket     │
                    │  Clients (Rider/Driver)  │
                    └──────────────────────────┘
```

## Features

### Real-time Communication
- WebSocket connections at `/ws` endpoint
- Sub-100ms message delivery latency
- Bi-directional communication (subscribe/unsubscribe)

### Connection Management
- In-memory connection tracking by user and ride
- Redis-backed state persistence for recovery
- Automatic connection cleanup on disconnect
- Graceful shutdown with 60-second drain period

### Message Reliability
- **Deduplication**: Prevents duplicate messages within 1-hour window
- **Ordering**: Messages ordered per ride using sequence numbers
- **Delivery Guarantee**: Acknowledges Kafka consumption after successful routing

### Scalability
- Horizontal scaling with stateless instances
- Shared Redis backend for connection state
- Metrics recording via Micrometer (Prometheus-compatible)
- Connection recovery across instance boundaries

## WebSocket API

### Endpoint
```
ws://localhost:8081/api/v1/ws
```

### Message Types

#### Subscribe to Ride
```json
{
  "type": "subscribe",
  "ride_id": "R123"
}
```

#### Unsubscribe from Ride
```json
{
  "type": "unsubscribe",
  "ride_id": "R123"
}
```

#### Heartbeat (Keep-alive)
```json
{
  "type": "ping"
}
```

### Incoming Messages

#### Driver Location Updated
```json
{
  "message_id": "uuid",
  "type": "driver.location_updated",
  "ride_id": "R123",
  "timestamp": "2026-06-02T14:30:00Z",
  "data": {
    "driver_id": "D456",
    "lat": 40.7128,
    "lng": -74.0060
  }
}
```

#### ETA Updated
```json
{
  "message_id": "uuid",
  "type": "eta_updated",
  "ride_id": "R123",
  "timestamp": "2026-06-02T14:30:00Z",
  "data": {
    "eta_minutes": 5
  }
}
```

#### Ride Status Changed
```json
{
  "message_id": "uuid",
  "type": "ride.status_changed",
  "ride_id": "R123",
  "timestamp": "2026-06-02T14:30:00Z",
  "data": {
    "status": "STARTED",
    "timestamp": "2026-06-02T14:30:00Z"
  }
}
```

#### Ride Matched
```json
{
  "message_id": "uuid",
  "type": "ride.matched",
  "ride_id": "R123",
  "timestamp": "2026-06-02T14:30:00Z",
  "data": {
    "driver_id": "D456",
    "driver_photo": "https://...",
    "driver_rating": 4.8,
    "vehicle_info": {
      "make": "Toyota",
      "model": "Prius",
      "color": "White",
      "plate": "ABC123"
    }
  }
}
```

#### Ride Cancelled
```json
{
  "message_id": "uuid",
  "type": "ride.cancelled",
  "ride_id": "R123",
  "timestamp": "2026-06-02T14:30:00Z",
  "data": {
    "reason": "DRIVER_CANCELLED",
    "timestamp": "2026-06-02T14:30:00Z"
  }
}
```

## Kafka Integration

### Consumed Topics

| Topic | Format | Frequency | Purpose |
|-------|--------|-----------|---------|
| `ride.matched` | JSON | Per matched ride | Notify rider and driver of match |
| `ride.status_changed` | JSON | Per status change | Notify of ride progress |
| `location.changed` | JSON | Every 5-10 seconds | Update rider on driver location |
| `eta.updated` | JSON | Every 2-5 minutes | Update estimated arrival time |
| `ride.cancelled` | JSON | When cancelled | Notify cancellation |

### Message Schema

```json
{
  "message_id": "uuid",
  "ride_id": "R123",
  "user_id": "U456",
  "timestamp": "2026-06-02T14:30:00Z",
  "data": {
    // Event-specific data
  }
}
```

## Configuration

### Development (application.yaml)
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
  data:
    redis:
      host: localhost
      port: 6379
server:
  port: 8081
```

### Production (application-prod.yaml)
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
  data:
    redis:
      host: ${REDIS_HOST}
      password: ${REDIS_PASSWORD}
      ssl: true
logging:
  level:
    root: WARN
    com.rideshare.notification: INFO
```

## Connection Lifecycle

### Connection Recovery (Mobile)

When mobile app reconnects:

1. Client initiates new WebSocket connection
2. Load balancer may route to different Notification Service instance
3. New instance checks Redis for previous connection state
4. Previous subscriptions restored from Redis
5. Pending messages delivered (if queued)
6. Real-time updates resume

### Graceful Shutdown

When service shuts down:

1. Accept no new connections
2. Notify all connected clients: `{"type": "server.shutdown"}`
3. Wait 60 seconds for graceful client disconnection
4. Force-close remaining connections
5. Exit cleanly

## Metrics

Available via Prometheus endpoint `/actuator/metrics`:

- `websocket.connections.active` - Current active connections
- `websocket.connections.opened` - Total connections opened
- `websocket.connections.closed` - Total connections closed
- `websocket.connections.reconnected` - Total reconnections
- `websocket.message.delivery.latency` - Message delivery time (ms)
- `websocket.messages.delivered` - Total messages delivered (by type)
- `websocket.messages.duplicates` - Duplicate messages detected
- `kafka.events.received` - Kafka events consumed (by type)

## Performance Targets

- **Connection latency**: <50ms
- **Message delivery latency**: <100ms (p99)
- **Kafka processing latency**: <200ms (p99)
- **Connection recovery**: <2 seconds
- **Duplicate detection**: <10ms (Redis lookup)

## Building and Running

### Build
```bash
mvn clean package -pl notification-service
```

### Run
```bash
# Development
mvn spring-boot:run -pl notification-service

# Production
java -jar notification-service-1.0.0.jar --spring.profiles.active=prod
```

### Test
```bash
# Unit tests
mvn test -pl notification-service

# Integration tests
mvn test -pl notification-service -Dgroups=integration
```

## Testing

### Unit Tests (>80% coverage)
- `NotificationServiceTest` - Service orchestration
- `WebSocketConnectionManagerTest` - Connection lifecycle
- `MessageRouterTest` - Event routing and deduplication
- `ConnectionStateServiceTest` - Redis persistence

### Integration Tests
- `WebSocketIntegrationTest` - Full notification flow

### Test Coverage
```
Target: >80% line coverage
Excluded: Test classes, Configuration
```

## Troubleshooting

### High Connection Count
- Check for proper unsubscribe/disconnect handling in clients
- Verify Redis expiration is working (30-minute TTL)
- Monitor `websocket.connections.active` metric

### Duplicate Messages
- Increase deduplication TTL if needed (currently 1 hour)
- Verify Kafka consumer group isolation
- Check for client-side resends

### Slow Message Delivery
- Monitor `websocket.message.delivery.latency` metric
- Check Redis connection pool availability
- Verify Kafka broker latency

### Connection Drops
- Enable client-side heartbeat/ping every 30 seconds
- Implement exponential backoff reconnection
- Check firewall/load balancer timeout settings

## Dependencies

- Spring Boot 3.3.0 (WebSocket, Redis)
- Spring Kafka 3.0.0 (Event consumption)
- Redis Jedis 5.x (Connection state persistence)
- Micrometer (Metrics collection)

## Related Services

- **Ride Service**: Publishes ride.matched, ride.status_changed, ride.cancelled events
- **Location Service**: Publishes location.changed events
- **ETA Service**: Publishes eta.updated events
- **Auth Service**: Provides user authentication for WebSocket connections

## Development Notes

### Adding a New Event Type

1. Add Kafka topic listener in `KafkaEventListener`
2. Update `NotificationEvent` DTO if needed
3. Create unit test for routing
4. Update Kafka topics list in configuration
5. Update WebSocket API documentation

### Connection State Recovery

Connection state is stored in Redis with format:
```
Key: conn:{connection_id}
Value: ConnectionState{userId, activeRides, connectedAt, lastHeartbeat}
TTL: 30 minutes
```

When client reconnects with same user ID, check Redis for previous state and restore subscriptions.

### Message Deduplication

Messages are deduplicated per ride using Redis set with TTL:
```
Key: msg:dedup:{ride_id}
Value: Set<message_id>
TTL: 1 hour
```

Only the first message with a given ID is delivered; subsequent duplicates are silently dropped.
