# Distributed Data Processing Platform - Setup Guide

## 🏗️ Project Architecture

This is a **distributed ride-sharing microservices platform** with:
- **6 Java Spring Boot backend services** deployed as microservices
- **3 React frontend applications** (Admin, Rider, Driver)
- **Docker & Kubernetes** infrastructure
- **Message queuing** (Kafka) for event-driven architecture
- **Redis** for caching and real-time features

---

## 📋 System Requirements

### Windows 10/11 (Current Environment)

#### Backend Stack
- **Java 21** ([Download](https://www.oracle.com/java/technologies/downloads/#java21))
  - Set `JAVA_HOME` environment variable
  - Verify: `java -version`

- **Apache Maven 3.9+** ([Download](https://maven.apache.org/download.cgi))
  - Set `M2_HOME` environment variable
  - Add `%M2_HOME%\bin` to PATH
  - Verify: `mvn -version`

- **PostgreSQL 15+** ([Download](https://www.postgresql.org/download/windows/))
  - Required for auth, driver, and ride services
  - Default: `postgres://localhost:5432`
  - Create databases: `rideshare_auth`, `rideshare_driver`, `rideshare_ride`

- **Redis 7+** ([Download](https://github.com/microsoftarchive/redis/releases))
  - Required for caching and real-time location tracking
  - Default: `redis://localhost:6379`
  - Windows: Use WSL2 or Docker

- **Apache Kafka 3.6+** ([Download](https://kafka.apache.org/downloads))
  - Required for event streaming (location updates, ride events)
  - Default: `localhost:9092`
  - Windows: Use WSL2 or Docker

#### Frontend Stack
- **Node.js 18+ & npm** ([Download](https://nodejs.org/))
  - Verify: `node -v` and `npm -v`

#### Containerization (Optional but Recommended)
- **Docker Desktop for Windows** ([Download](https://www.docker.com/products/docker-desktop))
  - Includes Docker & Docker Compose
  - Automatically runs PostgreSQL, Redis, Kafka in containers

---

## 🚀 Quick Start (Recommended with Docker)

### 1. Install Docker Desktop
```bash
# Windows: Download and install Docker Desktop
# Then restart your machine
docker --version
docker-compose --version
```

### 2. Clone/Navigate to Project
```bash
cd C:\Users\sunan\Downloads\Distributed Data Processing Platform
```

### 3. Start Infrastructure (Docker)
```bash
# Starts PostgreSQL, Redis, Kafka in containers
docker-compose up -d
```

### 4. Build Backend
```bash
cd backend
mvn clean install -DskipTests
# This compiles all microservices
```

### 5. Run Backend Services
```bash
# Terminal 1: Auth Service
cd backend/auth-service
mvn spring-boot:run

# Terminal 2: Ride Service
cd backend/ride-service
mvn spring-boot:run

# Terminal 3: Driver Service
cd backend/driver-service
mvn spring-boot:run

# Terminal 4: Location Service
cd backend/location-service
mvn spring-boot:run

# Terminal 5: Notification Service (WebSocket)
cd backend/notification-service
mvn spring-boot:run

# Terminal 6: ETA Service
cd backend/eta-service
mvn spring-boot:run
```

### 6. Setup & Run Frontend

#### Admin App
```bash
cd frontend/admin-app
npm install
npm run dev
# Opens http://localhost:5173
```

#### Rider App
```bash
cd frontend/rider-app
npm install
npm run dev
# Opens http://localhost:5174
```

#### Driver App
```bash
cd frontend/driver-app
npm install
npm run dev
# Opens http://localhost:5175
```

---

## 🐳 Alternative: All-in-One Docker Setup

```bash
# Build and run everything in Docker
docker-compose -f docker-compose.full.yml up --build

# Backend services available at:
# - Auth: http://localhost:8001
# - Rides: http://localhost:8002
# - Drivers: http://localhost:8003
# - Location: http://localhost:8004
# - Notification: http://localhost:8005
# - ETA: http://localhost:8006

# Frontend apps:
# - Admin: http://localhost:3001
# - Rider: http://localhost:3002
# - Driver: http://localhost:3003
```

---

## 🏗️ Backend Services

### Service Ports & Responsibilities

| Service | Port | Database | Responsibility |
|---------|------|----------|-----------------|
| Auth | 8001 | PostgreSQL | User authentication, JWT tokens, MFA |
| Ride | 8002 | PostgreSQL | Ride requests, matching, state management |
| Driver | 8003 | PostgreSQL | Driver profiles, documents, vehicle info |
| Location | 8004 | Redis | Real-time driver location, geo-queries |
| Notification | 8005 | - | WebSocket real-time updates |
| ETA | 8006 | Redis | ETA calculation, route caching |

### Key Endpoints

**Authentication**
```
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/verify-mfa
GET /api/v1/auth/validate
```

**Rides**
```
POST /api/v1/rides
GET /api/v1/rides/{rideId}
PUT /api/v1/rides/{rideId}/status
PUT /api/v1/rides/{rideId}/driver
POST /api/v1/rides/{rideId}/cancel
POST /api/v1/rides/{rideId}/rate
```

**Drivers**
```
POST /drivers
GET /drivers/{driverId}
PUT /drivers/{driverId}
PUT /drivers/{driverId}/vehicle
GET /drivers/stats
POST /drivers/{driverId}/documents
```

**Locations**
```
POST /api/v1/locations/update
GET /api/v1/locations/nearby
```

---

## 🎨 Frontend Apps

### Admin App
- **URL**: http://localhost:5173
- **Purpose**: Platform monitoring and driver management
- **Stack**: React 18 + TypeScript + Material UI + Vite

### Rider App
- **URL**: http://localhost:5174
- **Purpose**: Request rides, track, rate drivers
- **Stack**: React 18 + TypeScript + Material UI + Vite

### Driver App
- **URL**: http://localhost:5175
- **Purpose**: Accept rides, manage account, track earnings
- **Stack**: React 18 + TypeScript + Material UI + Vite

---

## 🔧 Environment Variables

### Backend (Create `.env` in backend root)
```env
# PostgreSQL
DB_HOST=localhost
DB_PORT=5432
DB_NAME=rideshare_auth
DB_USER=postgres
DB_PASSWORD=postgres

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Kafka
KAFKA_BROKERS=localhost:9092

# JWT
JWT_SECRET=your-secret-key-here-change-in-production
JWT_EXPIRY_MINUTES=30
```

### Frontend (Create `.env` in each app root)
```env
VITE_API_BASE_URL=http://localhost:8001
VITE_WS_URL=ws://localhost:8005
```

---

## 📊 Database Setup

### PostgreSQL

```bash
# Connect to PostgreSQL
psql -U postgres

# Create databases
CREATE DATABASE rideshare_auth;
CREATE DATABASE rideshare_driver;
CREATE DATABASE rideshare_ride;

# Create user (optional)
CREATE USER rideshare WITH PASSWORD 'rideshare';
GRANT ALL PRIVILEGES ON DATABASE rideshare_auth TO rideshare;
GRANT ALL PRIVILEGES ON DATABASE rideshare_driver TO rideshare;
GRANT ALL PRIVILEGES ON DATABASE rideshare_ride TO rideshare;
```

### Redis

```bash
# Start Redis (Windows with WSL2)
wsl
redis-server

# Or with Docker
docker run -d -p 6379:6379 redis:7
```

### Kafka

```bash
# Start Zookeeper
zookeeper-server-start.bat config/zookeeper.properties

# Start Kafka (separate terminal)
kafka-server-start.bat config/server.properties
```

---

## ✅ Verification Checklist

- [ ] Java 21 installed: `java -version`
- [ ] Maven 3.9+ installed: `mvn -version`
- [ ] Node.js 18+ installed: `node -v`
- [ ] PostgreSQL running: `psql -U postgres -l`
- [ ] Redis running: `redis-cli ping` → PONG
- [ ] Kafka running: Check ZooKeeper & Kafka processes
- [ ] Backend builds: `mvn clean install -DskipTests` (no errors)
- [ ] Auth service starts: `mvn spring-boot:run` (port 8001)
- [ ] Admin app installs: `npm install` (no errors)

---

## 🐛 Troubleshooting

### Maven "command not found"
- Add `%M2_HOME%\bin` to Windows PATH
- Restart terminal or use `./mvnw` (Maven Wrapper)

### Port already in use
```bash
# Find process using port (e.g., 8001)
netstat -ano | findstr :8001
# Kill process
taskkill /PID <PID> /F
```

### Docker issues on Windows
- Enable WSL 2 in Docker Desktop settings
- Use Docker Compose instead of manual containers

### Frontend won't connect to backend
- Check `VITE_API_BASE_URL` in `.env`
- Ensure backend service is running on correct port
- Check CORS headers in Spring Boot config

---

## 📚 Useful Commands

```bash
# Backend
cd backend
mvn clean build                    # Build all services
mvn clean install -DskipTests      # Build without running tests
mvn spring-boot:run                # Run a specific service

# Frontend
npm install                        # Install dependencies
npm run dev                        # Start dev server
npm run build                      # Build for production
npm run preview                    # Preview production build

# Docker
docker-compose up -d               # Start all services
docker-compose down                # Stop all services
docker-compose logs -f             # Follow logs
docker ps                          # List running containers
```

---

## 🔐 Security Notes

⚠️ **Before Production:**
- Change all default passwords
- Set secure JWT_SECRET (min 32 chars)
- Enable HTTPS/TLS
- Configure CORS properly
- Use environment-specific configs
- Enable rate limiting
- Add API authentication (OAuth2/JWT verification)

---

## 📖 Architecture

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   Admin     │  │   Rider     │  │   Driver    │
│    React    │  │    React    │  │    React    │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                 │                 │
       └─────────────┬───┴────────┬────────┘
                     │            │
                  API Gateway / Load Balancer
                     │            │
       ┌─────────────┼────────────┼──────────────┐
       │             │            │              │
    ┌──▼──┐      ┌──▼──┐     ┌──▼──┐      ┌──▼──┐
    │Auth │      │Ride │     │Driver    │Location│
    │Svc  │      │Svc  │     │Svc      │Svc    │
    └──┬──┘      └──┬──┘     └──┬──┘      └──┬──┘
       │             │            │          │
       └─────────────┼────────────┼──────────┘
                     │            │
            ┌────────▼────────┬──▼──────┐
            │  PostgreSQL     │  Redis  │
            │  (Auth, Ride,   │(Caching,│
            │   Driver data)  │Location)│
            └─────────────────┴─────────┘
                     │
               ┌─────▼─────┐
               │   Kafka   │
               │(Events)   │
               └───────────┘
```

---

## 📝 Next Steps

1. **Install requirements** (Java, Maven, Node.js, Docker)
2. **Start infrastructure** (`docker-compose up -d`)
3. **Build backend** (`mvn clean install -DskipTests`)
4. **Run services** (6 terminals, one per service)
5. **Start frontend apps** (`npm run dev`)
6. **Access apps** (http://localhost:5173, :5174, :5175)

---

For detailed configuration and advanced topics, see individual service READMEs in backend modules.
