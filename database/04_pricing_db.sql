-- ============================================================================
-- DATABASE: pricing_db (Pricing & Surge Calculation Service)
-- Port: 8084 | Microservice: pricing-service
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE SCHEMA IF NOT EXISTS pricing;

-- Bảng Quy tắc Giá Cước
CREATE TABLE IF NOT EXISTS pricing.pricing_rules
(
    id                        SERIAL PRIMARY KEY,
    service_type              VARCHAR(32)                                        NOT NULL,
    city_code                 VARCHAR(20)                                        NOT NULL,
    base_fare                 NUMERIC(12, 2)                                     NOT NULL,
    base_distance_km          NUMERIC(5, 2)            DEFAULT 1.00              NOT NULL,
    price_per_km              NUMERIC(12, 2)                                     NOT NULL,
    price_per_minute          NUMERIC(12, 2)           DEFAULT 500.00            NOT NULL,
    night_surcharge           NUMERIC(12, 2)           DEFAULT 0.00              NOT NULL,
    cancellation_fee          NUMERIC(12, 2)           DEFAULT 10000.00          NOT NULL,
    free_cancellation_minutes INT                      DEFAULT 3                 NOT NULL,
    is_active                 BOOLEAN                  DEFAULT TRUE              NOT NULL,
    created_at                TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_pricing_service_city UNIQUE (service_type, city_code)
);

-- Bảng Báo giá Chuyến xe (Fare Quotes)
CREATE TABLE IF NOT EXISTS pricing.fare_quotes
(
    id               UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    customer_id      UUID                                               NOT NULL,
    service_type     VARCHAR(32)                                        NOT NULL,
    distance_km      NUMERIC(6, 2)                                      NOT NULL,
    duration_minutes INT                                                NOT NULL,
    base_fare        NUMERIC(12, 2)                                     NOT NULL,
    distance_fare    NUMERIC(12, 2)                                     NOT NULL,
    duration_fare    NUMERIC(12, 2)                                     NOT NULL,
    surge_multiplier NUMERIC(3, 2)            DEFAULT 1.00              NOT NULL,
    surge_reason     VARCHAR(255),
    total_fare       NUMERIC(12, 2)                                     NOT NULL,
    expires_at       TIMESTAMP WITH TIME ZONE                           NOT NULL,
    is_used          BOOLEAN                  DEFAULT FALSE             NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Seed Pricing Rules
INSERT INTO pricing.pricing_rules (service_type, city_code, base_fare, price_per_km, price_per_minute)
VALUES ('RIDE_MOTORBIKE', 'HAN', 12500.00, 4500.00, 350.00),
       ('RIDE_MOTORBIKE', 'SGN', 12500.00, 4500.00, 350.00),
       ('RIDE_CAR_4', 'HAN', 20000.00, 9500.00, 600.00),
       ('RIDE_CAR_4', 'SGN', 20000.00, 9500.00, 600.00),
       ('RIDE_CAR_7', 'HAN', 25000.00, 12000.00, 800.00),
       ('RIDE_CAR_7', 'SGN', 25000.00, 12000.00, 800.00),
       ('DELIVERY', 'HAN', 15000.00, 5000.00, 400.00),
       ('DELIVERY', 'SGN', 15000.00, 5000.00, 400.00)
ON CONFLICT (service_type, city_code) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_fare_quotes_customer ON pricing.fare_quotes (customer_id);
CREATE INDEX IF NOT EXISTS idx_fare_quotes_expires ON pricing.fare_quotes (expires_at);
