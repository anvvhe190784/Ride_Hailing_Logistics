#!/bin/bash
set -e

# ============================================================================
# Docker Entrypoint Script: Initializes 6 independent microservice databases
# Executed automatically by /docker-entrypoint-initdb.d/
# ============================================================================

echo "Initializing 6 Microservice Databases..."

databases=("iam_db" "driver_db" "location_db" "pricing_db" "trip_db" "payment_db")
scripts=("01_iam_db.sql" "02_driver_db.sql" "03_location_db.sql" "04_pricing_db.sql" "05_trip_db.sql" "06_payment_db.sql")

for i in "${!databases[@]}"; do
    db="${databases[$i]}"
    script="${scripts[$i]}"
    
    echo ">>> Creating database '$db'..."
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
        CREATE DATABASE $db;
        GRANT ALL PRIVILEGES ON DATABASE $db TO $POSTGRES_USER;
EOSQL

    if [ -f "/docker-entrypoint-initdb.d/init-scripts/$script" ]; then
        echo ">>> Executing '$script' into '$db'..."
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$db" -f "/docker-entrypoint-initdb.d/init-scripts/$script"
    fi
done

echo "All 6 microservice databases initialized successfully in PostgreSQL container!"
