-- V1__Create_rides_table.sql
-- Initial schema for ride management

CREATE TABLE IF NOT EXISTS rides (
    id VARCHAR(32) PRIMARY KEY,
    rider_id VARCHAR(255) NOT NULL,
    driver_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'REQUESTED',

    -- Pickup location
    pickup_latitude DECIMAL(10, 8) NOT NULL,
    pickup_longitude DECIMAL(11, 8) NOT NULL,
    pickup_address VARCHAR(255) NOT NULL,

    -- Dropoff location
    dropoff_latitude DECIMAL(10, 8) NOT NULL,
    dropoff_longitude DECIMAL(11, 8) NOT NULL,
    dropoff_address VARCHAR(255) NOT NULL,

    -- Ride details
    passenger_count INTEGER NOT NULL,
    estimated_fare DECIMAL(10, 2),
    actual_fare DECIMAL(10, 2),
    estimated_duration_seconds INTEGER,
    actual_duration_seconds INTEGER,
    driver_eta INTEGER,

    -- State transition timestamps
    matched_at TIMESTAMP,
    accepted_at TIMESTAMP,
    arrived_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,

    -- Cancellation details
    cancellation_reason VARCHAR(500),
    cancellation_initiator VARCHAR(50),

    -- Ratings and feedback
    driver_rating INTEGER,
    rider_rating INTEGER,
    driver_feedback TEXT,
    rider_feedback TEXT,

    -- Sharding support
    shard_id INTEGER NOT NULL,

    -- Audit timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT check_passenger_count CHECK (passenger_count >= 1 AND passenger_count <= 6),
    CONSTRAINT check_ratings CHECK (driver_rating IS NULL OR (driver_rating >= 1 AND driver_rating <= 5)),
    CONSTRAINT check_ratings2 CHECK (rider_rating IS NULL OR (rider_rating >= 1 AND rider_rating <= 5)),
    CONSTRAINT check_status CHECK (status IN ('REQUESTED', 'MATCHED', 'ACCEPTED', 'ARRIVED', 'STARTED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT check_shard_id CHECK (shard_id >= 0 AND shard_id < 8)
);

-- Create indexes for efficient queries
CREATE INDEX idx_rider_id ON rides(rider_id);
CREATE INDEX idx_driver_id ON rides(driver_id);
CREATE INDEX idx_status ON rides(status);
CREATE INDEX idx_created_at ON rides(created_at);
CREATE INDEX idx_shard_id ON rides(shard_id);
CREATE INDEX idx_rider_created ON rides(rider_id, created_at DESC);
CREATE INDEX idx_driver_created ON rides(driver_id, created_at DESC);
CREATE INDEX idx_status_matched_at ON rides(status, matched_at) WHERE status = 'MATCHED';

-- Comment for documentation
COMMENT ON TABLE rides IS 'Core ride entity - tracks the lifecycle of rides from request to completion';
COMMENT ON COLUMN rides.shard_id IS 'Sharding key for horizontal scaling (0-7)';
COMMENT ON COLUMN rides.status IS 'Ride state: REQUESTED -> MATCHED -> ACCEPTED -> ARRIVED -> STARTED -> COMPLETED/CANCELLED';
