-- ============================================================================
-- DATABASE: location_db (Location & Telemetry Service)
-- Port: 8083 | Microservice: location-service
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";
CREATE SCHEMA IF NOT EXISTS location;

-- Bảng Vị trí Mới nhất của Tài xế (HOT table: High Write Frequency, fillfactor=70)
CREATE TABLE IF NOT EXISTS location.driver_latest_locations
(
    driver_id        UUID PRIMARY KEY,
    is_available     BOOLEAN                  DEFAULT TRUE              NOT NULL,
    sequence_num     BIGINT                   DEFAULT 1                 NOT NULL,
    device_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    server_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    location         GEOMETRY(Point, 4326)                              NOT NULL,
    latitude         NUMERIC(10, 7)                                     NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude        NUMERIC(10, 7)                                     NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    heading_degrees  DOUBLE PRECISION,
    speed_mps        DOUBLE PRECISION,
    accuracy_meters  DOUBLE PRECISION,
    is_suspicious_gps BOOLEAN                 DEFAULT FALSE             NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
) WITH (fillfactor = 70);

-- Bảng Lịch sử Hành trình Telemetry (Phân vùng theo tháng - Range Partitioning)
CREATE TABLE IF NOT EXISTS location.driver_telemetry_history
(
    id              BIGSERIAL,
    driver_id       UUID                                               NOT NULL,
    location        GEOMETRY(Point, 4326)                              NOT NULL,
    latitude        NUMERIC(10, 7),
    longitude       NUMERIC(10, 7),
    speed_mps       DOUBLE PRECISION,
    heading_degrees DOUBLE PRECISION,
    accuracy_meters DOUBLE PRECISION,
    recorded_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id, recorded_at)
) PARTITION BY RANGE (recorded_at);

-- Phân vùng dữ liệu mẫu (Monthly Partitions)
CREATE TABLE IF NOT EXISTS location.driver_telemetry_history_2026_10
    PARTITION OF location.driver_telemetry_history
    FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS location.driver_telemetry_history_2026_11
    PARTITION OF location.driver_telemetry_history
    FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS location.driver_telemetry_history_2026_12
    PARTITION OF location.driver_telemetry_history
    FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS location.driver_telemetry_history_default
    PARTITION OF location.driver_telemetry_history DEFAULT;

-- Chỉ mục không gian GiST và chỉ mục hỗ trợ truy vấn
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_driver_locations_geog ON location.driver_latest_locations USING GIST ((location::geography));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_driver_locations_available ON location.driver_latest_locations (is_available) WHERE is_available = TRUE;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_driver_locations_available_updated ON location.driver_latest_locations (is_available, updated_at);
CREATE INDEX IF NOT EXISTS idx_telemetry_driver_recorded ON location.driver_telemetry_history (driver_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_geog ON location.driver_telemetry_history USING GIST ((location::geography));
