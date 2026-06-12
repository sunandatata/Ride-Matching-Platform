# High-Level System Architecture

## Overview

The ride-matching platform is designed as a horizontally scalable, microservices-based system with clear service boundaries, enabling independent scaling of compute-intensive components (matching engine) and stateful services (ride management).

## Core Services

### 1. **Rider Service**
**Responsibility**: Manage rider profiles, authentication, and ride history.

**Key Capabilities**:
- Rider registration and profile management
- Authentication and authorization
- Ride history and ratings
- Payment information (PCI-compliant storage reference)

**Scaling**: Stateless service with read-heavy workload; easily horizontal scaled behind load balancer.

---

### 2. **Driver Service**
**Responsibility**: Manage driver profiles, authentication, availability status, and ratings.

**Key Capabilities**:
- Driver registration and KYC verification
- License and vehicle information
- Availability status (online/offline/on-ride)
- Driver ratings and acceptance rates
- Vehicle details and capacity

**Scaling**: Stateless service; can be horizontally scaled. Driver state (online/offline) is transient and lives in Location Service or Redis.

---

### 3. **Location Service**
**Responsibility**: Ingest and manage real-time driver locations; serve as the source of truth for spatial queries.

**Key Capabilities**:
- Ingest driver location updates (high throughput: ~100k+ locations/sec)
- Maintain current driver positions in low-latency store (Redis Geo or PostGIS)
- Provide spatial queries (nearby drivers within radius)
- Track driver availability state
- Publish location change events

**Scaling**: CPU-bound for location ingestion; scales horizontally with sharding by geography or driver_id ranges. Backing store (Redis Cluster or PostgreSQL replicas) must handle high read throughput.

---

### 4. **Matching Engine Service**
**Responsibility**: Find best drivers for ride requests and assign rides.

**Key Capabilities**:
- Spatial discovery (find drivers within radius using Location Service)
- Driver ranking (distance, rating, acceptance rate, ETA)
- Availability checking (is driver on a ride, vehicle full)
- Assignment logic with retry/fallback
- Match result persistence
- Matching event publishing

**Scaling**: The most compute-intensive service. Horizontal scaling by ride request sharding. Target: <200ms matching latency for 10k+ concurrent rides.

---

### 5. **Ride Service**
**Responsibility**: Manage ride lifecycle from request to completion.

**Key Capabilities**:
- Ride request creation and validation
- Ride state management (requested → matched → picked_up → completed → cancelled)
- Ride history and archival
- Ride metadata (pickup/dropoff locations, estimated fare, passenger count)
- Ride cancellation handling
- Payment settlement

**Scaling**: Horizontally scalable stateless service with PostgreSQL as persistent store. Ride state is also published to event stream for event sourcing.

---

### 6. **ETA Service**
**Responsibility**: Calculate and update estimated times of arrival.

**Key Capabilities**:
- Calculate ETA from driver location to pickup
- Calculate ETA from pickup to dropoff
- Update ETAs as ride progresses
- Route optimization (if multi-stop rides supported)
- Handle traffic/routing data integration

**Scaling**: Can call external routing APIs (Google Maps, HERE, OSRM). Local caching of route segments to reduce API calls. Horizontal scaling with request batching.

---

### 7. **Notification Service**
**Responsibility**: Deliver real-time updates to riders and drivers.

**Key Capabilities**:
- WebSocket connection management for live updates
- Push notification delivery (in-app, mobile push)
- Notification persistence for offline clients
- Event subscription and routing

**Scaling**: Stateful service; connection state per client. Horizontal scaling via sticky sessions or Redis pub/sub for state sharing. One instance per WebSocket connection pool.

---

### 8. **Authentication Service**
**Responsibility**: Handle user authentication and JWT token management.

**Key Capabilities**:
- User login/logout
- JWT token generation and validation
- OAuth2 integration (optional: social login)
- Token refresh and revocation
- MFA support

**Scaling**: Stateless service; token validation is cache-friendly. Redis for token blacklist/revocation cache.

---

## System Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           CLIENT LAYER                                         │
├─────────────────────┬────────────────────────┬──────────────────────────────────┤
│  Rider Mobile App   │  Driver Mobile App     │      Web Dashboard               │
│  (React Native)     │  (React Native)        │      (React + TypeScript)        │
└────────┬────────────┴──────────┬─────────────┴──────────────────┬───────────────┘
         │                       │                                │
         │ HTTP/WebSocket        │ HTTP/WebSocket                │ HTTP/WebSocket
         │                       │                                │
┌────────┴───────────────────────┴────────────────────────────────┴───────────────┐
│                         API GATEWAY & LOAD BALANCER                             │
│                        (Kubernetes Ingress + NGINX)                             │
└────────┬───────────────────────┬────────────────────────────────┬───────────────┘
         │                       │                                │
    ┌────┴──────┬───────┬───────┬──────────┬───────┬──────────┬──┴──────────┐
    │            │       │       │          │       │          │             │
┌───▼──┐    ┌───▼──┐ ┌──▼───┐ ┌─▼────┐ ┌──▼──┐ ┌─▼─────┐ ┌──▼───┐ ┌──▼─────┐
│Rider │    │Driver│ │Auth  │ │Ride  │ │Loca-│ │Match- │ │ETA   │ │Notif   │
│Service│   │Service│ │Service│ │Service│ │tion │ │ ing   │ │Service│ │Service │
└───┬──┘    └───┬──┘ └──┬───┘ └─┬────┘ └──┬──┘ └─┬─────┘ └──┬───┘ └──┬─────┘
    │           │      │       │          │       │          │        │
    └───────────┴──────┴───────┴──────────┴───────┴──────────┴────────┘
                        │                │
                ┌───────▼────────────────▼──────────┐
                │   Event Streaming (Kafka/RabbitMQ) │
                │   - Location.Changed              │
                │   - Ride.Requested                │
                │   - Ride.Matched                  │
                │   - Ride.Started                  │
                │   - Ride.Completed               │
                │   - Driver.StatusChanged          │
                └────────────┬──────────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
    ┌────▼─────┐        ┌────▼─────┐      ┌─────▼────┐
    │PostgreSQL │        │Redis      │      │PostGIS   │
    │(Primary)  │        │(Cache &   │      │(Spatial  │
    │           │        │Location)  │      │Queries)  │
    │- Riders   │        │           │      │          │
    │- Drivers  │        │- Driver   │      │- Geo     │
    │- Rides    │        │  Locations│      │  Index   │
    │- Payments │        │- Session  │      │          │
    └───────────┘        │  Tokens   │      └──────────┘
                         └───────────┘

                   ┌──────────────────────────────┐
                   │  Monitoring & Logging        │
                   │  (Prometheus + Grafana)      │
                   │  (ELK Stack)                 │
                   └──────────────────────────────┘
```

---

## Service Interaction Matrix

| Initiator | Target | Protocol | Purpose | Frequency |
|-----------|--------|----------|---------|-----------|
| Ride Service | Matching Engine | Async Event | Trigger driver assignment | Per ride request (~100/sec) |
| Matching Engine | Location Service | gRPC/HTTP | Query nearby drivers | Per ride request |
| Matching Engine | Driver Service | HTTP | Fetch driver details & ratings | Per driver candidate |
| Matching Engine | ETA Service | HTTP | Calculate ETAs for ranking | Per ride request |
| Notification Service | Event Stream | Event Subscribe | Get ride status updates | Real-time |
| Location Service | Event Stream | Event Publish | Broadcast location changes | High frequency |
| Ride Service | Payment Service | HTTP | Process payments (external) | Per ride completion |

---

## Data Flow: High-Level Sequence

### Ride Request → Matching → Completion

```
Rider App                   Ride Service                 Matching Engine
    │                            │                              │
    ├─ POST /rides ─────────────>│                              │
    │  (pickup, dropoff)         │                              │
    │                       [Create Ride]                       │
    │                       [Publish RideRequested Event]       │
    │                            ├──────────────────────────────>│
    │                            │                        [Query Location Service]
    │                            │                        [Rank Drivers]
    │                            │                        [Assign Top Driver]
    │                            │<──────────────────────────────┤
    │                       [Update Ride Status]                │
    │<─ 200 OK (pending) ───────│                              │
    │  with ride_id              │                              │
    │                            │ (WebSocket: ride.matched)    │
    │<─ Push Update ─────────────┤─ Driver Assignment          │
    │  (driver_id, location,     │                              │
    │   ETA to pickup)           │                              │
    │                            │ (WebSocket: driver accepted) │
    │<─ Push Update ─────────────┤─ Start Navigation           │
    │  (Driver is en route)      │                              │

Driver App                   Ride Service                 Location Service
    │                            │                              │
    │─ PUT /drivers/location ─────────────────────────────────>│
    │  (lat, lng, timestamp)     │                              │
    │                            │<─ Location update received ──┤
    │<─ 200 OK ─────────────────│                              │
    │  (batch every N ms)        │                              │
```

---

## Communication Patterns

### Synchronous (Request-Response)
- **API Gateway → Services**: REST/HTTP for client requests
- **Matching Engine → Location/ETA Services**: gRPC for low-latency queries
- **Matching Engine → Driver Service**: HTTP for driver details

### Asynchronous (Event-Driven)
- **Ride Service → Event Stream**: Publish ride lifecycle events
- **Location Service → Event Stream**: Publish location updates
- **Notification Service → Event Stream**: Subscribe to ride/location changes
- **Event Stream → External Integrations**: Order history, analytics, fraud detection

### Real-Time (WebSocket)
- **Notification Service ↔ Client Apps**: Persistent connection for push updates
- **Pub/Sub Pattern**: Rooms per ride (rider + driver + ops receive updates)

---

## High-Availability Strategy

1. **Service Redundancy**: All services deployed with 3+ replicas
2. **Database Replication**: PostgreSQL primary-replica with failover
3. **Cache Layer**: Redis Cluster for location/session distribution
4. **Circuit Breakers**: Fallback routing when Matching Engine degraded
5. **Load Balancing**: Kubernetes Service load balancing across replicas
6. **Health Checks**: Liveness and readiness probes on all services
7. **Graceful Shutdown**: Drain connections before termination

---

## Deployment Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │  Namespace   │  │  Namespace   │  │  Namespace   │    │
│  │   (Zone A)   │  │   (Zone B)   │  │   (Zone C)   │    │
│  │              │  │              │  │              │    │
│  │ ┌─────────┐  │  │ ┌─────────┐  │  │ ┌─────────┐  │    │
│  │ │ Rider   │  │  │ │ Rider   │  │  │ │ Rider   │  │    │
│  │ │ Service │  │  │ │ Service │  │  │ │ Service │  │    │
│  │ └─────────┘  │  │ └─────────┘  │  │ └─────────┘  │    │
│  │ ┌─────────┐  │  │ ┌─────────┐  │  │ ┌─────────┐  │    │
│  │ │ Matching│  │  │ │ Matching│  │  │ │ Matching│  │    │
│  │ │ Engine  │  │  │ │ Engine  │  │  │ │ Engine  │  │    │
│  │ └─────────┘  │  │ └─────────┘  │  │ └─────────┘  │    │
│  │ ┌─────────┐  │  │ ┌─────────┐  │  │ ┌─────────┐  │    │
│  │ │Location │  │  │ │Location │  │  │ │Location │  │    │
│  │ │Service  │  │  │ │Service  │  │  │ │Service  │  │    │
│  │ └─────────┘  │  │ └─────────┘  │  │ └─────────┘  │    │
│  │              │  │              │  │              │    │
│  └──────────────┘  └──────────────┘  └──────────────┘    │
│                                                             │
│  Persistent Storage (Outside K8s)                          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ PostgreSQL Primary (Region A)                       │   │
│  │ with Replicas in Region B, C (read-only)          │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Redis Cluster (distributed across zones)           │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Trade-offs and Decisions

| Decision | Rationale | Trade-off |
|----------|-----------|-----------|
| **Microservices over Monolith** | Enable independent scaling of matching engine; clear responsibility boundaries | Increased operational complexity; distributed debugging |
| **Event-driven Communication** | Loose coupling; enables async processing; audit trail for ride history | Eventual consistency; message ordering challenges |
| **Redis for Location Cache** | Sub-millisecond queries; in-memory geo-indexing | Memory cost; cache invalidation complexity |
| **Async Location Ingestion** | Handle 100k+ locations/sec without blocking client | Higher latency tolerance for UI (100-200ms is acceptable) |
| **gRPC for Matching Queries** | Binary protocol; better performance than REST for high-throughput internal APIs | Polyglot language support complexity |
| **Kubernetes Deployment** | Auto-scaling, self-healing, declarative infrastructure | Learning curve; operational overhead |

---

## Performance Targets

- **Ride Request Latency**: < 100ms (user perceives instant confirmation)
- **Matching Latency**: < 200ms (from request submission to driver assignment)
- **Location Update Latency**: < 500ms (acceptable for driver navigation)
- **ETA Update Latency**: < 1s (acceptable for rider UX)
- **WebSocket Message Delivery**: < 100ms (driver and rider see updates in real-time)

---

## Next Steps

Detailed sections follow covering:
1. Data flow specifics (ride request, location, matching, completion)
2. Database design (tables, indexes, sharding)
3. API contracts (REST endpoints, error handling)
4. Matching engine algorithm (ranking, scalability)
5. Real-time communication (WebSocket architecture)
6. Folder structure (frontend/backend organization)
7. Non-functional requirements (security, reliability, scalability)
