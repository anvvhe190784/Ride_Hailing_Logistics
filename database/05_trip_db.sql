-- ============================================================================
-- DATABASE: trip_db (Trip Management & Dispatch Service)
-- Port: 8085 | Microservice: trip-service
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE SCHEMA IF NOT EXISTS trip;
CREATE SCHEMA IF NOT EXISTS platform;

-- Bảng Chuyến xe (Trips)
CREATE TABLE IF NOT EXISTS trip.trips
(
    id                        UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    trip_code                 VARCHAR(32)                                        NOT NULL UNIQUE,
    customer_id               UUID                                               NOT NULL,
    driver_id                 UUID,
    quote_id                  UUID                                               NOT NULL,
    service_type              VARCHAR(32)                                        NOT NULL,
    status                    VARCHAR(32)              DEFAULT 'CREATED'         NOT NULL,
    idempotency_key           VARCHAR(128)                                       NOT NULL UNIQUE,
    quote_snapshot            JSONB                    DEFAULT '{}'              NOT NULL,
    delivery_package_snapshot JSONB,
    sender_name               VARCHAR(100),
    sender_phone              VARCHAR(20),
    recipient_name            VARCHAR(100),
    recipient_phone           VARCHAR(20),
    package_description       VARCHAR(255),
    final_fare                NUMERIC(12, 2),
    currency                  VARCHAR(3)               DEFAULT 'VND'             NOT NULL,
    payment_method            VARCHAR(32)              DEFAULT 'CASH'            NOT NULL,
    cancellation_fee          NUMERIC(12, 2)           DEFAULT 0.00,
    cancellation_reason       TEXT,
    cancelled_by              UUID,
    version                   BIGINT                   DEFAULT 0                 NOT NULL,
    requested_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    accepted_at               TIMESTAMP WITH TIME ZONE,
    arrived_at                TIMESTAMP WITH TIME ZONE,
    in_trip_at                TIMESTAMP WITH TIME ZONE,
    started_at                TIMESTAMP WITH TIME ZONE,
    completed_at              TIMESTAMP WITH TIME ZONE,
    cancelled_at              TIMESTAMP WITH TIME ZONE,
    created_at                TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- PARTIAL UNIQUE INDEXES: Chống race condition gán cuốc kép
CREATE UNIQUE INDEX IF NOT EXISTS idx_trips_driver_active_unique
    ON trip.trips (driver_id)
    WHERE status IN ('ACCEPTED', 'PICKING_UP', 'ARRIVED', 'IN_TRIP');

CREATE UNIQUE INDEX IF NOT EXISTS idx_trips_customer_active_unique
    ON trip.trips (customer_id)
    WHERE status IN ('MATCHING', 'ACCEPTED', 'PICKING_UP', 'ARRIVED', 'IN_TRIP');

-- Bảng Lời mời chuyến xe (Driver Offers)
CREATE TABLE IF NOT EXISTS trip.driver_offers
(
    id                               UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    trip_id                          UUID                                               NOT NULL REFERENCES trip.trips (id) ON DELETE CASCADE,
    driver_id                        UUID                                               NOT NULL,
    status                           VARCHAR(32)              DEFAULT 'PENDING'         NOT NULL,
    estimated_pickup_distance_meters INT,
    offered_at                       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at                       TIMESTAMP WITH TIME ZONE                           NOT NULL,
    responded_at                     TIMESTAMP WITH TIME ZONE,
    created_at                       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_driver_offers_trip_driver UNIQUE (trip_id, driver_id)
);

-- Bảng Điểm dừng chuyến xe (Multi-stop)
CREATE TABLE IF NOT EXISTS trip.trip_stops
(
    id            UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    trip_id       UUID                                               NOT NULL REFERENCES trip.trips (id) ON DELETE CASCADE,
    stop_sequence INT                                                NOT NULL,
    address       VARCHAR(255)                                       NOT NULL,
    latitude      NUMERIC(10, 7)                                     NOT NULL,
    longitude     NUMERIC(10, 7)                                     NOT NULL,
    contact_name  VARCHAR(100),
    contact_phone VARCHAR(20),
    status        VARCHAR(20)              DEFAULT 'PENDING'         NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Lịch sử chuyển đổi trạng thái (Audit History)
CREATE TABLE IF NOT EXISTS trip.trip_status_history
(
    id          BIGSERIAL PRIMARY KEY,
    trip_id     UUID                                               NOT NULL REFERENCES trip.trips (id) ON DELETE CASCADE,
    from_status VARCHAR(32),
    to_status   VARCHAR(32)                                        NOT NULL,
    actor_id    UUID,
    actor_role  VARCHAR(32),
    reason      TEXT,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Transactional Outbox Events (Bảo đảm gửi tin nhắn Kafka At-least-once)
CREATE TABLE IF NOT EXISTS platform.outbox_events
(
    id             UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    aggregate_type VARCHAR(50)                                        NOT NULL,
    aggregate_id   VARCHAR(100)                                       NOT NULL,
    event_type     VARCHAR(100)                                       NOT NULL,
    payload        JSONB                                              NOT NULL,
    status         VARCHAR(20)              DEFAULT 'PENDING'         NOT NULL,
    retry_count    INT                      DEFAULT 0                 NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed_at   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_trips_customer ON trip.trips (customer_id);
CREATE INDEX IF NOT EXISTS idx_trips_status ON trip.trips (status);
CREATE INDEX IF NOT EXISTS idx_trips_driver_history ON trip.trips (driver_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_trips_quote_id ON trip.trips (quote_id);
CREATE INDEX IF NOT EXISTS idx_driver_offers_trip ON trip.driver_offers (trip_id);
CREATE INDEX IF NOT EXISTS idx_driver_offers_driver_status ON trip.driver_offers (driver_id, status);
CREATE INDEX IF NOT EXISTS idx_trip_stops_trip_id ON trip.trip_stops (trip_id);
CREATE INDEX IF NOT EXISTS idx_outbox_pending_created ON platform.outbox_events (created_at ASC) WHERE status = 'PENDING';
