# Ride Service

Core microservice for ride lifecycle management in the Rideshare platform. Implements the state machine, event sourcing, and payment integration for the critical ride business logic.

## Overview

The Ride Service manages the complete lifecycle of rides from request creation to completion or cancellation. It enforces strict state machine rules, publishes events for real-time updates, and maintains an audit trail of all state changes.

### Key Responsibilities

1. **Ride Creation**: Validate and create new ride requests
2. **Driver Assignment**: Accept driver assignments from Matching Engine
3. **State Machine**: Enforce valid state transitions (REQUESTED → MATCHED → ACCEPTED → ARRIVED → STARTED → COMPLETED/CANCELLED)
4. **Event Sourcing**: Maintain immutable audit trail of all events
5. **Payment Integration**: Process charges and refunds
6. **Rating & Feedback**: Collect ratings after ride completion

## Architecture

### State Machine

```
REQUESTED (initial state)
    ├─→ MATCHED (driver assigned, 30 sec expiry)
    │   ├─→ ACCEPTED (driver confirmed)
    │   │   ├─→ ARRIVED (driver at pickup)
    │   │   │   ├─→ STARTED (rider boarded)
    │   │   │   │   └─→ COMPLETED (arrived at dropoff)
    │   │   │   └─→ CANCELLED
    │   │   └─→ CANCELLED
    │   └─→ CANCELLED
    └─→ CANCELLED
```

### Technology Stack

- **Framework**: Spring Boot 3.3.0 with Spring Data JPA
- **Language**: Java 21
- **Database**: PostgreSQL with Flyway migrations
- **Messaging**: Apache Kafka (event streaming)
- **Caching**: Redis (optional, for session management)
- **Build**: Maven 3.8+

### Project Structure

```
ride-service/
├── src/main/java/com/rideshare/ride/
│   ├── RideServiceApplication.java          # Entry point
│   ├── controller/
│   │   └── RideController.java              # REST endpoints
│   ├── service/
│   │   ├── RideService.java                 # Core business logic & state machine
│   │   └── PaymentProcessor.java            # Payment integration
│   ├── repository/
│   │   ├── RideRepository.java              # Ride data access
│   │   └── RideEventRepository.java         # Event sourcing
│   ├── entity/
│   │   ├── Ride.java                        # JPA entity
│   │   ├── RideStatus.java                  # State enum
│   │   ├── RideEvent.java                   # Audit trail entity
│   │   └── RideEventType.java               # Event types
│   ├── dto/
│   │   ├── CreateRideRequest.java           # Create ride DTO
│   │   ├── RideResponse.java                # Response DTO
│   │   ├── AssignDriverRequest.java         # Driver assignment DTO
│   │   ├── CancelRideRequest.java           # Cancellation DTO
│   │   ├── RatingRequest.java               # Rating DTO
│   │   ├── StatusUpdateRequest.java         # Status update DTO
│   │   └── RideEventResponse.java           # Event response DTO
│   ├── event/
│   │   └── RideEventPublisher.java          # Kafka event publishing
│   ├── validator/
│   │   └── RideStateValidator.java          # State transition validation
│   └── exception/
│       ├── RideNotFoundException.java
│       ├── InvalidStateTransitionException.java
│       └── RideAlreadyCompletedException.java
├── src/main/resources/
│   ├── application.yaml                     # Dev configuration
│   ├── application-prod.yaml                # Production configuration
│   └── db/migration/
│       ├── V1__Create_rides_table.sql
│       └── V2__Create_ride_events_table.sql
├── src/test/java/com/rideshare/ride/
│   ├── service/RideServiceTest.java         # Service unit tests
│   ├── validator/RideStateValidatorTest.java # Validator tests
│   └── controller/RideControllerTest.java   # Controller integration tests
└── pom.xml                                  # Maven configuration
```

## API Endpoints

### Create Ride
```http
POST /api/v1/rides
Content-Type: application/json

{
  "riderId": "RIDER-123",
  "pickupLatitude": 40.7128,
  "pickupLongitude": -74.0060,
  "pickupAddress": "123 Main St, NYC",
  "dropoffLatitude": 40.7580,
  "dropoffLongitude": -73.9855,
  "dropoffAddress": "456 Park Ave, NYC",
  "passengerCount": 2
}

Response: 201 Created
{
  "id": "RIDE-abc123",
  "riderId": "RIDER-123",
  "status": "REQUESTED",
  "pickupLatitude": 40.7128,
  "pickupLongitude": -74.0060,
  "pickupAddress": "123 Main St, NYC",
  "dropoffLatitude": 40.7580,
  "dropoffLongitude": -73.9855,
  "dropoffAddress": "456 Park Ave, NYC",
  "passengerCount": 2,
  "createdAt": "2026-06-02T10:00:00",
  "updatedAt": "2026-06-02T10:00:00"
}
```

### Get Ride Details
```http
GET /api/v1/rides/{ride_id}

Response: 200 OK
{
  "id": "RIDE-abc123",
  "riderId": "RIDER-123",
  "driverId": "DRIVER-456",
  "status": "MATCHED",
  "estimatedFare": 25.50,
  "estimatedDurationSeconds": 600,
  ...
}
```

### Assign Driver
```http
PUT /api/v1/rides/{ride_id}/driver
Content-Type: application/json

{
  "driverId": "DRIVER-456",
  "estimatedFare": 25.50,
  "estimatedDurationSeconds": 600,
  "driverETA": 120
}

Response: 200 OK
{
  "id": "RIDE-abc123",
  "status": "MATCHED",
  "driverId": "DRIVER-456",
  "estimatedFare": 25.50,
  "matchedAt": "2026-06-02T10:05:00",
  ...
}
```

### Update Ride Status
```http
PUT /api/v1/rides/{ride_id}/status
Content-Type: application/json

{
  "status": "ACCEPTED",
  "initiatorId": "DRIVER-456",
  "initiatorType": "DRIVER"
}

Response: 200 OK
{
  "id": "RIDE-abc123",
  "status": "ACCEPTED",
  "acceptedAt": "2026-06-02T10:06:00",
  ...
}
```

### Cancel Ride
```http
POST /api/v1/rides/{ride_id}/cancel
Content-Type: application/json

{
  "reason": "Driver not arriving",
  "initiatorId": "RIDER-123",
  "initiatorType": "RIDER"
}

Response: 200 OK
{
  "id": "RIDE-abc123",
  "status": "CANCELLED",
  "cancellationReason": "Driver not arriving",
  "cancellationInitiator": "RIDER",
  "cancelledAt": "2026-06-02T10:10:00",
  ...
}
```

### Complete Ride
```http
POST /api/v1/rides/{ride_id}/complete?actualFare=28.75

Response: 200 OK
{
  "id": "RIDE-abc123",
  "status": "COMPLETED",
  "actualFare": 28.75,
  "actualDurationSeconds": 900,
  "completedAt": "2026-06-02T10:15:00",
  ...
}
```

### Rate Ride
```http
POST /api/v1/rides/{ride_id}/rating
Content-Type: application/json

{
  "raterId": "RIDER-123",
  "raterType": "RIDER",
  "rating": 5,
  "feedback": "Excellent service!"
}

Response: 200 OK
{
  "id": "RIDE-abc123",
  "status": "COMPLETED",
  "driverRating": 5,
  "driverFeedback": "Excellent service!",
  ...
}
```

### Get Rider Rides
```http
GET /api/v1/riders/{rider_id}/rides?page=0&size=10

Response: 200 OK
{
  "content": [...],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 42,
  "totalPages": 5
}
```

### Get Driver Rides
```http
GET /api/v1/drivers/{driver_id}/rides?page=0&size=10

Response: 200 OK
{
  "content": [...],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 156,
  "totalPages": 16
}
```

### Get Ride Events (Audit Trail)
```http
GET /api/v1/rides/{ride_id}/events?page=0&size=20

Response: 200 OK
{
  "content": [
    {
      "id": 1,
      "rideId": "RIDE-abc123",
      "eventType": "RIDE_REQUESTED",
      "previousStatus": null,
      "newStatus": "REQUESTED",
      "initiatorId": null,
      "initiatorType": "SYSTEM",
      "createdAt": "2026-06-02T10:00:00"
    },
    {
      "id": 2,
      "rideId": "RIDE-abc123",
      "eventType": "RIDE_MATCHED",
      "previousStatus": "REQUESTED",
      "newStatus": "MATCHED",
      "initiatorId": "MATCHING-ENGINE",
      "initiatorType": "SYSTEM",
      "createdAt": "2026-06-02T10:05:00"
    },
    ...
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 8,
  "totalPages": 1
}
```

## Kafka Events

The service publishes events to the following Kafka topics for real-time consumption:

| Topic | Event | Payload |
|-------|-------|---------|
| `ride.requested` | New ride created | `ride_id, rider_id, pickup, dropoff, passenger_count` |
| `ride.matched` | Driver assigned | `ride_id, driver_id, estimated_fare, estimated_duration_seconds, driver_eta` |
| `ride.accepted` | Driver confirmed | `ride_id, driver_id, accepted_at` |
| `ride.arrived` | Driver at pickup | `ride_id, driver_id, arrived_at` |
| `ride.started` | Ride began | `ride_id, rider_id, driver_id, started_at` |
| `ride.completed` | Ride finished | `ride_id, rider_id, driver_id, actual_fare, actual_duration_seconds, completed_at` |
| `ride.cancelled` | Ride cancelled | `ride_id, reason, initiated_by, cancelled_at` |

## Database Schema

### rides table
- **Primary Key**: `id` (VARCHAR 32)
- **Sharding**: `shard_id` (0-7) for horizontal scaling
- **Indexes**:
  - `idx_rider_id` - Fast lookup by rider
  - `idx_driver_id` - Fast lookup by driver
  - `idx_status` - State machine queries
  - `idx_created_at` - Time-based pagination
  - `idx_shard_id` - Sharding support
  - Compound indexes for common queries

### ride_events table
- **Primary Key**: `id` (BIGSERIAL)
- **Foreign Key**: `ride_id` -> rides.id (ON DELETE CASCADE)
- **Immutable**: Never updated, only inserted
- **Indexes**:
  - `idx_ride_id_event` - Event retrieval by ride
  - `idx_event_type` - Event type filtering
  - `idx_created_at_event` - Chronological ordering

## Running the Service

### Prerequisites
- Java 21+
- Maven 3.8+
- PostgreSQL 14+
- Apache Kafka (for production)
- Redis (optional, for caching)

### Development

```bash
# Build
mvn clean install

# Run (uses in-memory H2 for testing, configure PostgreSQL for real testing)
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"

# Test
mvn test

# Test coverage report
mvn verify
# Open target/site/jacoco/index.html
```

### Production

```bash
# Build JAR
mvn clean package -P prod

# Run with environment variables
export DB_URL=jdbc:postgresql://ride-db:5432/rideshare_rides
export DB_USER=postgres
export DB_PASSWORD=secure_password
export KAFKA_BROKERS=kafka-1:9092,kafka-2:9092,kafka-3:9092
export REDIS_HOST=redis-cache

java -jar target/ride-service-1.0.0.jar --spring.profiles.active=prod
```

## Configuration

### Development (application.yaml)
- PostgreSQL on localhost:5432
- Kafka on localhost:9092
- Redis on localhost:6379
- Connection pooling: 20 connections
- Logging: DEBUG for ride service, INFO for Spring

### Production (application-prod.yaml)
- PostgreSQL with HikariCP (50 connections max)
- Kafka with SASL/SSL authentication
- Redis with SSL/TLS
- Connection pooling: 50 connections
- Logging: WARN for most, INFO for ride service
- Metrics: Prometheus enabled
- Health checks: Detailed monitoring

## Testing

### Test Coverage Target: >80%

Run tests with coverage report:
```bash
mvn clean verify
open target/site/jacoco/index.html
```

### Test Classes

1. **RideServiceTest** (23 test cases)
   - Ride creation and validation
   - State machine transitions
   - Event publishing
   - Payment processing
   - Rating and feedback
   - Error handling

2. **RideStateValidatorTest** (16 test cases)
   - Valid transitions
   - Invalid transitions
   - Terminal state checks
   - Transition prerequisites
   - Edge cases

3. **RideControllerTest** (12 test cases)
   - All API endpoints
   - Request validation
   - HTTP status codes
   - Response format validation

## Performance Characteristics

### Latency Targets
- Ride creation: <100ms (p99)
- Status updates: <50ms (p99)
- Payment processing: Async, no blocking
- Event publishing: Fire-and-forget to Kafka

### Throughput
- Sustained: 10,000+ rides/min
- Peak: 50,000+ rides/min with proper scaling

### Database Optimization
- Connection pooling with HikariCP
- Batch processing in Hibernate
- Query optimization with indexes
- Read replicas for historical queries

## Security

- HTTPS/TLS for all API calls (in production)
- SQL injection prevention via parameterized queries
- XSS protection via Spring Security
- CORS configuration (configurable)
- Rate limiting (to be implemented in API Gateway)
- Input validation with Spring Validation

## Monitoring & Observability

- Spring Boot Actuator endpoints (/health, /metrics)
- Prometheus metrics export
- Structured logging (JSON format recommended in prod)
- Distributed tracing (OpenTelemetry ready)
- ELK stack integration recommended

## Error Handling

### HTTP Status Codes
- `201 Created` - Ride created successfully
- `200 OK` - Successful operation
- `400 Bad Request` - Validation error
- `404 Not Found` - Ride not found
- `409 Conflict` - Invalid state transition
- `500 Internal Server Error` - Unexpected error

### Common Error Responses

```json
{
  "error": "RideNotFoundException",
  "message": "Ride not found with ID: RIDE-invalid",
  "timestamp": "2026-06-02T10:00:00",
  "status": 404
}
```

## Future Enhancements

1. **Sharding Router**: Implement data source routing based on shard ID
2. **Event Replay**: Rebuild state from event store
3. **Matching Timeout**: Auto-cancel rides if no driver accepts within 30s
4. **Dynamic Pricing**: Calculate estimated fare based on demand
5. **Ride Splitting**: Support multiple pickup/dropoff locations
6. **Accessibility**: Special ride types for accessibility needs
7. **Promotions**: Discount codes and loyalty integration
8. **Analytics**: Real-time ride metrics and KPIs

## Contributing

- Follow clean architecture principles
- Maintain >80% test coverage
- Use constructor injection only
- Document public methods with JavaDoc
- Keep database migrations backward-compatible

## License

Proprietary - Rideshare Platform

## Support

For issues or questions, contact the backend engineering team.
