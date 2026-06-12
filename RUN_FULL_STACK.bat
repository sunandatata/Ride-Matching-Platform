@echo off
REM =====================================================
REM Full Stack Startup Script for Ride-Sharing Platform
REM =====================================================
REM This script starts all 6 backend services + 3 frontend apps
REM Each service runs in its own window for easy monitoring

setlocal enabledelayedexpansion

cd /d "C:\Users\sunan\Downloads\Distributed Data Processing Platform"

echo.
echo =====================================================
echo   RIDE-SHARING PLATFORM - FULL STACK STARTUP
echo =====================================================
echo.

REM Check Java
echo [1/11] Verifying Java installation...
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java not found! Please run INSTALL_DEPS.ps1 first
    pause
    exit /b 1
)
echo OK - Java found
echo.

REM Check Maven
echo [2/11] Verifying Maven installation...
mvn -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Maven not found! Please run INSTALL_DEPS.ps1 first
    pause
    exit /b 1
)
echo OK - Maven found
echo.

REM Build Backend (One-time)
echo [3/11] Building Backend (this may take 2-3 minutes)...
cd backend
call mvn clean install -DskipTests
if errorlevel 1 (
    echo ERROR: Backend build failed!
    pause
    exit /b 1
)
cd ..
echo OK - Backend built successfully
echo.

REM Start services in new windows
echo [4/11] Starting Auth Service (Port 8001)...
start "Auth Service" cmd /k "cd backend\auth-service && mvn spring-boot:run"

timeout /t 2 /nobreak

echo [5/11] Starting Ride Service (Port 8002)...
start "Ride Service" cmd /k "cd backend\ride-service && mvn spring-boot:run"

timeout /t 2 /nobreak

echo [6/11] Starting Driver Service (Port 8003)...
start "Driver Service" cmd /k "cd backend\driver-service && mvn spring-boot:run"

timeout /t 2 /nobreak

echo [7/11] Starting Location Service (Port 8004)...
start "Location Service" cmd /k "cd backend\location-service && mvn spring-boot:run"

timeout /t 2 /nobreak

echo [8/11] Starting Notification Service (Port 8005)...
start "Notification Service" cmd /k "cd backend\notification-service && mvn spring-boot:run"

timeout /t 2 /nobreak

echo [9/11] Starting ETA Service (Port 8006)...
start "ETA Service" cmd /k "cd backend\eta-service && mvn spring-boot:run"

timeout /t 3 /nobreak

echo [10/11] Starting Frontend Apps...
echo   - Admin App (Port 5173)
echo   - Rider App (Port 5174)
echo   - Driver App (Port 5175)

cd frontend\admin-app
call npm install >nul 2>&1
start "Admin Frontend (5173)" cmd /k "npm run dev"

timeout /t 2 /nobreak

cd ..\rider-app
call npm install >nul 2>&1
start "Rider Frontend (5174)" cmd /k "npm run dev"

timeout /t 2 /nobreak

cd ..\driver-app
call npm install >nul 2>&1
start "Driver Frontend (5175)" cmd /k "npm run dev"

cd ..\..

echo.
echo =====================================================
echo   STARTUP COMPLETE!
echo =====================================================
echo.
echo Backend Services Running:
echo   ✓ Auth Service:         http://localhost:8001
echo   ✓ Ride Service:         http://localhost:8002
echo   ✓ Driver Service:       http://localhost:8003
echo   ✓ Location Service:     http://localhost:8004
echo   ✓ Notification Service: http://localhost:8005
echo   ✓ ETA Service:          http://localhost:8006
echo.
echo Frontend Apps:
echo   ✓ Admin Dashboard:  http://localhost:5173
echo   ✓ Rider App:        http://localhost:5174
echo   ✓ Driver App:       http://localhost:5175
echo.
echo All services will open in separate windows.
echo Check each window for startup messages.
echo.
echo To stop all services:
echo   1. Close each window, OR
echo   2. Run: taskkill /F /IM java.exe
echo.
echo =====================================================
echo.
pause
