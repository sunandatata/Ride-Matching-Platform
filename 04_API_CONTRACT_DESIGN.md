# API Contract Design

This document specifies all REST API endpoints, request/response schemas, error handling, and authentication/authorization requirements.

---

## 1. API Overview

### Base URLs
- **Development**: `https://api-dev.rideshare.local`
- **Staging**: `https://api-staging.rideshare.local`
- **Production**: `https://api.rideshare.local`

### API Versioning
- Current version: **v1**
- Backward compatibility: Maintain v1 for 2 major versions before deprecation
- Version in URL path: `/api/v1/...`

### Request/Response Format
- **Content-Type**: `application/json`
- **Encoding**: UTF-8
- **Date Format**: ISO 8601 (e.g., `2026-06-02T14:30:00Z`)

### Rate Limiting
```
Headers:
  X-RateLimit-Limit: 100        (requests per minute)
  X-RateLimit-Remaining: 45
  X-RateLimit-Reset: 1654178265 (Unix timestamp)

Limits:
  Authenticated users: 100 req/min
  Anonymous (public endpoints): 20 req/min
  Matching Engine (internal): 10,000 req/min (bypass limits)
```

---

## 2. Authentication & Authorization

### JWT Token Structure

```json
{
  "sub": "user_id",
  "type": "rider|driver|admin",
  "iat": 1654167600,
  "exp": 1654171200,
  "iss": "rideshare.local",
  "aud": "rideshare-api",
  "scope": "rides:read rides:write profile:read"
}
```

### Authentication Endpoints

#### POST /api/v1/auth/login

Request:
```json
{
  "phone_number": "+1234567890",
  "password": "encrypted_password",
  "device_id": "device-uuid",
  "device_type": "ios|android|web"
}
```

Response (200 OK):
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
  "expires_in": 3600,
  "token_type": "Bearer",
  "user": {
    "user_id": "U123",
    "first_name": "John",
    "last_name": "Doe",
    "phone_number": "+1234567890",
    "type": "rider|driver"
  }
}
```

Errors:
```
400 Bad Request: Missing required fields
401 Unauthorized: Invalid credentials
429 Too Many Requests: Too many login attempts (rate limited)
```

#### POST /api/v1/auth/refresh

Request:
```json
{
  "refresh_token": "eyJhbGciOiJIUzI1NiIs..."
}
```

Response (200 OK):
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "expires_in": 3600,
  "token_type": "Bearer"
}
```

#### POST /api/v1/auth/logout

Request:
```json
{
  "refresh_token": "eyJhbGciOiJIUzI1NiIs..."
}
```

Response (200 OK):
```json
{
  "message": "Logged out successfully"
}
```

---

## 3. Rider Endpoints

### POST /api/v1/rides

**Description**: Create a new ride request

**Authentication**: Required (rider)

Request:
```json
{
  "pickup_location": {
    "latitude": 40.7128,
    "longitude": -74.0060,
    "address": "123 Main St, NYC"
  },
  "dropoff_location": {
    "latitude": 40.7580,
    "longitude": -73.9855,
    "address": "Empire State Building, NYC"
  },
  "passenger_count": 1,
  "service_type": "ECONOMY|PREMIUM|SHARED",
  "payment_method_id": "PM123",
  "promo_code": "SUMMER20" [optional],
  "notes": "Allergic to pets" [optional],
  "idempotency_key": "unique-key-12345" [optional, for deduplication]
}
```

Response (201 Created):
```json
{
  "ride_id": "R123456",
  "status": "REQUESTED",
  "pickup_location": {...},
  "dropoff_location": {...},
  "passenger_count": 1,
  "estimated_fare": 12.50,
  "estimated_duration_seconds": 900,
  "estimated_distance_meters": 3200,
  "service_type": "ECONOMY",
  "created_at": "2026-06-02T14:30:00Z",
  "expires_at": "2026-06-02T14:30:30Z"
}
```

Errors:
```
400 Bad Request: Invalid locations, invalid passenger count
401 Unauthorized: Not authenticated
402 Payment Required: No valid payment method
409 Conflict: Duplicate request (idempotency key matched)
429 Too Many Requests: User has 3+ active rides
503 Service Unavailable: Matching engine unavailable
```

### GET /api/v1/rides/{ride_id}

**Description**: Get ride details

Response (200 OK):
```json
{
  "ride_id": "R123456",
  "status": "MATCHED",
  "rider_id": "U789",
  "driver": {
    "driver_id": "D456",
    "first_name": "Jane",
    "phone_number": "+1987654321",
    "rating": 4.8,
    "profile_photo_url": "https://...",
    "vehicle": {
      "make": "Tesla",
      "model": "Model 3",
      "color": "Black",
      "license_plate": "ABC123"
    },
    "location": {
      "latitude": 40.7150,
      "longitude": -74.0050
    },
    "eta_to_pickup_seconds": 240
  },
  "pickup_location": {...},
  "dropoff_location": {...},
  "passenger_count": 1,
  "estimated_fare": 12.50,
  "final_fare": null,
  "status_history": [
    {
      "status": "REQUESTED",
      "timestamp": "2026-06-02T14:30:00Z"
    },
    {
      "status": "MATCHED",
      "timestamp": "2026-06-02T14:30:05Z"
    }
  ],
  "created_at": "2026-06-02T14:30:00Z",
  "updated_at": "2026-06-02T14:30:05Z"
}
```

### GET /api/v1/riders/{rider_id}/rides

**Description**: Get ride history (paginated)

Query Parameters:
```
status: REQUESTED|MATCHED|STARTED|COMPLETED|CANCELLED (optional, filter)
limit: 20 (default)
offset: 0 (pagination)
sort_by: created_at|completed_at (default: created_at)
sort_order: asc|desc (default: desc)
```

Response (200 OK):
```json
{
  "data": [
    {ride_id, status, driver, fare, created_at, ...},
    ...
  ],
  "pagination": {
    "limit": 20,
    "offset": 0,
    "total": 245,
    "has_more": true
  }
}
```

### POST /api/v1/rides/{ride_id}/cancel

**Description**: Cancel a ride request or in-progress ride

Request:
```json
{
  "reason": "Driver taking too long|Wrong location|Emergency|Other",
  "notes": "Please refund the surge charge" [optional]
}
```

Response (200 OK):
```json
{
  "ride_id": "R123456",
  "status": "CANCELLED",
  "cancellation_reason": "Driver taking too long",
  "refund_amount": 5.00,
  "refund_status": "PENDING|COMPLETED",
  "cancelled_at": "2026-06-02T14:35:00Z"
}
```

Errors:
```
400 Bad Request: Invalid reason
403 Forbidden: Cannot cancel completed ride
409 Conflict: Ride already cancelled
```

### POST /api/v1/rides/{ride_id}/rate

**Description**: Rate driver and provide feedback (after ride completion)

Request:
```json
{
  "rating": 4,  // 1-5 stars
  "feedback": "Great driver, clean car",
  "issues": ["long_wait", "not_friendly"] [optional, predefined tags]
}
```

Response (200 OK):
```json
{
  "ride_id": "R123456",
  "rating_submitted": true,
  "rating": 4,
  "driver_id": "D456",
  "feedback": "Great driver, clean car"
}
```

---

## 4. Driver Endpoints

### POST /api/v1/drivers/availability

**Description**: Update driver availability status

Request:
```json
{
  "status": "ONLINE|OFFLINE|ON_BREAK",
  "reason": "break_time|meal|fuel_stop" [optional, required if ON_BREAK]
}
```

Response (200 OK):
```json
{
  "driver_id": "D456",
  "status": "ONLINE",
  "updated_at": "2026-06-02T14:30:00Z"
}
```

### PUT /api/v1/drivers/locations

**Description**: Update driver location (high-frequency endpoint)

Request:
```json
{
  "latitude": 40.7150,
  "longitude": -74.0050,
  "heading": 90,
  "speed_mph": 25,
  "accuracy_meters": 8,
  "timestamp": "2026-06-02T14:30:00Z"
}
```

Response (202 Accepted):
```json
{
  "message": "Location update queued",
  "batch_id": "batch-123"
}
```

**Note**: Response is 202 because the update is asynchronously batched.

### GET /api/v1/drivers/{driver_id}/rides

**Description**: Get driver's ride history and earnings

Query Parameters:
```
status: COMPLETED|CANCELLED (filter)
date_from: 2026-06-01 (optional)
date_to: 2026-06-02 (optional)
limit: 20
offset: 0
```

Response (200 OK):
```json
{
  "data": [
    {
      "ride_id": "R123456",
      "status": "COMPLETED",
      "rider": {...},
      "pickup_location": {...},
      "dropoff_location": {...},
      "fare": 12.50,
      "driver_earnings": 9.38,
      "surge_multiplier": 1.0,
      "started_at": "2026-06-02T14:35:00Z",
      "completed_at": "2026-06-02T14:50:00Z"
    }
  ],
  "summary": {
    "total_rides": 45,
    "total_earnings": 425.50,
    "average_rating": 4.8,
    "acceptance_rate": 95.2,
    "cancellation_rate": 2.1
  },
  "pagination": {...}
}
```

### POST /api/v1/drivers/{ride_id}/started

**Description**: Mark ride as started (driver picked up rider)

Request:
```json
{
  "start_location": {
    "latitude": 40.7128,
    "longitude": -74.0060
  },
  "timestamp": "2026-06-02T14:35:00Z"
}
```

Response (200 OK):
```json
{
  "ride_id": "R123456",
  "status": "STARTED",
  "started_at": "2026-06-02T14:35:00Z"
}
```

### POST /api/v1/drivers/{ride_id}/completed

**Description**: Mark ride as completed (driver dropped off rider)

Request:
```json
{
  "end_location": {
    "latitude": 40.7580,
    "longitude": -73.9855
  },
  "distance_meters": 3200,
  "duration_seconds": 900,
  "timestamp": "2026-06-02T14:50:00Z"
}
```

Response (200 OK):
```json
{
  "ride_id": "R123456",
  "status": "COMPLETED",
  "final_fare": 15.75,
  "driver_earnings": 11.81,
  "completed_at": "2026-06-02T14:50:00Z"
}
```

### POST /api/v1/drivers/{ride_id}/cancel

**Description**: Cancel assigned ride (driver cancels)

Request:
```json
{
  "reason": "Mechanical issue|Wrong address|Rider not ready|Other"
}
```

Response (200 OK):
```json
{
  "ride_id": "R123456",
  "status": "CANCELLED",
  "cancelled_by": "DRIVER",
  "cancelled_at": "2026-06-02T14:35:00Z",
  "cancellation_penalty": 5.00
}
```

Errors:
```
403 Forbidden: Driver cannot cancel after pickup
```

---

## 5. Profile Endpoints (Riders & Drivers)

### GET /api/v1/users/profile

**Description**: Get current user profile

Response (200 OK):
```json
{
  "user_id": "U789",
  "type": "rider|driver",
  "first_name": "John",
  "last_name": "Doe",
  "phone_number": "+1234567890",
  "email": "john@example.com",
  "profile_photo_url": "https://...",
  "date_of_birth": "1990-05-15",
  "kyc_verified": true,
  "kyc_verified_at": "2026-01-01T00:00:00Z",
  "average_rating": 4.8,
  "total_rides": 250,

  // Rider-specific
  "payment_methods": [...],

  // Driver-specific
  "license_number": "DL123456",
  "vehicle_info": {...},
  "total_earnings": 12500.00,
  "acceptance_rate": 95.2,
  "cancellation_rate": 2.1
}
```

### PUT /api/v1/users/profile

**Description**: Update profile

Request:
```json
{
  "first_name": "John",
  "last_name": "Doe",
  "email": "newemail@example.com",
  "profile_photo_url": "https://...",
  "emergency_contact_name": "Jane Doe",
  "emergency_contact_phone": "+1987654321"
}
```

Response (200 OK):
```json
{same as GET /profile}
```

### POST /api/v1/users/payment-methods

**Description**: Add payment method

Request:
```json
{
  "type": "CREDIT_CARD|DEBIT_CARD|WALLET|UPI",
  "token": "pm_1234567890abcdef",  // From Stripe tokenization
  "is_primary": true,
  "card_holder_name": "John Doe"
}
```

Response (201 Created):
```json
{
  "payment_method_id": "PM123",
  "type": "CREDIT_CARD",
  "last_four": "4242",
  "is_primary": true,
  "created_at": "2026-06-02T14:30:00Z"
}
```

### GET /api/v1/users/payment-methods

**Description**: List payment methods

Response (200 OK):
```json
{
  "data": [
    {
      "payment_method_id": "PM123",
      "type": "CREDIT_CARD",
      "last_four": "4242",
      "is_primary": true,
      "created_at": "2026-06-02T14:30:00Z"
    }
  ]
}
```

### DELETE /api/v1/users/payment-methods/{payment_method_id}

**Description**: Remove payment method

Response (200 OK):
```json
{
  "message": "Payment method deleted"
}
```

---

## 6. Admin/Operational Endpoints

### GET /api/v1/admin/rides/{ride_id}/details

**Description**: Get comprehensive ride details (for support team)

Response (200 OK):
```json
{
  "ride_id": "R123456",
  "status": "COMPLETED",
  "rider": {...},
  "driver": {...},
  "timeline": [
    {"status": "REQUESTED", "timestamp": "..."},
    {"status": "MATCHED", "timestamp": "..."},
    ...
  ],
  "events": [
    {
      "event_type": "ride.requested",
      "event_data": {...},
      "timestamp": "..."
    },
    ...
  ],
  "fare_breakdown": {
    "base_fare": 2.00,
    "distance_fare": 4.00,
    "time_fare": 3.50,
    "surge_multiplier": 1.0,
    "discount_amount": 0.00,
    "service_fee": 0.75,
    "final_fare": 10.25
  },
  "payment": {...},
  "ratings": {...}
}
```

---

## 7. Real-Time WebSocket Endpoints

### WebSocket: /ws/rides/{ride_id}

**Purpose**: Real-time updates for ride (pickup location, ETA, status changes)

**Authentication**: Required (JWT token)

**Message Format** (Server → Client):

```json
{
  "type": "ride.status_changed|driver.location_updated|driver.eta_updated|ride.cancelled",
  "data": {
    "ride_id": "R123456",
    "status": "MATCHED",
    "driver_location": {
      "latitude": 40.7150,
      "longitude": -74.0050,
      "heading": 90,
      "speed_mph": 25
    },
    "eta_to_pickup_seconds": 180,
    "updated_at": "2026-06-02T14:30:30Z"
  }
}
```

**Message Format** (Client → Server):

```json
{
  "type": "ping",
  "timestamp": "2026-06-02T14:30:30Z"
}
```

**Events Published**:
- `driver.location_updated` (every 5 seconds while en route)
- `driver.eta_updated` (when ETA changes significantly)
- `ride.status_changed` (MATCHED, STARTED, COMPLETED, CANCELLED)
- `driver.arrived` (driver arrived at pickup/dropoff)
- `ride.cancelled` (ride cancelled by rider or driver)

---

## 8. Error Handling Standards

### Global Error Response Format

```json
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Validation failed",
    "details": [
      {
        "field": "passenger_count",
        "message": "Must be between 1 and 6"
      }
    ],
    "request_id": "req-12345",
    "timestamp": "2026-06-02T14:30:00Z"
  }
}
```

### Error Codes

| Code | HTTP Status | Meaning | Retry |
|------|-------------|---------|-------|
| `INVALID_REQUEST` | 400 | Malformed request | No |
| `AUTHENTICATION_FAILED` | 401 | Invalid/expired token | Yes (refresh token) |
| `FORBIDDEN` | 403 | Insufficient permissions | No |
| `NOT_FOUND` | 404 | Resource not found | No |
| `DUPLICATE_REQUEST` | 409 | Idempotent request already processed | No |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests | Yes (exponential backoff) |
| `PAYMENT_FAILED` | 402 | Payment processing failed | Yes (with new method) |
| `MATCHING_TIMEOUT` | 504 | Matching engine didn't respond in time | Yes |
| `SERVICE_UNAVAILABLE` | 503 | Service temporarily down | Yes (exponential backoff) |

### Retry Strategy (Client-side)

```
Circuit Breaker Logic:
- If error is retriable (429, 503, 504):
  - Attempt exponential backoff: 100ms, 200ms, 400ms, 800ms, ...
  - Max retries: 5
  - Max wait time: 30 seconds
- If error is non-retriable (4xx except 429):
  - Return error immediately; don't retry
```

---

## 9. API Deprecation Policy

### Deprecation Timeline

```
Phase 1 (Announcement): Alert developers that endpoint will be deprecated
  - Add X-API-Deprecated: true header
  - Add deprecation notice in documentation
  - Wait 6 months

Phase 2 (Soft Deprecation): Endpoint still works but discouraged
  - Return 400 with warning message
  - Log usage for analytics
  - Recommend migration path
  - Wait 6 months

Phase 3 (Hard Deprecation): Endpoint removed
  - Return 404 or 501 Not Implemented
  - Operators aware of traffic impact

Minimum deprecation period: 6 months
```

---

## 10. API Versioning Strategy

### URL Path Versioning
```
/api/v1/rides     (current)
/api/v2/rides     (future, if needed)
```

### Backward Compatibility
- Fields added: Always safe (old clients ignore new fields)
- Fields removed: Not allowed; use soft deprecation
- Field types changed: Not allowed; create new field
- Required fields added: Not allowed; must be optional

### Example: Adding Optional Field
```
v1.0: POST /rides → {pickup, dropoff}
v1.1: POST /rides → {pickup, dropoff, promo_code? [optional]}

Both versions work; old clients continue without promo_code.
```

---

## Summary: API Design Decisions

| Decision | Rationale | Trade-off |
|----------|-----------|-----------|
| **v1 in URL path** | Easy version identification; explicit in logs | URL bloat; longer paths |
| **202 Accepted for location** | Non-blocking; asynchronous batching | Clients can't verify write success immediately |
| **JWT tokens** | Stateless authentication; easy horizontal scaling | Token revocation requires blacklist |
| **Rate limiting** | Prevent abuse; protect backend | User experience degradation during spikes |
| **Event IDs in responses** | Idempotency; request tracing | Extra data; complexity |
| **WebSocket for real-time** | Low latency; persistent connection | Stateful service; harder to scale |

---

**Next**: Matching Engine Design, Real-Time Communication, Folder Structure
