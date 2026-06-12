-- Create users table
CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    user_type VARCHAR(50) NOT NULL CHECK (user_type IN ('RIDER', 'DRIVER', 'ADMIN', 'SUPPORT')),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED')),
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_phone_number VARCHAR(20),
    kyc_verified BOOLEAN NOT NULL DEFAULT FALSE,
    kyc_verified_at TIMESTAMP WITH TIME ZONE,
    profile_photo_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT user_phone_unique UNIQUE (phone_number),
    CONSTRAINT user_email_unique UNIQUE (email)
);

-- Create indices for frequently queried columns
CREATE INDEX idx_users_phone_number ON users(phone_number);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_user_type ON users(user_type);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_created_at ON users(created_at DESC);

-- Create refresh_tokens table
CREATE TABLE refresh_tokens (
    token_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash VARCHAR(256) NOT NULL UNIQUE,
    device_id VARCHAR(255) NOT NULL,
    device_type VARCHAR(50) NOT NULL CHECK (device_type IN ('ios', 'android', 'web')),
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT refresh_token_expiry_check CHECK (expires_at > issued_at)
);

-- Create indices for token lookup and cleanup
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_refresh_tokens_active ON refresh_tokens(user_id, revoked, expires_at)
    WHERE revoked = FALSE;

-- Create token_blacklist table
CREATE TABLE token_blacklist (
    id UUID PRIMARY KEY,
    token_jti VARCHAR(36) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason VARCHAR(100) NOT NULL,
    blacklisted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT blacklist_jti_unique UNIQUE (token_jti)
);

-- Create indices for blacklist lookups
CREATE INDEX idx_token_blacklist_jti ON token_blacklist(token_jti);
CREATE INDEX idx_token_blacklist_expires_at ON token_blacklist(expires_at);
CREATE INDEX idx_token_blacklist_user_id ON token_blacklist(user_id);

-- Create view for active refresh tokens (useful for queries)
CREATE VIEW active_refresh_tokens AS
SELECT *
FROM refresh_tokens
WHERE revoked = FALSE
  AND expires_at > CURRENT_TIMESTAMP;

-- Create comments for documentation
COMMENT ON TABLE users IS 'Stores user accounts for riders, drivers, admins, and support staff';
COMMENT ON TABLE refresh_tokens IS 'Manages refresh token lifecycle and device tracking';
COMMENT ON TABLE token_blacklist IS 'Maintains revoked JWT tokens for logout and security';

-- Create audit log table for security events
CREATE TABLE auth_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(user_id) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_details JSONB,
    ip_address VARCHAR(45),
    device_info VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' CHECK (status IN ('SUCCESS', 'FAILURE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indices for audit log
CREATE INDEX idx_auth_audit_user_id ON auth_audit_log(user_id);
CREATE INDEX idx_auth_audit_event_type ON auth_audit_log(event_type);
CREATE INDEX idx_auth_audit_created_at ON auth_audit_log(created_at DESC);
CREATE INDEX idx_auth_audit_status ON auth_audit_log(status);

-- Create table for login attempts (for rate limiting)
CREATE TABLE login_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 1,
    last_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until TIMESTAMP WITH TIME ZONE,

    CONSTRAINT login_attempts_phone_unique UNIQUE (phone_number)
);

CREATE INDEX idx_login_attempts_phone_number ON login_attempts(phone_number);
CREATE INDEX idx_login_attempts_locked_until ON login_attempts(locked_until);
