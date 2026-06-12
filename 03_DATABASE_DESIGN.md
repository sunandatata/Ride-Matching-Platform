# Database Design

This document specifies the complete database schema, indexing strategy, and partitioning approach for PostgreSQL.

---

## Core Tables

### 1. users (Riders)

```sql
CREATE TABLE users (
  user_id UUID PRIMARY KEY,
  phone_number VARCHAR(20) NOT NULL UNIQUE,
  email VARCHAR(255) UNIQUE,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  profile_photo_url TEXT,
  date_of_birth DATE NOT NULL,  -- For age verification
  status ENUM ('ACTIVE', 'SUSPENDED', 'DELETED') NOT NULL DEFAULT 'ACTIVE',

  -- Rating and statistics
  average_rating NUMERIC(3, 2) DEFAULT 0.00,  -- 0.00 - 5.00
  total_rides INTEGER DEFAULT 0,
  total_spent NUMERIC(10, 2) DEFAULT 0.00,

  -- Account details
  kyc_verified BOOLEAN DEFAULT FALSE,
  kyc_verified_at TIMESTAMP,

  -- Emergency contact
  emergency_contact_name VARCHAR(100),
  emergency_contact_phone VARCHAR(20),

  -- Preferences
  preferred_route_language VARCHAR(10),
  sms_notifications_enabled BOOLEAN DEFAULT TRUE,
  email_notifications_enabled BOOLEAN DEFAULT TRUE,

  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMP
);

CREATE INDEX idx_users_phone ON users(phone_number) WHERE status = 'ACTIVE';
CREATE INDEX idx_users_email ON users(email) WHERE status = 'ACTIVE';
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_created_at ON users(created_at DESC);
```

---

### 2. drivers

```sql
CREATE TABLE drivers (
  driver_id UUID PRIMARY KEY,
  phone_number VARCHAR(20) NOT NULL UNIQUE,
  email VARCHAR(255) UNIQUE,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  profile_photo_url TEXT,
  date_of_birth DATE NOT NULL,
  status ENUM ('ACTIVE', 'SUSPENDED', 'DELETED') NOT NULL DEFAULT 'ACTIVE',

  -- License information
  license_number VARCHAR(50) NOT NULL UNIQUE,
  license_state VARCHAR(50),
  license_expiry_date DATE NOT NULL,
  license_verified BOOLEAN DEFAULT FALSE,
  license_verified_at TIMESTAMP,

  -- Background check
  background_check_status ENUM ('PENDING', 'APPROVED', 'FAILED', 'EXPIRED') DEFAULT 'PENDING',
  background_check_date DATE,
  background_check_expires_date DATE,

  -- Vehicle information (most recent)
  vehicle_id UUID,
  vehicle_make VARCHAR(100),
  vehicle_model VARCHAR(100),
  vehicle_year INTEGER,
  vehicle_color VARCHAR(50),
  vehicle_license_plate VARCHAR(50) UNIQUE,
  vehicle_capacity INTEGER DEFAULT 4,  -- Number of passengers
  vehicle_type ENUM ('ECONOMY', 'PREMIUM', 'SHARED') DEFAULT 'ECONOMY',
  vehicle_inspection_status ENUM ('PENDING', 'APPROVED', 'FAILED', 'EXPIRED') DEFAULT 'PENDING',
  vehicle_inspection_date DATE,

  -- Current status
  availability_status ENUM ('ONLINE', 'OFFLINE', 'ON_RIDE', 'BREAK') DEFAULT 'OFFLINE',
  last_activity_at TIMESTAMP,

  -- Rating and statistics
  average_rating NUMERIC(3, 2) DEFAULT 0.00,
  total_rides INTEGER DEFAULT 0,
  total_earnings NUMERIC(10, 2) DEFAULT 0.00,
  acceptance_rate NUMERIC(5, 2) DEFAULT 100.00,  -- Percentage: 0-100
  cancellation_rate NUMERIC(5, 2) DEFAULT 0.00,

  -- Bank details (tokenized, PCI compliance)
  payment_account_token VARCHAR(255),  -- Tokenized for security

  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMP
);

CREATE INDEX idx_drivers_phone ON drivers(phone_number) WHERE status = 'ACTIVE';
CREATE INDEX idx_drivers_email ON drivers(email) WHERE status = 'ACTIVE';
CREATE INDEX idx_drivers_status ON drivers(status);
CREATE INDEX idx_drivers_availability ON drivers(availability_status) WHERE status = 'ACTIVE';
CREATE INDEX idx_drivers_license ON drivers(license_number);
CREATE INDEX idx_drivers_vehicle_plate ON drivers(vehicle_license_plate);
CREATE INDEX idx_drivers_created_at ON drivers(created_at DESC);
CREATE INDEX idx_drivers_acceptance_rate ON drivers(acceptance_rate DESC) WHERE status = 'ACTIVE';
```

---

### 3. rides

```sql
CREATE TABLE rides (
  ride_id UUID PRIMARY KEY,
  rider_id UUID NOT NULL REFERENCES users(user_id),
  driver_id UUID REFERENCES drivers(driver_id),

  -- Location information
  pickup_latitude NUMERIC(10, 8) NOT NULL,
  pickup_longitude NUMERIC(11, 8) NOT NULL,
  pickup_address TEXT,

  dropoff_latitude NUMERIC(10, 8) NOT NULL,
  dropoff_longitude NUMERIC(11, 8) NOT NULL,
  dropoff_address TEXT,

  -- Status and timeline
  status ENUM (
    'REQUESTED',           -- Initial state
    'MATCHED',             -- Driver assigned
    'ACCEPTED',            -- Driver confirmed
    'ARRIVED',             -- Driver arrived at pickup
    'STARTED',             -- Rider boarded
    'COMPLETED',           -- Arrived at dropoff
    'CANCELLED',           -- Cancelled before completion
    'NO_SHOW'              -- Rider didn't show up
  ) NOT NULL DEFAULT 'REQUESTED',

  requested_at TIMESTAMP NOT NULL DEFAULT NOW(),
  matched_at TIMESTAMP,
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  cancelled_at TIMESTAMP,
  expires_at TIMESTAMP,  -- Match expires if not accepted (30 seconds)

  -- Fare information
  estimated_fare NUMERIC(8, 2) NOT NULL,
  estimated_duration_seconds INTEGER,
  estimated_distance_meters INTEGER,

  final_fare NUMERIC(8, 2),
  actual_duration_seconds INTEGER,
  actual_distance_meters INTEGER,

  -- Pricing breakdown
  base_fare NUMERIC(8, 2),
  distance_fare NUMERIC(8, 2),
  time_fare NUMERIC(8, 2),
  surge_multiplier NUMERIC(4, 2) DEFAULT 1.00,
  discount_amount NUMERIC(8, 2) DEFAULT 0.00,
  service_fee NUMERIC(8, 2) DEFAULT 0.00,

  -- Trip details
  passenger_count INTEGER DEFAULT 1,
  service_type ENUM ('ECONOMY', 'PREMIUM', 'SHARED') DEFAULT 'ECONOMY',

  -- Ratings
  rider_rating INTEGER,  -- 1-5 stars
  rider_feedback TEXT,
  driver_rating INTEGER,  -- 1-5 stars
  driver_feedback TEXT,

  -- Cancellation reason
  cancellation_reason VARCHAR(255),
  cancelled_by ENUM ('RIDER', 'DRIVER', 'SYSTEM'),

  -- Payment
  payment_method_id UUID,
  payment_status ENUM ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED') DEFAULT 'PENDING',
  payment_processed_at TIMESTAMP,

  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rides_rider_id ON rides(rider_id, created_at DESC);
CREATE INDEX idx_rides_driver_id ON rides(driver_id, created_at DESC);
CREATE INDEX idx_rides_status ON rides(status);
CREATE INDEX idx_rides_requested_at ON rides(requested_at DESC);
CREATE INDEX idx_rides_completed_at ON rides(completed_at DESC) WHERE status = 'COMPLETED';
CREATE INDEX idx_rides_payment_status ON rides(payment_status) WHERE status = 'COMPLETED';
CREATE INDEX idx_rides_expires_at ON rides(expires_at) WHERE status = 'MATCHED';

-- Spatial index for dropoff location queries (analytics)
CREATE INDEX idx_rides_dropoff_geo ON rides USING GIST (
  ST_SetSRID(ST_MakePoint(dropoff_longitude, dropoff_latitude), 4326)
);
```

---

### 4. driver_locations (Current State)

```sql
-- Real-time driver location state
-- Note: Redis Geo is the primary store; this table is for audit/historical queries

CREATE TABLE driver_locations (
  location_id BIGSERIAL PRIMARY KEY,
  driver_id UUID NOT NULL REFERENCES drivers(driver_id),
  latitude NUMERIC(10, 8) NOT NULL,
  longitude NUMERIC(11, 8) NOT NULL,
  heading INTEGER,  -- 0-360 degrees
  speed_mph INTEGER,
  accuracy_meters INTEGER,

  recorded_at TIMESTAMP NOT NULL DEFAULT NOW(),

  CONSTRAINT one_latest_per_driver UNIQUE (driver_id)  -- Only latest row per driver
);

CREATE INDEX idx_driver_locations_driver_id ON driver_locations(driver_id);
CREATE INDEX idx_driver_locations_recorded_at ON driver_locations(recorded_at DESC);

-- Spatial index for geographic queries
CREATE INDEX idx_driver_locations_geo ON driver_locations USING GIST (
  ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
);
```

**Rationale for Location Storage**:
- **Redis Geo**: Real-time location queries (matching engine)
- **PostgreSQL**: Historical location data, audit trail, and analytics
- **Partitioning**: Partition by month (driver_locations_2026_06, _2026_07, etc.)

---

### 5. payment_methods

```sql
CREATE TABLE payment_methods (
  payment_method_id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(user_id),
  type ENUM ('CREDIT_CARD', 'DEBIT_CARD', 'WALLET', 'UPI') NOT NULL,

  -- PCI-compliant: Store only tokenized payment info
  token VARCHAR(255) NOT NULL,  -- Token from payment processor (Stripe, etc.)
  last_four VARCHAR(4),  -- Last 4 digits for display

  -- Card metadata (non-sensitive)
  card_holder_name VARCHAR(255),
  expiry_month INTEGER,
  expiry_year INTEGER,

  is_primary BOOLEAN DEFAULT FALSE,
  is_active BOOLEAN DEFAULT TRUE,

  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_methods_user_id ON payment_methods(user_id);
CREATE INDEX idx_payment_methods_is_primary ON payment_methods(user_id, is_primary) WHERE is_active = TRUE;
```

---

### 6. promotions & referrals

```sql
CREATE TABLE promotions (
  promotion_id UUID PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  description TEXT,
  discount_type ENUM ('PERCENTAGE', 'FIXED_AMOUNT') NOT NULL,
  discount_value NUMERIC(8, 2) NOT NULL,

  min_ride_fare NUMERIC(8, 2),  -- Minimum fare to apply promo
  max_discount NUMERIC(8, 2),   -- Cap on discount amount

  max_uses INTEGER,
  current_uses INTEGER DEFAULT 0,

  start_date TIMESTAMP NOT NULL,
  end_date TIMESTAMP NOT NULL,

  is_active BOOLEAN DEFAULT TRUE,

  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE referral_bonuses (
  referral_id UUID PRIMARY KEY,
  referrer_user_id UUID NOT NULL REFERENCES users(user_id),
  referred_user_id UUID NOT NULL REFERENCES users(user_id),

  referrer_bonus NUMERIC(8, 2),
  referred_bonus NUMERIC(8, 2),

  referred_user_first_ride_id UUID REFERENCES rides(ride_id),
  bonus_status ENUM ('PENDING', 'COMPLETED', 'FAILED') DEFAULT 'PENDING',

  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE(referrer_user_id, referred_user_id)
);

CREATE INDEX idx_referral_bonuses_referrer ON referral_bonuses(referrer_user_id);
CREATE INDEX idx_referral_bonuses_referred ON referral_bonuses(referred_user_id);
```

---

### 7. ride_events (Event Sourcing)

```sql
-- Append-only log for complete ride history and audit trail
CREATE TABLE ride_events (
  event_id BIGSERIAL PRIMARY KEY,
  ride_id UUID NOT NULL REFERENCES rides(ride_id),
  event_type VARCHAR(100) NOT NULL,  -- 'ride.requested', 'ride.matched', etc.

  event_data JSONB NOT NULL,  -- Full event payload

  source VARCHAR(50),  -- 'api', 'driver_app', 'system'
  user_id UUID,  -- Who triggered the event

  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ride_events_ride_id ON ride_events(ride_id);
CREATE INDEX idx_ride_events_type ON ride_events(event_type);
CREATE INDEX idx_ride_events_created_at ON ride_events(created_at DESC);

-- Partition by date for efficient archival
-- ride_events_2026_06_01, ride_events_2026_06_02, etc.
```

**Purpose**: Complete audit trail for:
- Dispute resolution (rider/driver disagreements)
- Regulatory compliance (trip history)
- Debugging matching failures
- Analytics

---

### 8. support_tickets

```sql
CREATE TABLE support_tickets (
  ticket_id UUID PRIMARY KEY,
  ride_id UUID REFERENCES rides(ride_id),
  user_id UUID NOT NULL REFERENCES users(user_id),

  issue_type VARCHAR(100) NOT NULL,  -- 'payment_issue', 'safety_concern', etc.
  description TEXT NOT NULL,

  status ENUM ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED') DEFAULT 'OPEN',
  priority ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') DEFAULT 'MEDIUM',

  assigned_to_agent_id UUID,  -- Support staff member

  resolution_notes TEXT,
  refund_amount NUMERIC(8, 2),

  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  resolved_at TIMESTAMP
);

CREATE INDEX idx_support_tickets_user_id ON support_tickets(user_id, created_at DESC);
CREATE INDEX idx_support_tickets_status ON support_tickets(status);
CREATE INDEX idx_support_tickets_priority ON support_tickets(priority, status) WHERE status != 'CLOSED';
```

---

## Indexing Strategy

### Query Patterns & Indexes

```
1. Rider: Get all rides (paginated)
   Query: SELECT * FROM rides WHERE rider_id = ? ORDER BY created_at DESC LIMIT 20
   Index: idx_rides_rider_id

2. Driver: Get completed rides (for earnings calculation)
   Query: SELECT * FROM rides WHERE driver_id = ? AND status = 'COMPLETED' ORDER BY completed_at DESC
   Index: idx_rides_driver_id

3. Matching: Get all online drivers
   Query: SELECT * FROM drivers WHERE availability_status = 'ONLINE' AND status = 'ACTIVE'
   Index: idx_drivers_availability

4. Ride Status: Get active rides for a driver
   Query: SELECT * FROM rides WHERE driver_id = ? AND status IN ('MATCHED', 'STARTED')
   Index: idx_rides_driver_id, idx_rides_status

5. Analytics: Rides completed in a date range
   Query: SELECT * FROM rides WHERE completed_at BETWEEN ? AND ? AND status = 'COMPLETED'
   Index: idx_rides_completed_at

6. Support: Get unresolved tickets
   Query: SELECT * FROM support_tickets WHERE status IN ('OPEN', 'IN_PROGRESS') ORDER BY priority DESC
   Index: idx_support_tickets_status, idx_support_tickets_priority
```

### Composite Indexes

```sql
-- For efficient filtering and sorting
CREATE INDEX idx_rides_status_created ON rides(status, created_at DESC);
CREATE INDEX idx_drivers_status_rating ON drivers(status, average_rating DESC) WHERE availability_status = 'ONLINE';
```

### Partial Indexes

```sql
-- Only index active users (saves space, improves write performance)
CREATE INDEX idx_active_drivers ON drivers(driver_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_online_drivers ON drivers(average_rating DESC) WHERE status = 'ACTIVE' AND availability_status = 'ONLINE';
```

---

## Scaling Strategy: Horizontal Partitioning

### Problem
At 100k+ drivers and 10k+ concurrent rides:
- Single PostgreSQL instance becomes CPU/IO bottleneck
- Large table scans slow down queries
- Write throughput limited

### Solution: Sharding by ride_id (Range Partitioning)

**Sharding Key**: ride_id hash modulo N (e.g., N=8 shards)

```
Shard 0: rides with ride_id hash % 8 == 0
Shard 1: rides with ride_id hash % 8 == 1
...
Shard 7: rides with ride_id hash % 8 == 7

Distribution:
- Each shard on separate PostgreSQL instance
- App layer determines shard via: shard_id = hash(ride_id) % 8
- Total writes: distributed across 8 instances (8x throughput)
```

**Ride Shard Distribution**:
```
If 10k concurrent rides, average ~1,250 per shard.
Sequential scan of rides table = scan all 10k rows (distributed across shards).
Per-shard scan = scan ~1,250 rows (much faster).
```

### Driver & User Sharding

**Option A: Centralized (Recommended for initial scale)**
- Keep drivers and users in central PostgreSQL (read replicas for scaling reads)
- Shard only rides table
- Justification: Drivers/users grow slowly; Matching queries don't require coordination

**Option B: Shard by driver_id (if driver queries dominate)**
```
Shard Key: driver_id % N
Benefits: Distribute driver stats, vehicle info, location history
Drawback: Matching queries (find drivers in radius) require scatter-gather across all shards
```

### Recommended Approach (Hybrid)

```
Users & Drivers:
  - PostgreSQL Primary (Write all user/driver changes)
  - 3x Read Replicas (Distribute reads for profile lookups)
  - Redis Cache on top (Frequently accessed driver ratings, stats)

Rides:
  - Shard 0-7 (8 shards, range-partitioned by ride_id hash)
  - Each shard: Primary + 1 Replica
  - Writes distributed across shards
  - Reads hit replica when possible

Locations:
  - Redis Geo Cluster (primary store, distributed by driver_id)
  - PostgreSQL (historical archive, partitioned by date)

Audit Log (ride_events):
  - Partitioned by date in central PostgreSQL
  - Archival: move old partitions to cold storage (S3) after 6 months
```

---

## Partition Strategy for ride_events

```sql
-- Partition by date for efficient time-range queries and archival

CREATE TABLE ride_events_2026_06_01 PARTITION OF ride_events
  FOR VALUES FROM ('2026-06-01') TO ('2026-06-02');

CREATE TABLE ride_events_2026_06_02 PARTITION OF ride_events
  FOR VALUES FROM ('2026-06-02') TO ('2026-06-03');

-- Retention policy:
--   - Recent (< 1 month): In PostgreSQL for fast queries
--   - Archive (> 1 month): Move to S3 for long-term storage
--   - Delete (> 7 years): Comply with data retention regulations
```

---

## Caching Strategy

### Read Cache (Redis)

```
Key Pattern: cache:{entity}:{id}

Examples:
  cache:driver:D123
  → Value: {driver_id, avg_rating, acceptance_rate, vehicle_info}
  → TTL: 5 minutes (expires after driver profile update)

  cache:user:U456
  → Value: {user_id, name, phone, email}
  → TTL: 1 hour

Cache Invalidation:
  - On write: DELETE cache:{entity}:{id}
  - Lazy: On read miss, fetch from DB and populate cache
  - Event-driven: Kafka event for updates, cache service listens and invalidates
```

### Location Cache (Redis Geo)

```
Redis Geo Index:
  Key: driver_locations
  Members: driver_id with (latitude, longitude)

Operations:
  GEOADD driver_locations 40.7128 -74.0060 D123
  GEORADIUS driver_locations 40.7128 -74.0060 10 km

Durability:
  - Redis Cluster with replication
  - Each location update written to append-only file (AOF)
  - Recovery: Rebuild from driver_locations table on startup
```

---

## Query Performance Examples

### Example 1: Find Nearby Drivers (Matching Engine)

```sql
-- Slow: Full table scan
SELECT * FROM drivers
WHERE ST_Distance(
  ST_SetSRID(ST_MakePoint(longitude, latitude), 4326),
  ST_SetSRID(ST_MakePoint(-74.0060, 40.7128), 4326)
) < 10000  -- 10km
AND availability_status = 'ONLINE';

-- Fast: Redis Geo query (used in practice)
GEORADIUS driver_locations 40.7128 -74.0060 10 km
  → Returns [D123, D456, D789, ...]
  → Latency: <10ms
```

### Example 2: Get Driver's Earnings

```sql
-- Query:
SELECT SUM(final_fare) as total_earnings
FROM rides
WHERE driver_id = 'D123'
  AND status = 'COMPLETED'
  AND completed_at >= NOW() - INTERVAL '1 month';

Index: idx_rides_driver_id (driver_id, completed_at DESC)
Execution: Index scan → ~1,000 rows (1 month of rides)
Latency: <50ms
Cache: Refresh hourly via background job
```

### Example 3: Matching Latency Profile

```
Scenario: 10,000 concurrent ride requests, 100k online drivers

Bottleneck 1: Find nearby drivers
  - Redis Geo: 50 drivers in 5km radius
  - Latency: <10ms

Bottleneck 2: Fetch driver details
  - Serial: 50 × 20ms HTTP calls = 1000ms ❌
  - Parallel (thread pool): 50 / 10 = 5 batches × 20ms = 100ms ✓
  - Batched RPC: Single call = 20ms ✓

Bottleneck 3: ETA calculation
  - Cache hit rate: 80%
    - 40 drivers with cached routes: <5ms
    - 10 drivers requiring API call: ~50ms
    - Total: <50ms

Bottleneck 4: Ranking & assignment
  - In-memory sort of 50 drivers: <5ms

Total: 10 + 50 + 50 + 5 = 115ms (within 200ms target)
```

---

## Data Consistency & ACID Guarantees

### Transactions (Ride Creation → Assignment)

```sql
BEGIN TRANSACTION (SERIALIZABLE isolation);

1. CREATE ride record
   INSERT INTO rides (ride_id, rider_id, status, ...)
   VALUES (R123, U789, 'REQUESTED', ...);

2. Publish event (transactional outbox pattern)
   INSERT INTO ride_events (ride_id, event_type, event_data)
   VALUES (R123, 'ride.requested', '...');

3. Commit
   COMMIT;
   → Event processing job picks up from ride_events table
   → Publishes to Kafka asynchronously

Benefit: No lost events; at-least-once delivery guarantee
```

### Race Conditions: Preventing Double Assignment

```sql
-- Ride assignment (handled by Matching Engine)
UPDATE rides
SET driver_id = 'D123', status = 'MATCHED', matched_at = NOW()
WHERE ride_id = 'R123' AND status = 'REQUESTED';

If no rows updated:
  → Another request already assigned this ride
  → Matching Engine returns 409 Conflict
  → Client triggers new ride request (backoff + retry)
```

---

## Backup & Disaster Recovery

### Backup Strategy

```
PostgreSQL:
  - Daily full backups to S3
  - Continuous WAL (Write-Ahead Log) archival to S3
  - Point-in-time recovery: restore to any moment in last 30 days

Redis:
  - RDB snapshots: every 1 hour to S3
  - AOF (append-only file) for crash recovery
  - Cluster replication: 3 replicas per shard

Recovery Time Objective (RTO): 15 minutes
Recovery Point Objective (RPO): 5 minutes
```

---

## Summary: Database Design Decisions

| Decision | Rationale | Trade-off |
|----------|-----------|-----------|
| **PostgreSQL Primary Store** | ACID guarantees; spatial index support; complex queries | Not a NoSQL database (eventual consistency) |
| **Redis Geo for Locations** | Sub-millisecond queries for matching | Extra infrastructure; cache invalidation |
| **Ride Sharding (8 shards)** | Horizontal scaling; 8x write throughput | App complexity; distributed transactions |
| **Event Sourcing (ride_events)** | Complete audit trail; debugging; replay capability | Extra storage; eventual consistency for analytics |
| **Partial Indexes** | Save space; improve write performance | More complex index management |
| **Transactional Outbox** | No lost events; at-least-once delivery | Extra table; polling overhead |

---

**Next**: API Contract Design, Matching Engine Details, Real-Time Communication
