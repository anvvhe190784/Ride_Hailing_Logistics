-- ============================================================================
-- DATABASE: pricing_db (Pricing & Surge Calculation Service)
-- Port: 8084 | Microservice: pricing-service
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";
CREATE SCHEMA IF NOT EXISTS pricing;

-- Bảng Quy tắc Giá Cước (Pricing Rules)
CREATE TABLE IF NOT EXISTS pricing.pricing_rules
(
    id               UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    service_type     VARCHAR(32)                                        NOT NULL,
    region_code      VARCHAR(32)                                        NOT NULL,
    base_fare        NUMERIC(12, 2)                                     NOT NULL,
    per_km_rate      NUMERIC(12, 2)                                     NOT NULL,
    per_minute_rate  NUMERIC(12, 2)                                     NOT NULL,
    minimum_fare     NUMERIC(12, 2)                                     NOT NULL,
    version          INT                      DEFAULT 1                 NOT NULL,
    effective_from   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    effective_to     TIMESTAMP WITH TIME ZONE,
    is_active        BOOLEAN                  DEFAULT TRUE              NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_pricing_service_region_version UNIQUE (service_type, region_code, version)
);

-- Bảng Báo giá Chuyến xe (Fare Quotes)
CREATE TABLE IF NOT EXISTS pricing.fare_quotes
(
    id                         UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    customer_id                UUID                                               NOT NULL,
    service_type               VARCHAR(32)                                        NOT NULL,
    pickup_point               GEOMETRY(Point, 4326)                              NOT NULL,
    pickup_address             TEXT                                               NOT NULL,
    dropoff_point              GEOMETRY(Point, 4326)                              NOT NULL,
    dropoff_address            TEXT                                               NOT NULL,
    estimated_distance_meters  INT                                                NOT NULL,
    estimated_duration_seconds INT                                                NOT NULL,
    base_price                 NUMERIC(12, 2)                                     NOT NULL,
    distance_price             NUMERIC(12, 2)                                     NOT NULL,
    duration_price             NUMERIC(12, 2)                                     NOT NULL,
    surge_multiplier           NUMERIC(4, 2)            DEFAULT 1.00              NOT NULL,
    total_fare                 NUMERIC(12, 2)                                     NOT NULL,
    currency                   VARCHAR(3)               DEFAULT 'VND'             NOT NULL,
    rule_version               INT                      DEFAULT 1                 NOT NULL,
    expires_at                 TIMESTAMP WITH TIME ZONE                           NOT NULL,
    is_used                    BOOLEAN                  DEFAULT FALSE             NOT NULL,
    created_at                 TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Seed Pricing Rules
INSERT INTO pricing.pricing_rules (id, service_type, region_code, base_fare, per_km_rate, per_minute_rate, minimum_fare, version, effective_from, is_active)
VALUES ('11111111-1111-1111-1111-111111111101', 'RIDE_MOTORBIKE', 'HAN', 12500.00, 4500.00, 350.00, 12500.00, 1, CURRENT_TIMESTAMP, TRUE),
       ('11111111-1111-1111-1111-111111111102', 'RIDE_MOTORBIKE', 'SGN', 12500.00, 4500.00, 350.00, 12500.00, 1, CURRENT_TIMESTAMP, TRUE),
       ('11111111-1111-1111-1111-111111111103', 'RIDE_CAR_4', 'HAN', 20000.00, 9500.00, 600.00, 20000.00, 1, CURRENT_TIMESTAMP, TRUE),
       ('11111111-1111-1111-1111-111111111104', 'RIDE_CAR_4', 'SGN', 20000.00, 9500.00, 600.00, 20000.00, 1, CURRENT_TIMESTAMP, TRUE),
       ('11111111-1111-1111-1111-111111111105', 'RIDE_CAR_7', 'HAN', 25000.00, 12000.00, 800.00, 25000.00, 1, CURRENT_TIMESTAMP, TRUE),
       ('11111111-1111-1111-1111-111111111106', 'RIDE_CAR_7', 'SGN', 25000.00, 12000.00, 800.00, 25000.00, 1, CURRENT_TIMESTAMP, TRUE),
       ('11111111-1111-1111-1111-111111111107', 'DELIVERY', 'HAN', 15000.00, 5000.00, 400.00, 15000.00, 1, CURRENT_TIMESTAMP, TRUE),
       ('11111111-1111-1111-1111-111111111108', 'DELIVERY', 'SGN', 15000.00, 5000.00, 400.00, 15000.00, 1, CURRENT_TIMESTAMP, TRUE)
ON CONFLICT (service_type, region_code, version) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_pricing_rules_lookup ON pricing.pricing_rules (service_type, region_code, is_active);
CREATE INDEX IF NOT EXISTS idx_fare_quotes_active ON pricing.fare_quotes (customer_id, expires_at) WHERE is_used = FALSE;
CREATE INDEX IF NOT EXISTS idx_fare_quotes_expires ON pricing.fare_quotes (expires_at);
