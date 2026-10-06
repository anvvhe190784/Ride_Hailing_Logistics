# ============================================================================
# SCRIPT: setup_database.ps1
# PURPOSE: Executes database creation, migrations and verification tests
# ============================================================================

$ErrorActionPreference = "Stop"

$psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
$env:PGPASSWORD = "admin"
$hostName = "127.0.0.1"
$port = "5432"
$user = "postgres"
$dbName = "ride_hailing_db"
$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "==> Step 1: Initializing Database '$dbName'..." -ForegroundColor Cyan
& $psql -U $user -h $hostName -p $port -d postgres -f "$baseDir\init_db.sql"

$migrations = @(
    "01_extensions_and_schemas.sql",
    "02_iam_and_driver.sql",
    "03_location_and_pricing.sql",
    "04_trip_and_dispatch.sql",
    "05_billing_and_wallet.sql",
    "06_platform_outbox.sql"
)

Write-Host "`n==> Step 2: Applying Migrations..." -ForegroundColor Cyan
foreach ($mig in $migrations) {
    Write-Host "   -> Running $mig" -ForegroundColor Yellow
    & $psql -U $user -h $hostName -p $port -d $dbName -f "$baseDir\migrations\$mig"
}

Write-Host "`n==> Step 3: Running Automated Verification Tests..." -ForegroundColor Cyan
& $psql -U $user -h $hostName -p $port -d $dbName -f "$baseDir\tests\verify_db.sql"

Write-Host "`n==> Setup and Verification Finished Successfully!" -ForegroundColor Green
