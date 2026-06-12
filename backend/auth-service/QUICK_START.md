# Authentication Service - Quick Start Guide

## 5-Minute Setup

### 1. Prerequisites
```bash
# Check Java version
java -version  # Requires Java 21+

# Check Maven
mvn -version   # Requires Maven 3.8+

# PostgreSQL and Redis running
psql --version
redis-server --version
```

### 2. Build
```bash
cd backend/auth-service
mvn clean install
```

### 3. Database Setup
```bash
# Create database
createdb auth_service

# Run migrations automatically on startup
# (Flyway will handle this)
```

### 4. Configure
Create `.env` file in project root:
```bash
JWT_SECRET=your-minimum-256-bit-secret-key-change-in-production
DATABASE_URL=jdbc:postgresql://localhost:5432/auth_service
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres
REDIS_HOST=localhost
REDIS_PORT=6379
```

### 5. Run
```bash
mvn spring-boot:run
```

Service starts on `http://localhost:8080`

## Testing the API

### Test Login
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "phone_number": "+1234567890",
    "password": "password123",
    "device_id": "device-456",
    "device_type": "ios"
  }'
```

### Test Token Refresh
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refresh_token": "YOUR_REFRESH_TOKEN"
  }'
```

### Test Token Validation
```bash
curl -X GET http://localhost:8080/api/v1/auth/validate \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

## Running Tests

### All Tests
```bash
mvn test
```

### With Coverage Report
```bash
mvn test jacoco:report
# Report: target/site/jacoco/index.html
```

### Specific Test Class
```bash
mvn test -Dtest=AuthServiceTest
mvn test -Dtest=AuthControllerTest
```

## Configuration for Different Environments

### Development (Default)
```bash
# Uses application.yaml
spring.profiles.active=
```

### Production
```bash
# Uses application-prod.yaml
spring.profiles.active=prod
```

### Custom Configuration
```bash
java -jar auth-service.jar \
  --server.port=8081 \
  --spring.datasource.url=jdbc:postgresql://prod-db:5432/auth \
  --auth.jwt.secret=your-secret-key
```

## Key Endpoints Reference

| Method | Endpoint | Purpose | Auth Required |
|--------|----------|---------|---|
| POST | /api/v1/auth/login | Login with credentials | No |
| POST | /api/v1/auth/refresh | Refresh access token | No |
| POST | /api/v1/auth/logout | Logout and revoke token | Yes |
| POST | /api/v1/auth/verify-mfa | Verify OTP for MFA | No |
| GET | /api/v1/auth/validate | Check token validity | Yes |
| GET | /health | Service health check | No |
| GET | /actuator/metrics | Prometheus metrics | No |

## Troubleshooting

### Port Already in Use
```bash
# Change port
java -jar auth-service.jar --server.port=8081
```

### Database Connection Error
```bash
# Check PostgreSQL is running
psql -U postgres -d auth_service

# Check credentials in application.yaml
# Verify DATABASE_URL environment variable
```

### Redis Connection Error
```bash
# Check Redis is running
redis-cli ping  # Should return PONG

# Verify Redis host/port in application.yaml
```

### JWT Secret Not Set
```bash
# Provide JWT secret
export JWT_SECRET="your-minimum-256-bit-key"

# Verify it's set
echo $JWT_SECRET
```

## Performance Testing

### Load Test with Apache Bench
```bash
# 1000 requests, 10 concurrent
ab -n 1000 -c 10 \
  -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8080/api/v1/auth/validate
```

### Load Test with hey
```bash
hey -n 10000 -c 100 \
  -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8080/api/v1/auth/validate
```

## Monitoring

### Health Check
```bash
curl http://localhost:8080/health
```

### Metrics
```bash
curl http://localhost:8080/actuator/metrics
```

### Detailed Health
```bash
curl http://localhost:8080/actuator/health/details
```

## Docker Deployment

### Build Image
```bash
docker build -t auth-service:latest .
```

### Run Container
```bash
docker run -p 8080:8080 \
  -e JWT_SECRET=your-secret \
  -e DATABASE_URL=jdbc:postgresql://postgres:5432/auth_service \
  -e REDIS_HOST=redis \
  auth-service:latest
```

### Docker Compose
```bash
docker-compose -f docker-compose.yaml up -d
```

## Kubernetes Deployment

### Apply Manifests
```bash
kubectl apply -f infrastructure/kubernetes/services/auth-service.yaml
```

### Check Status
```bash
kubectl get pods -l app=auth-service
kubectl logs -f deployment/auth-service
kubectl describe pod auth-service-xxxxx
```

### Port Forward for Local Testing
```bash
kubectl port-forward svc/auth-service 8080:8080
```

## Development Workflow

### Local Development
```bash
# Start PostgreSQL (Docker)
docker run -d \
  --name postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:15

# Start Redis (Docker)
docker run -d \
  --name redis \
  -p 6379:6379 \
  redis:latest

# Run service
mvn spring-boot:run
```

### Debug Mode
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
```

Connect debugger to localhost:5005

## Common Commands

```bash
# Clean and build
mvn clean install

# Run tests with output
mvn test -X

# Skip tests during build
mvn clean install -DskipTests

# Update dependencies
mvn dependency:update-snapshots

# Format code
mvn spotless:apply

# Check for security vulnerabilities
mvn dependency-check:check

# Generate test coverage report
mvn clean test jacoco:report
```

## Database Management

### Access Database
```bash
psql -U postgres -d auth_service
```

### Common Queries
```sql
-- Check users
SELECT user_id, phone_number, user_type, status FROM users LIMIT 10;

-- Check active refresh tokens
SELECT user_id, device_id, expires_at FROM active_refresh_tokens;

-- Check token blacklist
SELECT user_id, token_jti, blacklisted_at FROM token_blacklist;

-- Check failed login attempts
SELECT phone_number, attempt_count, locked_until FROM login_attempts;
```

## Environment Variables

```bash
# Required
JWT_SECRET                    # Min 256 bits
DATABASE_URL                  # PostgreSQL connection
DATABASE_USER                 # Database user
DATABASE_PASSWORD             # Database password

# Optional (defaults shown)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=               # Leave empty if no auth
AUTH_JWT_ACCESS_EXPIRY_HOURS=1
AUTH_JWT_REFRESH_EXPIRY_DAYS=7
AUTH_LOGIN_ATTEMPT_BLOCKING=true
AUTH_MFA_ENABLED=true
TWILIO_ACCOUNT_SID=          # For MFA SMS
TWILIO_AUTH_TOKEN=           # For MFA SMS
TWILIO_PHONE_NUMBER=         # SMS sender number
```

## Support

- Check README.md for detailed documentation
- Review test files for usage examples
- Check application.yaml for configuration options
- See AUTHENTICATION_SERVICE_DELIVERY.md for complete specification

## Quick Reference

**Package**: com.rideshare.auth
**Main Class**: AuthServiceApplication
**Port**: 8080
**Database**: PostgreSQL (auth_service)
**Cache**: Redis (optional, for rate limiting)
**Test Command**: `mvn test`
**Build Command**: `mvn clean install`
**Run Command**: `mvn spring-boot:run`

---

Ready to deploy! ✅
