-- ============================================================================
-- DATABASE: location_db (Location & Telemetry Service)
-- Port: 8083 | Microservice: location-service
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";
CREATE SCHEMA IF NOT EXISTS location;

-- Bảng Vị trí Hiện tại Tài xế
CREATE TABLE IF NOT EXISTS location.driver_locations (
    driver_id UUID PRIMARY KEY,
    latitude NUMERIC(10, 7) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(10, 7) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    geom GEOMETRY(Point, 4326),
    accuracy_meters NUMERIC(6, 2),
    speed NUMERIC(5, 2),
    heading NUMERIC(5, 2),
    is_suspicious_gps BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Lịch sử Hành trình Telemetry
CREATE TABLE IF NOT EXISTS location.driver_telemetry_history (
    id BIGSERIAL PRIMARY KEY,
    driver_id UUID NOT NULL,
    geom GEOMETRY(Point, 4326) NOT NULL,
    speed NUMERIC(5, 2),
    heading NUMERIC(5, 2),
    accuracy_meters NUMERIC(6, 2),
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Chỉ mục không gian GiST cho truy vấn bán kính ST_DWithin cực nhanh
CREATE INDEX IF NOT EXISTS idx_driver_locations_geom ON location.driver_locations USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_driver_telemetry_geom ON location.driver_telemetry_history USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_driver_telemetry_time ON location.driver_telemetry_history (driver_id, recorded_at);
