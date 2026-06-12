-- Add additional indexes for performance metrics and driver eligibility checks
CREATE INDEX idx_drivers_eligible_check ON drivers(
    status,
    license_verified,
    license_expiry_date,
    background_check_status,
    vehicle_inspection_status,
    availability_status
) WHERE status = 'ACTIVE';

-- Index for batch driver lookups
CREATE INDEX idx_drivers_id_status ON drivers(driver_id, status);

-- Index for metrics queries
CREATE INDEX idx_drivers_metrics ON drivers(
    average_rating DESC,
    acceptance_rate DESC,
    cancellation_rate ASC
) WHERE status = 'ACTIVE';

-- Index for last activity tracking
CREATE INDEX idx_drivers_last_activity ON drivers(last_activity_at DESC) WHERE status = 'ACTIVE';

-- Index for earnings queries
CREATE INDEX idx_drivers_total_earnings ON drivers(total_earnings DESC) WHERE status = 'ACTIVE';
