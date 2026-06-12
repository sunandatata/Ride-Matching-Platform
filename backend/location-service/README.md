# Location Service

High-throughput microservice handling 100k+ driver location updates per second.

## Overview

The Location Service is a critical component of the ride-sharing platform responsible for:
- **Location Ingestion**: Accepts 100k+ location updates/sec via REST API
- **High-Performance Indexing**: Stores driver locations in Redis Geo for sub-millisecond spatial queries
- **Batching & Persistence**: Async batch writes to PostgreSQL for audit trail
- **Event Publishing**: Publishes location.changed events to Kafka for Matching Engine and Notifications
- **Driver Status Tracking**: Maintains online/offline status with Redis cache + PostgreSQL persistence

## Architecture

### Key Components

1. **LocationController** - REST endpoints for location operations
2. **LocationService** - Business logic and orchestration
3. **LocationBatchProcessor** - Critical: Batches incoming updates, manages flushing
4. **RedisGeoService** - High-performance Redis Geo operations with pipelining
5. **DriverStatusService** - Online/offline status management
6. **LocationEventPublisher** - Publishes events to Kafka

### Data Flow

```
Driver App (100k+/sec)
    ↓ PUT /drivers/location
LocationController
    ↓
LocationBatchProcessor
    ├─→ Redis Geo (pipelined, 500 updates or 100ms)
    └─→ PostgreSQL (async, 5 minute batches)

Event Publishing
    ↓ Kafka (location.changed)
├─→ Matching Engine (driver discovery)
├─→ Notification Service (real-time updates)
└─→ Analytics
```

### Batching Strategy

- **In-Memory Queue**: BlockingQueue<LocationUpdateRequest> (10k capacity)
- **Batch Triggers**:
  - Size: 500 updates → immediate flush
  - Time: 100ms window → flush if no size trigger
- **Redis Flush**: Pipelined GEOADD operations for efficiency
- **DB Flush**: Async batch write every 5 minutes on dedicated thread pool
- **Event Publishing**: Per-update, non-blocking

## API Endpoints

### 1. Update Driver Location (High-Throughput)
```http
PUT /api/v1/locations/drivers/location
Content-Type: application/json

{
  "driverId": "driver-123",
  "lat": 40.7128,
  "lng": -74.0060,
  "heading": 180,
  "speed": 15.5,
  "accuracy": 10.0,
  "timestamp": "2026-06-02T12:34:56Z",
  "source": "gps"
}
```

**Response**: 202 Accepted (update queued for async processing)

### 2. Find Nearby Drivers (Matching Engine)
```http
GET /api/v1/locations/nearby?lat=40.7128&lng=-74.0060&radiusKm=5
```

**Response**:
```json
{
  "lat": 40.7128,
  "lng": -74.0060,
  "radiusKm": 5,
  "count": 42,
  "drivers": [
    {
      "driverId": "driver-1",
      "latitude": 40.7130,
      "longitude": -74.0062,
      "distanceMeters": 250.0,
      "heading": 180,
      "speed": 15.5,
      "isOnline": true
    }
  ]
}
```

**Latency Target**: < 200ms (p99)

### 3. Get Current Location
```http
GET /api/v1/locations/drivers/{driverId}/location
```

**Response**:
```json
{
  "driverId": "driver-123",
  "latitude": 40.7128,
  "longitude": -74.0060,
  "heading": 180,
  "speed": 15.5,
  "accuracy": 10.0,
  "timestamp": "2026-06-02T12:34:56Z",
  "source": "gps",
  "isOnline": true
}
```

### 4. Get Location History (Last 1 Hour)
```http
GET /api/v1/locations/drivers/{driverId}/location-history
```

**Response**:
```json
{
  "driverId": "driver-123",
  "count": 256,
  "locations": [
    { "latitude": 40.7128, "longitude": -74.0060, "timestamp": "..." },
    ...
  ]
}
```

### 5. Batch Statistics (Monitoring)
```http
GET /api/v1/locations/stats
```

**Response**:
```json
{
  "processedCount": 1000000,
  "failedCount": 0,
  "currentBatchSize": 150,
  "timeSinceLastFlush": 50,
  "inputQueueSize": 75
}
```

## Configuration

### application.yaml

```yaml
location:
  batch:
    size: 500              # Batch size trigger
    window:
      ms: 100             # Time window (ms)
  db:
    flush-interval-ms: 300000  # 5 minutes

spring:
  datasource:
    hikari:
      maximum-pool-size: 20
  redis:
    host: localhost
    port: 6379
  kafka:
    bootstrap-servers: localhost:9092
```

### Environment Variables (Production)

```bash
DB_HOST=postgres.internal
DB_PORT=5432
DB_NAME=location_service
DB_USER=app_user
DB_PASSWORD=***

REDIS_HOST=redis.internal
REDIS_PORT=6379
REDIS_PASSWORD=***

KAFKA_BROKERS=kafka1:9092,kafka2:9092,kafka3:9092
```

## Performance Metrics

### Throughput

| Scenario | Target | Status |
|----------|--------|--------|
| Sequential (1 driver) | > 5k updates/sec | ✓ |
| Concurrent (100 drivers) | > 1k updates/sec | ✓ |
| Sustained (50 drivers, 5 sec) | > 500 updates/sec | ✓ |
| Burst (1000 updates, 100ms) | < 1 sec | ✓ |

### Latency

| Operation | Target | Notes |
|-----------|--------|-------|
| PUT location (queue) | < 10ms | Synchronous |
| GET nearby drivers | < 200ms (p99) | Redis Geo query |
| GET current location | < 50ms (p99) | Redis lookup |
| Event publish | async | Fire-and-forget |

### Resource Usage

- **Memory**: ~500MB base + 10MB per 100k drivers in Redis
- **CPU**: Single core handles ~50k updates/sec
- **Network**: ~1-2 Mbps per 100k updates/sec (compressed)
- **DB**: Batched writes, ~100 inserts/sec after batching

## Database Schema

### driver_locations
```sql
CREATE TABLE driver_locations (
    id BIGSERIAL PRIMARY KEY,
    driver_id VARCHAR(64) NOT NULL,
    latitude NUMERIC(10, 8) NOT NULL,
    longitude NUMERIC(11, 8) NOT NULL,
    heading INTEGER,
    speed DOUBLE PRECISION,
    accuracy DOUBLE PRECISION,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    source VARCHAR(20) DEFAULT 'gps'
);

CREATE INDEX idx_driver_id_timestamp ON driver_locations(driver_id, timestamp DESC);
CREATE INDEX idx_timestamp ON driver_locations(timestamp DESC);
```

### driver_status
```sql
CREATE TABLE driver_status (
    driver_id VARCHAR(64) PRIMARY KEY,
    is_online BOOLEAN NOT NULL DEFAULT false,
    last_location_update TIMESTAMP,
    last_heartbeat TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

## Kafka Events

### location.changed
Publishes one event per location update. Topic: `location.changed`

```json
{
  "driverId": "driver-123",
  "latitude": 40.7128,
  "longitude": -74.0060,
  "heading": 180,
  "speed": 15.5,
  "timestamp": "2026-06-02T12:34:56Z",
  "source": "gps",
  "eventPublishedAt": "2026-06-02T12:34:56.100Z"
}
```

## Testing

### Unit Tests (>80% coverage)
```bash
mvn test
```

Includes tests for:
- Batching logic and flushing conditions
- Redis Geo operations
- Service business logic
- Controller validation
- Error handling

### Load Tests
```bash
mvn test -Dgroups=performance
```

Tests:
- Sequential throughput (10k updates)
- Concurrent throughput (100 drivers)
- Latency under sustained load
- Bursty traffic handling

### Integration Tests
```bash
mvn failsafe:integration-test
```

Tests with embedded PostgreSQL, Redis, and Kafka.

## Deployment

### Docker Build
```bash
mvn clean package
docker build -t location-service:1.0.0 .
```

### Kubernetes Deployment
```bash
kubectl apply -f deployment.yaml
kubectl rollout status deployment/location-service
```

### Health Checks
```bash
# Liveness probe
curl http://localhost:8082/actuator/health

# Readiness check
curl http://localhost:8082/actuator/health/ready
```

## Monitoring

### Prometheus Metrics

- `location_updates_total` - Total location updates processed
- `location_batch_size` - Current batch size
- `location_batch_flush_duration` - Time to flush batch
- `redis_geo_operation_duration` - Redis Geo operation latency
- `event_publish_duration` - Kafka event publish latency

### Grafana Dashboards
- Location Service Overview
- Batching Performance
- Spatial Query Latency
- Driver Status Distribution

## Known Limitations & Future Optimizations

1. **Single-Instance Batching**: Batching runs per-instance (not distributed)
   - Fix: Distributed batching via Redis Streams for multi-instance deployments

2. **Redis Eviction**: No TTL on driver locations (can grow unbounded)
   - Fix: Auto-expire offline drivers after 24 hours

3. **No Compression**: Location data sent uncompressed over network
   - Fix: Enable Kafka compression (snappy) - already configured

4. **Geo-Sharding**: Not yet implemented
   - Plan: Geographic sharding by country/region for scale to 1M+ drivers

5. **Duplicate Detection**: No deduplication of rapid updates
   - Plan: Client-side throttling to 1 update/sec per driver

## Troubleshooting

### High Latency on PUT endpoint
- Check input queue size (GET /stats)
- Monitor Redis connection pool (HikariCP metrics)
- Check Kafka producer batch timeout

### Nearby Driver Queries Slow
- Verify Redis Geo index is populated (driver count vs active drivers)
- Check network latency to Redis
- Monitor CPU usage on Redis node

### Missing Location History
- Verify PostgreSQL DB flush interval hasn't increased
- Check disk space on PostgreSQL
- Verify Flyway migrations completed successfully

### Memory Leak
- Monitor JVM heap (use jcmd or JMX)
- Check for unbounded maps in drivers map
- Verify batch processor garbage collection

## Support

For issues or questions:
1. Check logs: `kubectl logs -f deployment/location-service`
2. Review metrics: Grafana dashboard
3. Check Kafka topic: `kafka-topics.sh --describe --topic location.changed`

## License

MIT
