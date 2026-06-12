-- V2__Create_ride_events_table.sql
-- Event sourcing table for audit trail and compliance

CREATE TABLE IF NOT EXISTS ride_events (
    id BIGSERIAL PRIMARY KEY,
    ride_id VARCHAR(32) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    previous_status VARCHAR(50),
    new_status VARCHAR(50),
    initiator_id VARCHAR(255),
    initiator_type VARCHAR(50),
    event_data TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ride_id FOREIGN KEY (ride_id) REFERENCES rides(id) ON DELETE CASCADE,
    CONSTRAINT check_event_type CHECK (
        event_type IN (
            'RIDE_REQUESTED',
            'RIDE_MATCHED',
            'RIDE_ACCEPTED',
            'RIDE_ARRIVED',
            'RIDE_STARTED',
            'RIDE_COMPLETED',
            'RIDE_CANCELLED',
            'DRIVER_ASSIGNED',
            'RATING_PROVIDED',
            'PAYMENT_PROCESSED'
        )
    ),
    CONSTRAINT check_status_enum CHECK (
        previous_status IS NULL OR
        previous_status IN ('REQUESTED', 'MATCHED', 'ACCEPTED', 'ARRIVED', 'STARTED', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT check_status_enum2 CHECK (
        new_status IS NULL OR
        new_status IN ('REQUESTED', 'MATCHED', 'ACCEPTED', 'ARRIVED', 'STARTED', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT check_initiator_type CHECK (
        initiator_type IS NULL OR
        initiator_type IN ('RIDER', 'DRIVER', 'SYSTEM')
    )
);

-- Create indexes for efficient event retrieval
CREATE INDEX idx_ride_id_event ON ride_events(ride_id);
CREATE INDEX idx_event_type ON ride_events(event_type);
CREATE INDEX idx_created_at_event ON ride_events(created_at);
CREATE INDEX idx_initiator_id ON ride_events(initiator_id);
CREATE INDEX idx_ride_created ON ride_events(ride_id, created_at ASC);

-- Comment for documentation
COMMENT ON TABLE ride_events IS 'Event sourcing table - immutable audit trail of all ride state changes';
COMMENT ON COLUMN ride_events.event_type IS 'Type of event that occurred';
COMMENT ON COLUMN ride_events.previous_status IS 'Status before the state transition';
COMMENT ON COLUMN ride_events.new_status IS 'Status after the state transition';
COMMENT ON COLUMN ride_events.event_data IS 'JSON payload with additional event details';
