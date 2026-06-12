# Non-Functional Requirements

This document specifies performance targets, security requirements, scalability goals, and reliability standards.

---

## 1. Performance Requirements

### Latency Targets

| Operation | P50 | P95 | P99 | SLA |
|-----------|-----|-----|-----|-----|
| **Ride Request Submission** | 50ms | 100ms | 200ms | < 200ms (99th percentile) |
| **Driver Matching** | 60ms | 150ms | 200ms | < 200ms (95th percentile) |
| **Location Update** | 200ms | 500ms | 1000ms | < 1s (acceptable async) |
| **ETA Update** | 100ms | 500ms | 1500ms | < 2s |
| **WebSocket Message Delivery** | 10ms | 50ms | 100ms | < 100ms (real-time) |
| **API Response (typical)** | 100ms | 200ms | 500ms | < 500ms (95th percentile) |
| **Database Query (indexed)** | 5ms | 20ms | 100ms | < 100ms (99th percentile) |

### Throughput Targets

| Operation | Target Throughput | Peak Multiplier | Measurement |
|-----------|-------------------|-----------------|-------------|
| **Ride Requests** | 1,000 req/sec sustained | 10x peak | requests/second |
| **Location Updates** | 100,000 updates/sec | 1.5x peak | location updates/second |
| **Matching Engine** | 1,000 matches/sec | 5x peak | rides matched/second |
| **WebSocket Messages** | 20,000 msg/sec | 2x peak | messages/second |
| **Database Writes** | 10,000 writes/sec | 3x peak | transactions/second |
| **Database Reads** | 50,000 reads/sec | 2x peak | queries/second |

### Resource Utilization Targets

```
CPU Utilization:
  Normal: 30-50%
  Peak: 70-80%
  Absolute max: < 95% (with autoscaling triggered)

Memory Utilization:
  Normal: 40-60%
  Peak: 75-85%
  Absolute max: < 90%

Disk I/O:
  Target: < 70% utilization
  Spike tolerance: < 90%

Network Bandwidth:
  Target: < 50% of available
  Peak: < 80% of available

Database Connection Pool:
  Usage: < 80% of max connections
  Alert: > 75% utilization

Cache Hit Rate:
  Driver details cache: > 90%
  Route cache: > 80%
  Location cache: > 95%
  User profile cache: > 85%
```

---

## 2. Availability & Reliability

### Uptime Targets (SLA)

```
Overall System:
  Target: 99.9% uptime (four nines)
  Acceptable downtime: 43 minutes per month
  Budget: 43 minutes/month for maintenance & incidents

Per Service:
  Critical (matching, location): 99.95% (4.4 min/month)
  High (ride, driver, rider): 99.9% (43 min/month)
  Medium (payment, notification): 99.5% (216 min/month)
  Low (admin, analytics): 99% (7.2 hours/month)

Measurement:
  - Uptime calculated via health check endpoints
  - Exclude scheduled maintenance windows (announced > 24h)
  - Include all failure modes (crashes, deployments, network)
```

### Disaster Recovery

```
Recovery Time Objective (RTO): 15 minutes
  Target: Restore service to 95% capacity within 15 minutes

Recovery Point Objective (RPO): 5 minutes
  Target: Recover data with < 5 minutes of loss

Backup Strategy:
  - PostgreSQL: Continuous WAL archival (point-in-time recovery)
  - Redis: RDB snapshots every 1 hour + AOF
  - Kafka: 7-day retention for event replay
  - Application state: Stateless (easier recovery)

Testing:
  - Monthly DR drills (simulate failure scenarios)
  - Automated failover tests (primary → replica)
  - Data restoration tests (from backup)

Failover Triggers:
  - Primary database down: Automatic promotion of replica (< 30 sec)
  - Service instance crash: Kubernetes auto-restart (< 60 sec)
  - Data center outage: Manual failover to backup DC (< 15 min)
```

### High Availability Architecture

```
Service Replication:
  All services: Minimum 3 replicas
  Critical services (matching): 5 replicas
  Replicas distributed across 3+ availability zones

Load Balancing:
  - API Gateway: Health-check based routing
  - Database: Read replicas for read-heavy queries
  - Redis: Cluster mode (automatic shard rebalancing)
  - Kafka: Multiple brokers (replication factor 3)

Graceful Degradation:
  - If 1 matching engine shard down: Remaining shards handle 7x traffic
  - If ETA service slow: Use fallback distance estimate
  - If Redis down: Fallback to database for location queries (slower)
  - If Kafka down: Queue assignments in local memory (limited window)

Circuit Breaker Configuration:
  Failure threshold: 5 consecutive failures
  Timeout per request: 2 seconds
  Recovery timeout: 30 seconds
  Action: Fail-open (use fallback) or fail-closed (reject request)
```

---

## 3. Scalability

### Horizontal Scaling

```
Ride Service:
  Current: 1 instance per 500 req/sec
  Max load (10,000 concurrent rides = 100+ req/sec):
    Instances needed: 100 / 500 = 0.2 → 1 instance
    Recommended (with headroom): 3-4 instances
  Scale trigger: CPU > 70%, add 1 instance

Location Service:
  Current: 1 instance per 20k location updates/sec
  Max load (100,000 updates/sec):
    Instances needed: 100,000 / 20,000 = 5 instances
  Scale trigger: Throughput > 15k/sec, add 1 instance

Matching Engine:
  Current: 1 instance per 150 matches/sec
  Max load (10,000 concurrent rides = 1,000+ matches/sec):
    Instances needed: 1,000 / 150 ≈ 7 instances
  Scale trigger: Latency P95 > 150ms OR CPU > 80%, add 2 instances

Notification Service:
  Current: 1 instance per 100k connections (memory bound)
  10,000 concurrent rides = 20k connections:
    Instances needed: 1 instance
  Recommended (with failover): 3 instances
  Scale trigger: Memory > 75%, add 1 instance
```

### Vertical Scaling Limits

```
Node Configuration:
  CPU: 16 cores (optimal for I/O-bound services)
  Memory: 64 GB (sufficient for connection pooling, caching)
  Disk: 500 GB SSD (for logs, temporary storage)

Scaling decision tree:
  If CPU-bound → Horizontal scale (add instances)
  If Memory-bound → Increase cache eviction OR horizontal scale
  If Network-bound → Add load balancers, optimize payload
  If Disk-bound → Archive logs, increase IOPS

Database vertical scaling:
  Single PostgreSQL instance scales to ~50,000 QPS
  Beyond: Use read replicas + sharding (horizontal)
```

### Database Scaling

```
Current Capacity (Single Instance):
  Connections: 100 (configurable)
  Queries/sec: 50,000 (indexed queries)
  Write throughput: 10,000 transactions/sec
  Ride table rows: 10 billion (with 3-month retention)

Scaling Strategy:

1. Reads: Read Replicas
   Setup: Primary + 3 replicas (async replication)
   Distribution: Distribute read queries across replicas
   Use case: Ride history, analytics, reports

2. Writes: Sharding
   Strategy: 8 shards by ride_id % 8
   Per-shard capacity: 50,000 / 8 ≈ 6,000 writes/sec per shard
   Sufficient for 10k concurrent rides

3. Archive: Time-based partitioning
   Strategy: Monthly partitions (rides_2026_06, rides_2026_07)
   Retention: 3 months hot (PostgreSQL), 7 years cold (S3)
   Archival: Move partitions > 3 months to S3, drop from DB

Sharding Implementation:
  Shard ID = ride_id % 8
  Connection routing: App determines shard, routes query
  Example: Ride R123 → R123 % 8 = 3 → Route to shard-3
```

### Cache Scaling

```
Redis Cluster Configuration:
  Nodes: 16 nodes (geohash partitioned)
  Replication: 1 master + 1 replica per node (high availability)
  Memory: 4 GB per node = 64 GB total
  Distribution: Across 3+ availability zones

Keyspace Breakdown:
  Driver locations (Geo): 100k drivers × 100 bytes ≈ 10 GB
  Driver details: 100k drivers × 1 KB ≈ 100 MB
  Route cache: 1M route segments × 100 bytes ≈ 100 MB
  Session tokens: 1M sessions × 200 bytes ≈ 200 MB
  Other: 200 MB

Total: ~11 GB (leaves 53 GB headroom for growth)

Eviction Policy: allkeys-lru (evict least recently used)
Limits: 90% memory threshold (trigger eviction)
```

---

## 4. Security Requirements

### Authentication & Authorization

```
User Authentication:
  Method: JWT tokens (HS256 signing)
  Token lifetime: 1 hour (access token)
  Refresh token: 30 days (for automatic re-authentication)
  Revocation: Blacklist stored in Redis (TTL = token expiry)

Role-Based Access Control (RBAC):
  Roles: RIDER, DRIVER, ADMIN, SUPPORT
  Permissions:
    - RIDER: Create ride, view own rides, rate driver, manage payment methods
    - DRIVER: Accept rides, update location, complete rides, view earnings
    - ADMIN: View all rides, manage users, configure system settings
    - SUPPORT: View rides/details, issue refunds, manage tickets

Authorization checks:
  - Rider cannot view other riders' rides
  - Driver cannot modify ride they're not assigned to
  - Payment API: Verify user owns payment method before use
  - Scope validation: X-Auth-Scope header must match endpoint
```

### Data Protection

```
Encryption at Rest:
  Database: PostgreSQL with pgcrypto extension
  Sensitive fields encrypted:
    - Payment method tokens (PCI-DSS compliant)
    - Phone numbers (rider/driver)
    - Social security numbers (KYC data)
  Encryption algorithm: AES-256
  Key management: AWS KMS (envelope encryption)

Encryption in Transit:
  Protocol: TLS 1.3 (minimum)
  Certificate: Wildcard certificate for *.rideshare.local
  Pinning: Certificate pinning in mobile apps (public key)

Data Minimization:
  - Never store full credit card numbers (tokenized)
  - Mask phone numbers in logs (display last 4 digits)
  - PII logging: Only enabled in debug mode (disabled in production)

PCI-DSS Compliance:
  - Payment processing: Delegated to Stripe (PCI-compliant service)
  - Never store full payment details in our database
  - Tokens stored: Stripe token (not actual card data)
  - Annual PCI audit: By qualified third party
```

### API Security

```
Rate Limiting:
  Per-user (authenticated): 100 requests/minute
  Per-IP (anonymous): 20 requests/minute
  Matching engine (internal): 10,000 requests/minute

DDoS Protection:
  - API Gateway rate limiting (first line)
  - CloudFlare/WAF (network layer)
  - Implement slow-read attack defense (timeouts)

Input Validation:
  - All inputs validated server-side (never trust client)
  - SQL injection prevention: Parameterized queries (PreparedStatement)
  - XSS prevention: HTML escaping in responses
  - CSRF protection: Double-submit cookie pattern

API Versioning:
  - URL versioning: /api/v1/, /api/v2/
  - Deprecation: 6-month notice before version removal
  - Backward compatibility: Maintain support for 2 prior versions
```

### Infrastructure Security

```
Network Security:
  - VPC with public/private subnets
  - Bastion host: SSH access only through bastion
  - Network ACLs: Restrict traffic between subnets
  - Security groups: Whitelist specific ports only
  - No direct database access from internet

Container Security:
  - Image scanning: Scan for vulnerabilities before deployment
  - Runtime security: Pod Security Policy enforces best practices
  - Network policies: Service-to-service communication whitelisted
  - RBAC: Kubernetes RBAC for service accounts

Secrets Management:
  - External: Never commit secrets to git
  - Storage: AWS Secrets Manager or Kubernetes Sealed Secrets
  - Rotation: Automatic rotation every 90 days
  - Audit: Log all secret access

Monitoring & Logging:
  - Centralized logging: ELK stack (Elasticsearch, Logstash, Kibana)
  - Log retention: 90 days hot, 1 year cold (S3)
  - PII masking: Automatic redaction of sensitive fields
  - Audit trail: All privileged actions logged (user_id, action, timestamp)

Vulnerability Management:
  - Dependency scanning: Regular updates via Dependabot
  - Security scanning: SAST (static analysis) on code commits
  - Penetration testing: Annual security audit
  - Incident response: Security team on-call 24/7
```

---

## 5. Consistency & Durability

### ACID Guarantees

```
Transaction Isolation Level: SERIALIZABLE
  Prevents: Dirty reads, non-repeatable reads, phantom reads
  Trade-off: Slightly lower throughput due to locking

Ride Assignment Consistency:
  Problem: Two matching engines assign same ride to different drivers
  Solution: Transactional UPDATE with WHERE clause checking ride status
  Guarantee: Exactly-once semantics (if transaction succeeds, no duplicates)

Event Ordering:
  Problem: Events arrive out-of-order in Kafka
  Solution: Partition by ride_id (all events for a ride in same partition)
  Guarantee: Total ordering for events within a ride

Idempotency:
  Problem: Duplicate requests (network retry) create duplicate rides
  Solution: Idempotency key (client provides unique key)
  Implementation: Store (idempotency_key, response) in database
  Guarantee: Duplicate requests return same response
```

### Eventual Consistency Model

```
Strongly Consistent Data (Rides):
  - Ride creation: ACID transaction
  - Ride status updates: Transactional
  - Latency: < 100ms

Weakly Consistent Data (Analytics):
  - Event log: Published asynchronously
  - Analytics aggregations: Eventually consistent (< 5 min delay)
  - Acceptable: Count of rides by status (refresh every 5 min)

Cache Consistency:
  - Cache invalidation: On write, delete cache key
  - TTL: 5 min (time-based expiry)
  - Refresh: On read miss, fetch from database
  - Acceptable staleness: < 5 minutes for driver details

Change Data Capture:
  - Event sourcing: ride_events table (append-only log)
  - Kafka: Real-time stream of events
  - Analytics: Consume Kafka for reporting (eventual consistency)
```

---

## 6. Observability

### Logging

```
Log Levels:
  DEBUG: Detailed execution flow (disabled in production)
  INFO: Significant events (service started, ride matched)
  WARN: Unusual conditions (slow query, cache miss)
  ERROR: Failure conditions (database error, API timeout)
  FATAL: System-level failures (OOM, service crash)

Log Fields (structured logging):
  Timestamp: ISO 8601 format
  Level: DEBUG, INFO, WARN, ERROR, FATAL
  Service: auth-service, matching-service, etc.
  Request ID: Unique ID for request tracing
  User ID: Rider/driver performing action
  Ride ID: Ride being processed
  Message: Human-readable message
  Stack trace: For errors

Log Retention:
  Hot (ELK): 90 days (searchable, quick queries)
  Warm (S3): 1 year (less frequent access)
  Cold (Glacier): 7 years (compliance, archival)

Sampling (to reduce volume):
  Info/Warn: 100% sampling
  Debug: 10% sampling (every 10th request)
  Error: 100% sampling (always log errors)
```

### Metrics

```
Golden Signals (measure these):
  1. Latency: Response time distributions
  2. Traffic: Requests per second
  3. Errors: Error rate (5xx responses)
  4. Saturation: Resource utilization (CPU, memory, connections)

Key Metrics by Service:

Matching Engine:
  - Latency (P50, P95, P99)
  - Throughput (matches/sec)
  - Success rate (% of successful assignments)
  - Driver assignment rate (% rides matched within 30s)
  - Circuit breaker state (open/closed)

Location Service:
  - Ingestion rate (updates/sec)
  - Batch latency (end-to-end)
  - Redis write latency
  - Cache hit rate

Ride Service:
  - Request latency
  - Status transition latency
  - State machine violations (invalid transitions)
  - Database query latency (by query type)

Notification Service:
  - Connection count (active WebSockets)
  - Message delivery latency
  - Reconnection rate
  - Message deduplication (duplicates handled)

Database:
  - Query latency (by query type)
  - Connection pool usage
  - Replication lag
  - Disk space usage

Infrastructure:
  - Node CPU/memory/disk
  - Network bandwidth (in/out)
  - Pod restart count
  - Deployment health (ready vs. total replicas)

Collection:
  - Prometheus scrape interval: 15 seconds
  - Retention: 15 days (configurable)
  - Aggregation: 1-hour buckets for long-term storage
```

### Alerting

```
Alert Thresholds (Pagerduty integration):

CRITICAL (Immediate paging):
  - P95 matching latency > 300ms for 5 minutes
  - Matching engine success rate < 80%
  - Database down or unreachable
  - 50%+ pod replicas unhealthy
  - Error rate > 5% for 5 minutes

WARNING (Ticket created, no page):
  - P95 latency > 200ms for 10 minutes
  - CPU utilization > 80% for 15 minutes
  - Memory utilization > 85% for 15 minutes
  - Disk utilization > 90%
  - Replication lag > 30 seconds
  - Cache hit rate < 75%

Alert Routing:
  - Matching engine alerts → On-call matching engineer
  - Database alerts → Database team
  - Infrastructure alerts → Platform team
  - Payment alerts → Finance team + backend
```

### Tracing

```
Distributed Tracing: Jaeger/Zipkin

Trace Headers:
  X-Trace-ID: Unique ID for entire request flow
  X-Span-ID: ID for individual operation
  X-Parent-SPAN-ID: Parent operation (for nesting)

Example trace:
  POST /rides
    → Validate rider (50ms)
    → Call ETA service (30ms)
    → Create ride record (10ms)
    → Publish event (5ms)
    → Return response (5ms)
  Total: ~100ms

Sampling:
  10% of requests (1 in 10) sampled for tracing
  All errors sampled regardless of rate
  Manual trace request: X-Force-Trace: true header
```

---

## 7. Compliance & Legal

### Data Residency

```
Data Storage:
  US region: rider/driver profiles, ride history
  EU region: EU riders' data (GDPR compliance)
  No cross-region transfer without user consent

Data Retention:
  Active rides: Kept for 30 days (operational)
  Ride history: 3 years (legal requirement, dispute resolution)
  Deleted user data: 90-day grace period, then purge
  Audit logs: 7 years (financial, legal)
```

### GDPR Compliance

```
Right to Erasure:
  - User can request data deletion
  - 30-day grace period for final calculations
  - After: Remove from hot storage, anonymize in backups

Right to Access:
  - Provide downloadable copy of user data (JSON format)
  - Include ride history, ratings, support tickets
  - Delivery: Within 30 days of request

Data Processing Agreement (DPA):
  - Third-party services (Stripe, Google Maps): Have DPAs in place
  - Subprocessor list: Maintained and shared with users
  - Regular audits: Annual DPA compliance reviews

Consent Management:
  - Explicit consent for data processing
  - Granular: Location tracking, analytics, marketing emails
  - Withdrawal: One-click opt-out

Privacy Policy:
  - Plain language explanation
  - Review and update: Annually or after changes
  - Notify users: 30 days before policy changes
```

---

## Summary: Non-Functional Requirements Trade-offs

| Requirement | Target | Trade-off |
|-------------|--------|-----------|
| **Latency (P95 < 200ms)** | Matching must be fast | More hardware; complex optimization |
| **99.9% Uptime** | High reliability | Extra redundancy; operational overhead |
| **Horizontal scaling** | Handle 100k+ drivers | Distributed complexity; debugging harder |
| **Encryption at rest** | Data security | Performance overhead (~5%); key management |
| **ACID transactions** | Data consistency | Lower throughput; potential bottlenecks |
| **7-year audit logs** | Compliance | Storage cost; querying large datasets slow |
| **Real-time WebSockets** | User experience | Stateful services; harder to scale |

---

## Performance Testing Checklist

- [ ] Load test: 10k concurrent ride requests
- [ ] Location update throughput: 100k updates/sec
- [ ] Matching latency: P95 < 200ms
- [ ] WebSocket connection: 100k concurrent clients
- [ ] Database sharding: 8 shards × 6k writes/sec
- [ ] Cache hit rates: > 80% (driver details, routes)
- [ ] Failover: Automatic within 30 seconds
- [ ] Backup recovery: RTO < 15 minutes
- [ ] Network bandwidth: < 50% utilization

---

**END OF ARCHITECTURE DOCUMENTATION**

---

## Quick Reference: System Limits

```
Concurrent Users:
  Riders: 100,000+ active riders
  Drivers: 100,000+ online drivers
  WebSocket connections: 20,000+ (10k rides × 2 users)

Throughput:
  Ride requests: 1,000 req/sec sustained (10,000 peak)
  Location updates: 100,000 updates/sec
  Matches: 1,000 matches/sec

Storage:
  Ride records: 10 billion records (3-year retention)
  Event log: 100+ million events/month
  Database size: ~500 GB hot, ~5 TB cold

Latency Budget (Matching):
  Total: < 200ms (P95)
  Spatial discovery: 10ms
  Driver enrichment: 30ms
  ETA calculation: 50ms
  Ranking: 5ms
  Assignment: 5ms
  Event publishing: 1ms

Infrastructure:
  Kubernetes nodes: 20-50 (varies with load)
  Database shards: 8 (ride table)
  Redis nodes: 16 (Geo + cache)
  Kafka brokers: 5-10
  Matching engine instances: 7-20
```

This completes the full system architecture design.
