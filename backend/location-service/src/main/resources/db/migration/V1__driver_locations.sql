-- Location Service Schema
-- Audit trail for driver location history
-- All writes are batched (5 minute intervals)

CREATE TABLE IF NOT EXISTS driver_locations (
    id BIGSERIAL PRIMARY KEY,
    driver_id VARCHAR(64) NOT NULL,
    latitude NUMERIC(10, 8) NOT NULL,
    longitude NUMERIC(11, 8) NOT NULL,
    heading INTEGER CHECK (heading >= 0 AND heading <= 360),
    speed DOUBLE PRECISION,
    accuracy DOUBLE PRECISION,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(20) DEFAULT 'gps'
);

-- Indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_driver_id_timestamp ON driver_locations(driver_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_timestamp ON driver_locations(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_driver_id ON driver_locations(driver_id);

-- Partitioning by month for large-scale operations (optional, for future optimization)
-- CREATE TABLE driver_locations_2024_06 PARTITION OF driver_locations
--     FOR VALUES FROM ('2024-06-01') TO ('2024-07-01');

-- Driver status table for online/offline tracking
CREATE TABLE IF NOT EXISTS driver_status (
    driver_id VARCHAR(64) PRIMARY KEY,
    is_online BOOLEAN NOT NULL DEFAULT false,
    last_location_update TIMESTAMP,
    last_heartbeat TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for status queries
CREATE INDEX IF NOT EXISTS idx_driver_status_online ON driver_status(is_online) WHERE is_online = true;

-- Comment
COMMENT ON TABLE driver_locations IS 'Audit trail of driver location history. Batched writes every 5 minutes.';
COMMENT ON TABLE driver_status IS 'Current online/offline status of drivers. Fast lookup cache for status.';
COMMENT ON COLUMN driver_locations.timestamp IS 'Event timestamp from driver device (may be stale).';
COMMENT ON COLUMN driver_locations.created_at IS 'Server timestamp when location was persisted.';
COMMENT ON COLUMN driver_locations.source IS 'GPS source: gps (device GPS), network (IP-based), or fused (combined).';
