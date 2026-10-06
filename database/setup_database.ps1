# PowerShell Script to setup 6 independent databases on PostgreSQL local
# Database per Service Pattern: iam_db, driver_db, location_db, pricing_db, trip_db, payment_db

$env:PGPASSWORD = 'admin'
$PSQL = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
$HOST_NAME = "127.0.0.1"
$PORT = "5432"
$USER = "postgres"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "INITIALIZING 6 MICROSERVICE DATABASES (PostgreSQL 18)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$databases = @(
    @{ Name = "iam_db"; Script = "01_iam_db.sql"; Service = "iam-service (8081)" },
    @{ Name = "driver_db"; Script = "02_driver_db.sql"; Service = "driver-service (8082)" },
    @{ Name = "location_db"; Script = "03_location_db.sql"; Service = "location-service (8083)" },
    @{ Name = "pricing_db"; Script = "04_pricing_db.sql"; Service = "pricing-service (8084)" },
    @{ Name = "trip_db"; Script = "05_trip_db.sql"; Service = "trip-service (8085)" },
    @{ Name = "payment_db"; Script = "06_payment_db.sql"; Service = "payment-service (8086)" }
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

foreach ($db in $databases) {
    Write-Host "`n>>> Processing Database: $($db.Name) for $($db.Service)..." -ForegroundColor Yellow
    
    # 1. Create Database if not exists
    $checkDbSql = "SELECT 1 FROM pg_database WHERE datname = '$($db.Name)';"
    $exists = & $PSQL -h $HOST_NAME -p $PORT -U $USER -d postgres -t -c $checkDbSql
    
    if (-not $exists -or $exists.Trim() -ne "1") {
        Write-Host "Creating database '$($db.Name)'..." -ForegroundColor Green
        & $PSQL -h $HOST_NAME -p $PORT -U $USER -d postgres -c "CREATE DATABASE $($db.Name);"
    } else {
        Write-Host "Database '$($db.Name)' already exists." -ForegroundColor Gray
    }
    
    # 2. Run DDL migration script into the database
    $scriptPath = Join-Path $scriptDir $db.Script
    if (Test-Path $scriptPath) {
        Write-Host "Executing DDL script '$($db.Script)' into '$($db.Name)'..." -ForegroundColor Green
        & $PSQL -h $HOST_NAME -p $PORT -U $USER -d $db.Name -f $scriptPath
    } else {
        Write-Warning "Script not found: $scriptPath"
    }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "ALL 6 DATABASES INITIALIZED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
