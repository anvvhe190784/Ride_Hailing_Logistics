-- ============================================================================
-- MIGRATION: 03_location_and_pricing.sql
-- PURPOSE: Location Telemetry with Spatial GiST index and Pricing Engine
-- ============================================================================

-- 1. Latest Driver Locations (FR-LOC-001 -> FR-LOC-014, CON-04, DR-GEO-001)
CREATE TABLE IF NOT EXISTS location.driver_latest_locations
(
    driver_id        UUID PRIMARY KEY REFERENCES driver.driver_profiles (driver_id) ON DELETE CASCADE,
    location         GEOMETRY(Point, 4326) NOT NULL,
    latitude         NUMERIC(10, 7)        NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude        NUMERIC(10, 7)        NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    accuracy_meters  NUMERIC(6, 2),
    heading_degrees  NUMERIC(5, 2),
    speed_mps        NUMERIC(6, 2),
    sequence_num     BIGINT                NOT NULL DEFAULT 1,
    device_timestamp TIMESTAMPTZ           NOT NULL,
    server_timestamp TIMESTAMPTZ           NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- PostGIS GiST index for ultra-fast spatial search (NFR-PERF-003 < 20ms)
CREATE INDEX IF NOT EXISTS idx_driver_latest_location_gist ON location.driver_latest_locations USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_driver_location_server_time ON location.driver_latest_locations (server_timestamp);

-- 2. Driver Telemetry History (Audit / Replay / Analytics per DR-GEO-004)
CREATE TABLE IF NOT EXISTS location.driver_telemetry_history
(
    id               BIGSERIAL PRIMARY KEY,
    driver_id        UUID                  NOT NULL REFERENCES driver.driver_profiles (driver_id) ON DELETE CASCADE,
    location         GEOMETRY(Point, 4326) NOT NULL,
    latitude         NUMERIC(10, 7)        NOT NULL,
    longitude        NUMERIC(10, 7)        NOT NULL,
    speed_mps        NUMERIC(6, 2),
    accuracy_meters  NUMERIC(6, 2),
    device_timestamp TIMESTAMPTZ           NOT NULL,
    recorded_at      TIMESTAMPTZ           NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_telemetry_history_driver_time ON location.driver_telemetry_history (driver_id, recorded_at DESC);

-- 3. Pricing Rules (FR-PRI-001 -> FR-PRI-012)
CREATE TABLE IF NOT EXISTS pricing.pricing_rules
(
    id              UUID PRIMARY KEY        DEFAULT uuid_generate_v4(),
    service_type    VARCHAR(32)    NOT NULL CHECK (service_type IN ('RIDE', 'DELIVERY')),
    region_code     VARCHAR(32)    NOT NULL,
    base_fare       NUMERIC(12, 2) NOT NULL CHECK (base_fare >= 0),
    per_km_rate     NUMERIC(12, 2) NOT NULL CHECK (per_km_rate >= 0),
    per_minute_rate NUMERIC(12, 2) NOT NULL CHECK (per_minute_rate >= 0),
    minimum_fare    NUMERIC(12, 2) NOT NULL CHECK (minimum_fare >= 0),
    extra_fees      JSONB                   DEFAULT '{}'::jsonb,
    version         INT            NOT NULL DEFAULT 1,
    effective_from  TIMESTAMPTZ    NOT NULL,
    effective_to    TIMESTAMPTZ,
    is_active       BOOLEAN        NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pricing_rules_active ON pricing.pricing_rules (service_type, region_code, is_active);

-- 4. Fare Quotes (FR-PRI-013 -> FR-PRI-018)
CREATE TABLE IF NOT EXISTS pricing.fare_quotes
(
    id                         UUID PRIMARY KEY               DEFAULT uuid_generate_v4(),
    customer_id                UUID                  NOT NULL REFERENCES iam.users (id),
    service_type               VARCHAR(32)           NOT NULL CHECK (service_type IN ('RIDE', 'DELIVERY')),
    pickup_point               GEOMETRY(Point, 4326) NOT NULL,
    pickup_address             TEXT                  NOT NULL,
    dropoff_point              GEOMETRY(Point, 4326) NOT NULL,
    dropoff_address            TEXT                  NOT NULL,
    estimated_distance_meters  INT                   NOT NULL CHECK (estimated_distance_meters > 0),
    estimated_duration_seconds INT                   NOT NULL CHECK (estimated_duration_seconds > 0),
    base_price                 NUMERIC(12, 2)        NOT NULL CHECK (base_price >= 0),
    distance_price             NUMERIC(12, 2)        NOT NULL CHECK (distance_price >= 0),
    duration_price             NUMERIC(12, 2)        NOT NULL CHECK (duration_price >= 0),
    surge_multiplier           NUMERIC(4, 2)         NOT NULL DEFAULT 1.00 CHECK (surge_multiplier >= 1.00),
    total_fare                 NUMERIC(12, 2)        NOT NULL CHECK (total_fare >= 0),
    currency                   VARCHAR(3)            NOT NULL DEFAULT 'VND',
    rule_version               INT                   NOT NULL,
    expires_at                 TIMESTAMPTZ           NOT NULL,
    created_at                 TIMESTAMPTZ           NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fare_quotes_customer_exp ON pricing.fare_quotes (customer_id, expires_at);
