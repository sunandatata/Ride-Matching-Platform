# Authentication Service

Complete JWT-based authentication service for the ride-sharing platform with MFA support, token refresh, and comprehensive security features.

## Features

- **JWT Token Generation**: 1-hour access tokens with 7-day refresh tokens
- **MFA Support**: Phone-based OTP verification via SMS
- **Token Refresh**: Seamless token renewal with device tracking
- **Token Blacklist**: Revocation support for logout and security scenarios
- **Login Rate Limiting**: Protection against brute-force attacks
- **Device Management**: Multi-device token tracking per user
- **Role-Based Access**: RIDER, DRIVER, ADMIN, SUPPORT role support
- **Comprehensive Audit Logging**: Security event tracking

## Architecture

```
auth-service/
├── controller/          REST API endpoints
├── service/            Business logic (AuthService, MfaService)
├── security/           JWT token provider, password hashing
├── entity/             JPA entities (User, RefreshToken, TokenBlacklist)
├── dto/                Request/Response DTOs
├── repository/         JPA repositories
├── exception/          Custom exceptions
└── resources/          Configuration, migrations
```

## Quick Start

### Prerequisites
- Java 21+
- PostgreSQL 13+
- Redis 6+
- Maven 3.8+

### Setup

1. **Clone and build**:
```bash
cd backend/auth-service
mvn clean install
```

2. **Configure database**:
```bash
# Update application.yaml with your PostgreSQL credentials
spring.datasource.url=jdbc:postgresql://localhost:5432/auth_service
spring.datasource.username=postgres
spring.datasource.password=yourpassword
```

3. **Set JWT secret** (minimum 256 bits):
```bash
export JWT_SECRET="your-secret-key-minimum-256-bits-long"
```

4. **Run migrations**:
```bash
mvn flyway:migrate
```

5. **Start service**:
```bash
mvn spring-boot:run
```

Service runs on `http://localhost:8080`

## API Endpoints

### Login
```bash
POST /api/v1/auth/login
Content-Type: application/json

{
  "phone_number": "+1234567890",
  "password": "password123",
  "device_id": "device-uuid",
  "device_type": "ios"
}

Response:
{
  "access_token": "eyJhbGc...",
  "refresh_token": "eyJhbGc...",
  "expires_in": 3600,
  "token_type": "Bearer",
  "user": {
    "user_id": "U123",
    "first_name": "John",
    "last_name": "Doe",
    "phone_number": "+1234567890",
    "type": "rider",
    "kyc_verified": true
  },
  "mfa_required": false
}
```

### Refresh Token
```bash
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refresh_token": "eyJhbGc..."
}

Response:
{
  "access_token": "eyJhbGc...",
  "expires_in": 3600,
  "token_type": "Bearer"
}
```

### Logout
```bash
POST /api/v1/auth/logout
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "refresh_token": "eyJhbGc..."
}

Response:
{
  "message": "Logged out successfully"
}
```

### Verify MFA
```bash
POST /api/v1/auth/verify-mfa
Content-Type: application/json

{
  "user_id": "U123",
  "otp": "123456"
}

Response:
{
  "message": "MFA verification successful",
  "access_token": "eyJhbGc...",
  ...
}
```

### Validate Token
```bash
GET /api/v1/auth/validate
Authorization: Bearer {access_token}

Response:
{
  "valid": true,
  "user_id": "U123"
}
```

## Configuration

### Environment Variables
```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/auth_service
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres

# JWT
JWT_SECRET=your-minimum-256-bit-secret-key

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=optional

# MFA
TWILIO_ACCOUNT_SID=your-twilio-sid
TWILIO_AUTH_TOKEN=your-twilio-token
TWILIO_PHONE_NUMBER=+1234567890
```

### Key Properties (application.yaml)
```yaml
auth:
  jwt:
    access-token-expiry-hours: 1
    refresh-token-expiry-days: 7
    secret: ${JWT_SECRET}
    issuer: rideshare.local
    audience: rideshare-api

  mfa:
    enabled: true
    sms-provider: twilio

  login-attempt-blocking: true
  max-login-attempts: 5
  login-attempt-window-minutes: 15
```

## Database Schema

### users
- `user_id`: UUID primary key
- `phone_number`: Unique phone in E.164 format
- `password_hash`: BCrypt hashed password
- `user_type`: RIDER, DRIVER, ADMIN, SUPPORT
- `status`: ACTIVE, INACTIVE, SUSPENDED, DELETED
- `mfa_enabled`: Boolean flag for MFA activation
- `kyc_verified`: KYC verification status
- Timestamps: `created_at`, `updated_at`

### refresh_tokens
- `token_id`: UUID primary key
- `user_id`: Foreign key to users
- `token_hash`: SHA-256 hash of refresh token
- `device_id`: Device identifier for multi-device support
- `device_type`: ios, android, web
- `revoked`: Revocation status
- `expires_at`: Token expiration time

### token_blacklist
- `id`: UUID primary key
- `token_jti`: JWT ID (jti claim) - unique
- `user_id`: Foreign key to users
- `expires_at`: When blacklist entry expires
- `reason`: USER_LOGOUT, TOKEN_COMPROMISE, etc.

## Security Features

### Password Security
- **BCrypt Hashing**: Strength factor 12 (~200ms per hash)
- **No Password Storage**: Only hashes stored in database
- **Timing Attack Prevention**: Constant-time comparison for verification

### JWT Security
- **HS256 Signing**: HMAC with SHA-256
- **Token Expiration**: 1-hour access tokens, 7-day refresh tokens
- **JTI Claims**: Unique token ID for blacklist tracking
- **Audience Validation**: Token audience verified on validation

### Rate Limiting
- **Login Attempts**: Max 5 failed attempts per 15 minutes
- **Device Limiting**: Max 3 active refresh tokens per device type
- **Token Cleanup**: Automatic deletion of expired tokens daily

### MFA Implementation
- **OTP Delivery**: Via SMS (Twilio integration)
- **OTP Validation**: 6-digit codes, 5-minute expiration
- **Attempt Limiting**: Max 3 failed OTP attempts
- **Constant-Time Comparison**: Timing attack prevention

### Additional Security
- **Stateless Authentication**: No server-side session
- **CORS Configuration**: Origin validation
- **Token Revocation**: Logout blacklist support
- **Device Tracking**: Multi-device management
- **Audit Logging**: All auth events logged

## Testing

### Run Tests
```bash
# All tests
mvn test

# With coverage
mvn test jacoco:report

# Specific test class
mvn test -Dtest=AuthServiceTest

# Integration tests
mvn test -Dgroups=integration
```

### Test Coverage
- **80%+ code coverage** for business logic
- Unit tests: Service, repository, security components
- Integration tests: REST endpoints with MockMvc
- Security tests: Token validation, password hashing

### Key Test Classes
- `AuthServiceTest`: Login, refresh, logout, validation (15 tests)
- `AuthControllerTest`: REST endpoint behavior (10 tests)
- `JwtTokenProviderTest`: Token generation and validation (18 tests)
- `PasswordHasherTest`: Password hashing and verification (10 tests)

## Performance

### Benchmarks (target <100ms p99)
- **Login**: ~150ms (password hash dominates)
- **Token Refresh**: ~20ms (JWT parsing)
- **Token Validation**: ~5ms (signature check)
- **MFA Verification**: ~10ms (OTP lookup)

### Optimizations
- **Connection Pooling**: HikariCP with 10-30 pool size
- **Redis Caching**: Login attempts, OTP storage
- **Index Coverage**: Phone number, user_id, token_hash
- **Batch Processing**: Expired token cleanup nightly

## Production Deployment

### Kubernetes
```bash
# Apply manifests
kubectl apply -f auth-service.yaml

# Check status
kubectl get pods -l app=auth-service
kubectl logs -f deployment/auth-service
```

### Health Checks
```bash
# Liveness
GET /health

# Readiness
GET /health/ready

# Detailed metrics
GET /actuator/health/details
```

### Scaling
- Horizontal scaling: Stateless, no server-side state
- Database: Connection pooling managed per instance
- Redis: Shared across all instances
- Load balancing: Round-robin suitable

## Troubleshooting

### Common Issues

**"Invalid JWT signature"**
- Verify JWT_SECRET environment variable is set
- Ensure secret is same across all instances

**"User not found"**
- Check phone number format (must be E.164)
- Verify user exists and is ACTIVE

**"Token expired"**
- Call refresh endpoint with valid refresh token
- Refresh tokens valid for 7 days by default

**"MFA verification failed"**
- Check OTP hasn't expired (5-minute window)
- Verify correct OTP code
- Check max attempts not exceeded (3 attempts)

### Debug Logging
```yaml
logging:
  level:
    com.rideshare.auth: DEBUG
    org.springframework.security: DEBUG
```

## Contributing

1. Follow clean architecture principles
2. Maintain >80% test coverage
3. Add tests before features
4. Document API changes
5. Use constructor injection only

## License

Proprietary - Ride-Sharing Platform
