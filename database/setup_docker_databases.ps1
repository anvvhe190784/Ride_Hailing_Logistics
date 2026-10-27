# ============================================================================
# AUTOMATED DATABASE SETUP & SEED FOR DOCKER POSTGRESQL (PostGIS 16)
# Initializes 6 microservices databases and seeds all roles & mock data
# ============================================================================

$CONTAINER_NAME = "ride_hailing_postgres"
$POSTGRES_USER = "postgres"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "INITIALIZING 6 DATABASES IN DOCKER: $CONTAINER_NAME" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Verify Docker container is running
$containerStatus = docker inspect -f '{{.State.Running}}' $CONTAINER_NAME 2>$null
if ($containerStatus -ne "true") {
    Write-Host "Starting container '$CONTAINER_NAME'..." -ForegroundColor Yellow
    docker start $CONTAINER_NAME
    Start-Sleep -Seconds 3
}

$databases = @(
    @{ Name = "iam_db"; Script = "01_iam_db.sql"; Service = "iam-service (:8081)" },
    @{ Name = "driver_db"; Script = "02_driver_db.sql"; Service = "driver-service (:8082)" },
    @{ Name = "location_db"; Script = "03_location_db.sql"; Service = "location-service (:8083)" },
    @{ Name = "pricing_db"; Script = "04_pricing_db.sql"; Service = "pricing-service (:8084)" },
    @{ Name = "trip_db"; Script = "05_trip_db.sql"; Service = "trip-service (:8085)" },
    @{ Name = "payment_db"; Script = "06_payment_db.sql"; Service = "payment-service (:8086)" }
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# 2. Create Databases and Run DDL
foreach ($db in $databases) {
    Write-Host "`n>>> Processing Database: $($db.Name) for $($db.Service)..." -ForegroundColor Yellow
    
    # Check if DB exists
    $exists = docker exec -i $CONTAINER_NAME psql -U $POSTGRES_USER -d postgres -t -c "SELECT 1 FROM pg_database WHERE datname = '$($db.Name)';" 2>$null
    if (-not $exists -or $exists.Trim() -ne "1") {
        Write-Host "Creating database '$($db.Name)'..." -ForegroundColor Green
        docker exec -i $CONTAINER_NAME psql -U $POSTGRES_USER -d postgres -c "CREATE DATABASE $($db.Name);"
    } else {
        Write-Host "Database '$($db.Name)' already exists." -ForegroundColor Gray
    }

    # Execute DDL Script
    $ddlFile = Join-Path $scriptDir $db.Script
    if (Test-Path $ddlFile) {
        Write-Host "Applying DDL script '$($db.Script)'..." -ForegroundColor Green
        Get-Content $ddlFile -Raw | docker exec -i $CONTAINER_NAME psql -U $POSTGRES_USER -d $db.Name
    } else {
        Write-Warning "DDL script not found: $ddlFile"
    }
}

# 3. Apply Seed Data
$seedFile = Join-Path $scriptDir "07_seed_data.sql"
if (Test-Path $seedFile) {
    Write-Host "`n==========================================================" -ForegroundColor Cyan
    Write-Host "APPLYING SEED DATA ACROSS ALL 6 DATABASES" -ForegroundColor Cyan
    Write-Host "==========================================================" -ForegroundColor Cyan
    Get-Content $seedFile -Raw | docker exec -i $CONTAINER_NAME psql -U $POSTGRES_USER -d postgres
    Write-Host "Seed data applied successfully!" -ForegroundColor Green
} else {
    Write-Warning "Seed file not found: $seedFile"
}

# 4. Verify Record Counts
Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "VERIFICATION: RECORD COUNTS IN DOCKER POSTGRES" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

docker exec -i $CONTAINER_NAME psql -U $POSTGRES_USER -d iam_db -c "SELECT role, count(*) FROM iam.users GROUP BY role;"
docker exec -i $CONTAINER_NAME psql -U $POSTGRES_USER -d driver_db -c "SELECT review_status, count(*) FROM driver.driver_profiles GROUP BY review_status;"
docker exec -i $CONTAINER_NAME psql -U $POSTGRES_USER -d pricing_db -c "SELECT service_type, region_code, base_fare FROM pricing.pricing_rules;"
docker exec -i $CONTAINER_NAME psql -U $POSTGRES_USER -d payment_db -c "SELECT id, driver_id, balance, currency FROM billing.wallets;"

Write-Host "`nALL 6 MICROSERVICE DATABASES ARE READY AND SEEDED IN DOCKER!" -ForegroundColor Green
