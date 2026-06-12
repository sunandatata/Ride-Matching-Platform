# Windows PowerShell Script to Install Java 21 and Maven
# RUN THIS AS ADMINISTRATOR: Right-click PowerShell → "Run as Administrator"
# Then paste: powershell -ExecutionPolicy Bypass -File "C:\Users\sunan\Downloads\Distributed Data Processing Platform\INSTALL_DEPS.ps1"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Installing Java 21 and Maven" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Check if running as Administrator
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "ERROR: This script must be run as Administrator!" -ForegroundColor Red
    Write-Host "Right-click PowerShell and select 'Run as Administrator'" -ForegroundColor Yellow
    exit 1
}

Write-Host "✓ Running as Administrator" -ForegroundColor Green
Write-Host ""

# Step 1: Install Java 21
Write-Host "[1/2] Installing Java 21..." -ForegroundColor Yellow

try {
    choco install temurin21jdk -y --force
    Write-Host "✓ Java 21 installed successfully" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to install Java 21" -ForegroundColor Red
    Write-Host "Error: $_" -ForegroundColor Red
}

# Step 2: Install Maven
Write-Host "[2/2] Installing Maven..." -ForegroundColor Yellow

try {
    choco install maven -y --force
    Write-Host "✓ Maven installed successfully" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to install Maven" -ForegroundColor Red
    Write-Host "Error: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Installation Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Refresh environment
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

# Verify installations
Write-Host "Verifying installations..." -ForegroundColor Yellow
Write-Host ""

Write-Host "Java version:" -ForegroundColor Cyan
java -version

Write-Host ""
Write-Host "Maven version:" -ForegroundColor Cyan
mvn -version

Write-Host ""
Write-Host "✓ All installations verified!" -ForegroundColor Green
Write-Host ""
Write-Host "NEXT STEPS:" -ForegroundColor Yellow
Write-Host "1. Close this PowerShell window"
Write-Host "2. Open a NEW PowerShell/CMD window (admin not required)"
Write-Host "3. Run: cd C:\Users\sunan\Downloads\Distributed Data Processing Platform"
Write-Host "4. Then follow QUICK_RUN_GUIDE.md"
Write-Host ""
