# PowerShell Script: Start Full Stack (All Services)
# Run from: PowerShell or Command Prompt (admin not required)
# Command: powershell -ExecutionPolicy Bypass -File "C:\Users\sunan\Downloads\Distributed Data Processing Platform\START_ALL.ps1"

param(
    [switch]$SkipBuild = $false
)

$projectRoot = "C:\Users\sunan\Downloads\Distributed Data Processing Platform"
$backendRoot = "$projectRoot\backend"
$frontendRoot = "$projectRoot\frontend"

Write-Host "╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  RIDE-SHARING PLATFORM - FULL STACK STARTUP          ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Verify Java
Write-Host "[CHECK] Java Installation..." -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1
    Write-Host "✓ Java found" -ForegroundColor Green
    Write-Host "  $($javaVersion[0])" -ForegroundColor Gray
} catch {
    Write-Host "✗ Java NOT found! Run INSTALL_DEPS.ps1 first" -ForegroundColor Red
    exit 1
}

# Verify Maven
Write-Host "[CHECK] Maven Installation..." -ForegroundColor Yellow
try {
    $mvnVersion = mvn -version 2>&1 | Select-Object -First 1
    Write-Host "✓ Maven found" -ForegroundColor Green
    Write-Host "  $mvnVersion" -ForegroundColor Gray
} catch {
    Write-Host "✗ Maven NOT found! Run INSTALL_DEPS.ps1 first" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "PHASE 1: BUILD BACKEND" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

if (-not $SkipBuild) {
    Write-Host "[BUILD] Compiling all microservices (2-3 minutes)..." -ForegroundColor Yellow
    Push-Location $backendRoot
    mvn clean install -DskipTests -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "✗ Build failed!" -ForegroundColor Red
        Pop-Location
        exit 1
    }
    Pop-Location
    Write-Host "✓ Backend built successfully" -ForegroundColor Green
} else {
    Write-Host "[SKIP] Skipping build (using --SkipBuild)" -ForegroundColor Gray
}

Write-Host ""
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "PHASE 2: START BACKEND SERVICES" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

$services = @(
    @{name="Auth Service";port="8001";path="auth-service"},
    @{name="Ride Service";port="8002";path="ride-service"},
    @{name="Driver Service";port="8003";path="driver-service"},
    @{name="Location Service";port="8004";path="location-service"},
    @{name="Notification Service";port="8005";path="notification-service"},
    @{name="ETA Service";port="8006";path="eta-service"}
)

$serviceCount = 1
foreach ($service in $services) {
    Write-Host "[$serviceCount/6] Starting $($service.name) (Port $($service.port))..." -ForegroundColor Yellow

    $command = "cd '$backendRoot\$($service.path)' && mvn spring-boot:run"

    Start-Process -WindowStyle Normal -FilePath "powershell.exe" -ArgumentList "-NoExit -Command `"$command`"" -PassThru | Out-Null

    Start-Sleep -Milliseconds 1500
    $serviceCount++
}

Write-Host "✓ All backend services started" -ForegroundColor Green
Write-Host ""

Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "PHASE 3: START FRONTEND APPS" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

$frontendApps = @(
    @{name="Admin App";port="5173";path="admin-app"},
    @{name="Rider App";port="5174";path="rider-app"},
    @{name="Driver App";port="5175";path="driver-app"}
)

$appCount = 1
foreach ($app in $frontendApps) {
    Write-Host "[$appCount/3] Starting $($app.name) (Port $($app.port))..." -ForegroundColor Yellow

    $appPath = "$frontendRoot\$($app.path)"
    $command = "cd '$appPath' && npm install --silent && npm run dev"

    Start-Process -WindowStyle Normal -FilePath "powershell.exe" -ArgumentList "-NoExit -Command `"$command`"" -PassThru | Out-Null

    Start-Sleep -Milliseconds 1500
    $appCount++
}

Write-Host "✓ All frontend apps started" -ForegroundColor Green
Write-Host ""

Write-Host "╔════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║            ✓ FULL STACK IS NOW RUNNING                ║" -ForegroundColor Green
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""

Write-Host "BACKEND SERVICES:" -ForegroundColor Cyan
Write-Host "  • Auth Service:         http://localhost:8001" -ForegroundColor White
Write-Host "  • Ride Service:         http://localhost:8002" -ForegroundColor White
Write-Host "  • Driver Service:       http://localhost:8003" -ForegroundColor White
Write-Host "  • Location Service:     http://localhost:8004" -ForegroundColor White
Write-Host "  • Notification Service: http://localhost:8005" -ForegroundColor White
Write-Host "  • ETA Service:          http://localhost:8006" -ForegroundColor White
Write-Host ""

Write-Host "FRONTEND APPLICATIONS:" -ForegroundColor Cyan
Write-Host "  • Admin Dashboard:      http://localhost:5173" -ForegroundColor White
Write-Host "  • Rider App:            http://localhost:5174" -ForegroundColor White
Write-Host "  • Driver App:           http://localhost:5175" -ForegroundColor White
Write-Host ""

Write-Host "STATUS:" -ForegroundColor Cyan
Write-Host "  ✓ 6 backend services running" -ForegroundColor Green
Write-Host "  ✓ 3 frontend apps running" -ForegroundColor Green
Write-Host "  ✓ 9 terminal windows open (check taskbar)" -ForegroundColor Green
Write-Host ""

Write-Host "NEXT STEPS:" -ForegroundColor Yellow
Write-Host "  1. Wait 10 seconds for services to fully start" -ForegroundColor White
Write-Host "  2. Open: http://localhost:5173 (Admin Dashboard)" -ForegroundColor White
Write-Host "  3. Check each window for startup messages" -ForegroundColor White
Write-Host ""

Write-Host "TO STOP ALL SERVICES:" -ForegroundColor Yellow
Write-Host "  • Close each window, OR" -ForegroundColor White
Write-Host "  • Run: taskkill /F /IM java.exe /IM node.exe" -ForegroundColor White
Write-Host ""

Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Keep window open
Write-Host "Press Enter to close this window (services will continue running)..." -ForegroundColor Gray
Read-Host
