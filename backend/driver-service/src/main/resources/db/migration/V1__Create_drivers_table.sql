-- Create drivers table for driver profile management
CREATE TABLE IF NOT EXISTS drivers (
    driver_id UUID PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    email VARCHAR(255) UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    profile_photo_url VARCHAR(500),
    date_of_birth DATE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    -- License information
    license_number VARCHAR(50) NOT NULL UNIQUE,
    license_state VARCHAR(50),
    license_expiry_date DATE NOT NULL,
    license_verified BOOLEAN NOT NULL DEFAULT FALSE,
    license_verified_at TIMESTAMP,

    -- Background check
    background_check_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    background_check_date DATE,
    background_check_expires_date DATE,

    -- Vehicle information
    vehicle_id UUID,
    vehicle_make VARCHAR(100),
    vehicle_model VARCHAR(100),
    vehicle_year INTEGER,
    vehicle_color VARCHAR(50),
    vehicle_license_plate VARCHAR(50) UNIQUE,
    vehicle_capacity INTEGER DEFAULT 4,
    vehicle_type VARCHAR(50) DEFAULT 'ECONOMY',
    vehicle_inspection_status VARCHAR(50) DEFAULT 'PENDING',
    vehicle_inspection_date DATE,

    -- Availability status
    availability_status VARCHAR(50) NOT NULL DEFAULT 'OFFLINE',
    last_activity_at TIMESTAMP,

    -- Rating and performance metrics
    average_rating NUMERIC(3, 2) DEFAULT 0.00,
    total_rides INTEGER DEFAULT 0,
    total_earnings NUMERIC(10, 2) DEFAULT 0.00,
    acceptance_rate NUMERIC(5, 2) DEFAULT 100.00,
    cancellation_rate NUMERIC(5, 2) DEFAULT 0.00,

    -- Payment account
    payment_account_token VARCHAR(255),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

-- Create indexes for efficient queries
CREATE INDEX idx_drivers_phone ON drivers(phone_number) WHERE status = 'ACTIVE';
CREATE INDEX idx_drivers_email ON drivers(email) WHERE status = 'ACTIVE';
CREATE INDEX idx_drivers_status ON drivers(status);
CREATE INDEX idx_drivers_availability ON drivers(availability_status) WHERE status = 'ACTIVE';
CREATE INDEX idx_drivers_license ON drivers(license_number);
CREATE INDEX idx_drivers_vehicle_plate ON drivers(vehicle_license_plate);
CREATE INDEX idx_drivers_created_at ON drivers(created_at DESC);
CREATE INDEX idx_drivers_acceptance_rate ON drivers(acceptance_rate DESC) WHERE status = 'ACTIVE';
CREATE INDEX idx_drivers_average_rating ON drivers(average_rating DESC) WHERE status = 'ACTIVE';
