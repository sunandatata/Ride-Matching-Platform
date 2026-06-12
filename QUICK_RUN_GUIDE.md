# Quick Run Guide - Windows Setup & Execution

> **Current Status:** Node.js ✅ | Java ❌ | Maven ❌ | Databases ❌

---

## 🚀 Fast Track (15 minutes to running app)

### Step 1: Install Java 21 (5 minutes)

1. **Download Java 21 JDK**
   - Go to: https://www.oracle.com/java/technologies/downloads/#java21
   - Click "Windows x64 Installer" (for your system)
   - Run the installer (.exe)
   - **Accept all defaults** and click Next until done

2. **Verify Installation**
   ```bash
   java -version
   ```
   Should show: `Java 21.x.x`

3. **Set JAVA_HOME (Critical)**
   - Open **Control Panel** → **System** → **Advanced system settings**
   - Click **Environment Variables** button
   - Click **New** (under System variables)
   - Variable name: `JAVA_HOME`
   - Variable value: `C:\Program Files\Java\jdk-21` (or wherever Java installed)
   - Click OK, OK, OK
   - **Restart your terminal** for changes to take effect

---

### Step 2: Install Maven (3 minutes)

1. **Download Maven 3.9+**
   - Go to: https://maven.apache.org/download.cgi
   - Download: `apache-maven-3.9.x-bin.zip` (Binary)
   - Extract to: `C:\Program Files\apache-maven-3.9.x` (create this folder)

2. **Set M2_HOME (Critical)**
   - Open **Control Panel** → **System** → **Advanced system settings**
   - Click **Environment Variables**
   - Click **New** (under System variables)
   - Variable name: `M2_HOME`
   - Variable value: `C:\Program Files\apache-maven-3.9.x`
   - Click OK
   - Click **Edit PATH** (under System variables)
   - Click **New** and add: `%M2_HOME%\bin`
   - Click OK, OK, OK
   - **Restart your terminal**

3. **Verify Installation**
   ```bash
   mvn -version
   ```
   Should show Maven version 3.9+

---

### Step 3: Set Up Databases (Docker - Easiest)

> **Option A (Easiest): Use Docker**
> **Option B (Manual): Install PostgreSQL & Redis locally**

#### OPTION A: Docker (Recommended) - 3 minutes

1. **Install Docker Desktop**
   - Download: https://www.docker.com/products/docker-desktop
   - Run installer, restart computer
   - Verify: `docker --version`

2. **Create docker-compose.yml in project root**
   ```bash
   cd C:\Users\sunan\Downloads\Distributed Data Processing Platform
   # Create this file:
   ```

   **File: `docker-compose.yml`**
   ```yaml
   version: '3.8'

   services:
     postgres:
       image: postgres:15-alpine
       environment:
         POSTGRES_USER: postgres
         POSTGRES_PASSWORD: postgres
       ports:
         - "5432:5432"
       volumes:
         - postgres_data:/var/lib/postgresql/data

     redis:
       image: redis:7-alpine
       ports:
         - "6379:6379"

     kafka:
       image: confluentinc/cp-kafka:7.5.0
       environment:
         KAFKA_BROKER_ID: 1
         KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
         KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
         KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
         KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
         KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
       ports:
         - "9092:9092"
       depends_on:
         - zookeeper

     zookeeper:
       image: confluentinc/cp-zookeeper:7.5.0
       environment:
         ZOOKEEPER_CLIENT_PORT: 2181
       ports:
         - "2181:2181"

   volumes:
     postgres_data:
   ```

3. **Start All Services**
   ```bash
   docker-compose up -d
   ```

4. **Verify Services Running**
   ```bash
   docker-compose ps
   ```
   All should show "healthy" or "running"

---

#### OPTION B: Manual Installation (If no Docker)

**PostgreSQL:**
1. Download: https://www.postgresql.org/download/windows/
2. Install, use password: `postgres`
3. Verify: `psql -U postgres -l`

**Redis:**
1. Use Windows Subsystem for Linux (WSL2) or download from: https://github.com/microsoftarchive/redis/releases
2. Start: `redis-server`

**Kafka:** (Skip if using Docker)
- Complex to set up on Windows, use Docker instead

---

### Step 4: Create Databases (PostgreSQL)

Open new terminal:

```bash
# Connect to PostgreSQL
psql -U postgres

# Inside psql, run:
CREATE DATABASE rideshare_auth;
CREATE DATABASE rideshare_driver;
CREATE DATABASE rideshare_ride;

# Verify
\l

# Exit
\q
```

---

## ▶️ RUN THE FULL STACK

### Terminal 1: Build Backend (One-time, 2-3 minutes)

```bash
cd C:\Users\sunan\Downloads\Distributed Data Processing Platform\backend
mvn clean install -DskipTests
```

Wait for: `BUILD SUCCESS`

---

### Terminal 2: Auth Service (8001)

```bash
cd C:\Users\sunan\Downloads\Distributed Data Processing Platform\backend\auth-service
mvn spring-boot:run
```

Wait for: `Started AuthServiceApplication in X seconds`

---

### Terminal 3: Ride Service (8002)

```bash
cd C:\Users\sunan\Downloads\Distributed Data Processing Platform\backend\ride-service
mvn spring-boot:run
```

Wait for: `Started RideServiceApplication in X seconds`

---

### Terminal 4: Driver Service (8003)

```bash
cd C:\Users\sunan\Downloads\Distributed Data Processing Platform\backend\driver-service
mvn spring-boot:run
```

Wait for: `Started DriverServiceApplication in X seconds`

---

### Terminal 5: Location Service (8004)

```bash
cd C:\Users\sunan\Downloads\Distributed Data Processing Platform\backend\location-service
mvn spring-boot:run
```

Wait for: `Started LocationServiceApplication in X seconds`

---

### Terminal 6: Notification Service (8005)

```bash
cd C:\Users\sunan\Downloads\Distributed Data Processing Platform\backend\notification-service
mvn spring-boot:run
```

Wait for: `Started NotificationServiceApplication in X seconds`

---

### Terminal 7: ETA Service (8006)

```bash
cd C:\Users\sunan\Downloads\Distributed Data Processing Platform\backend\eta-service
mvn spring-boot:run
```

Wait for: `Started ETAServiceApplication in X seconds`

---

### Terminal 8: Admin Frontend (5173)

```bash
cd C:\Users\sunan\Downloads\Distributed Data Processing Platform\frontend\admin-app
npm install
npm run dev
```

Open browser: http://localhost:5173

---

### Terminal 9: Rider Frontend (5174)

```bash
cd C:\Users\sunan\Downloads\Distributed Data Processing Platform\frontend\rider-app
npm install
npm run dev
```

Open browser: http://localhost:5174

---

### Terminal 10: Driver Frontend (5175)

```bash
cd C:\Users\sunan\Downloads\Distributed Data Processing Platform\frontend\driver-app
npm install
npm run dev
```

Open browser: http://localhost:5175

---

## ✅ Verification Checklist

Once everything is running:

```bash
# Test Auth Service
curl http://localhost:8001/api/v1/auth/validate

# Test Ride Service
curl http://localhost:8002/api/v1/rides

# Test Driver Service
curl http://localhost:8003/drivers

# Test Location Service
curl http://localhost:8004/api/v1/locations/nearby

# Test Notification Service (WebSocket)
# Open http://localhost:8005/ws in browser

# Test Frontend
# Admin: http://localhost:5173
# Rider: http://localhost:5174
# Driver: http://localhost:5175
```

---

## 🎯 Expected Result

| Component | Port | Status |
|-----------|------|--------|
| Auth Service | 8001 | ✅ Running |
| Ride Service | 8002 | ✅ Running |
| Driver Service | 8003 | ✅ Running |
| Location Service | 8004 | ✅ Running |
| Notification Service | 8005 | ✅ Running |
| ETA Service | 8006 | ✅ Running |
| Admin Frontend | 5173 | ✅ Running |
| Rider Frontend | 5174 | ✅ Running |
| Driver Frontend | 5175 | ✅ Running |
| PostgreSQL | 5432 | ✅ Running |
| Redis | 6379 | ✅ Running |
| Kafka | 9092 | ✅ Running |

---

## 🐛 Troubleshooting

### "mvn: command not found"
- Did you restart terminal after setting M2_HOME?
- Check: `echo %M2_HOME%` should show path
- If empty, redo "Set M2_HOME" step

### "java: command not found"
- Did you restart terminal after setting JAVA_HOME?
- Check: `echo %JAVA_HOME%` should show path
- If empty, redo "Set JAVA_HOME" step

### Port already in use
```bash
# Find process using port (e.g., 5173)
netstat -ano | findstr :5173

# Kill process
taskkill /PID <PID> /F
```

### Database connection error
- Verify PostgreSQL running: `psql -U postgres -l`
- Verify Redis running: `redis-cli ping` (should return PONG)
- Check databases exist: `psql -U postgres -l | grep rideshare`

### Docker containers not starting
```bash
docker-compose logs -f
# Shows detailed error messages
```

---

## 📝 Next Steps After Running

1. **Test Login Flow** (Rider App)
   - Go to http://localhost:5174
   - Try login with test credentials
   - Request a ride

2. **Monitor Admin Dashboard**
   - Go to http://localhost:5173
   - Should see real-time metrics

3. **Track Driver Activity**
   - Go to http://localhost:5175
   - Accept rides, update location

4. **Check Logs**
   - Backend logs show in each terminal
   - Frontend logs show in browser DevTools

---

## ⏹️ Stop All Services

```bash
# Stop all backend services
# Ctrl+C in each terminal

# Stop Docker containers
docker-compose down

# Remove Docker volumes (optional)
docker-compose down -v
```

---

## 💾 Save This for Future Runs

Next time you want to run the stack:

1. **Docker up** (if using Docker): `docker-compose up -d`
2. **Build once**: `mvn clean install -DskipTests` (if code changed)
3. **Run 10 commands** in parallel terminals (see above)

Or create a batch script to automate:

**start-stack.bat**
```batch
@echo off
cd C:\Users\sunan\Downloads\Distributed Data Processing Platform

start "Auth Service" cmd /k "cd backend\auth-service && mvn spring-boot:run"
start "Ride Service" cmd /k "cd backend\ride-service && mvn spring-boot:run"
start "Driver Service" cmd /k "cd backend\driver-service && mvn spring-boot:run"
start "Location Service" cmd /k "cd backend\location-service && mvn spring-boot:run"
start "Notification Service" cmd /k "cd backend\notification-service && mvn spring-boot:run"
start "ETA Service" cmd /k "cd backend\eta-service && mvn spring-boot:run"
start "Admin Frontend" cmd /k "cd frontend\admin-app && npm install && npm run dev"
start "Rider Frontend" cmd /k "cd frontend\rider-app && npm install && npm run dev"
start "Driver Frontend" cmd /k "cd frontend\driver-app && npm install && npm run dev"
```

Save as `start-stack.bat` in project root and run: `start-stack.bat`
