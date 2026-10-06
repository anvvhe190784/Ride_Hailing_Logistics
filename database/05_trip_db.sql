-- ============================================================================
-- DATABASE: trip_db (Trip Management & Dispatch Service)
-- Port: 8085 | Microservice: trip-service
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE SCHEMA IF NOT EXISTS trip;
CREATE SCHEMA IF NOT EXISTS platform;

-- Bảng Chuyến xe (Trips)
CREATE TABLE IF NOT EXISTS trip.trips (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID NOT NULL,
    driver_id UUID,
    quote_id UUID,
    service_type VARCHAR(32) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    fare_amount NUMERIC(12, 2) NOT NULL,
    cancellation_fee NUMERIC(12, 2) DEFAULT 0.00,
    pickup_address VARCHAR(255) NOT NULL,
    pickup_lat NUMERIC(10, 7) NOT NULL,
    pickup_lng NUMERIC(10, 7) NOT NULL,
    dropoff_address VARCHAR(255) NOT NULL,
    dropoff_lat NUMERIC(10, 7) NOT NULL,
    dropoff_lng NUMERIC(10, 7) NOT NULL,
    distance_km NUMERIC(6, 2) NOT NULL,
    duration_minutes INT,
    cancelled_by VARCHAR(20),
    cancellation_reason TEXT,
    rating INT CHECK (rating BETWEEN 1 AND 5),
    review_comment TEXT,
    accepted_at TIMESTAMP WITH TIME ZONE,
    arrived_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- PARTIAL UNIQUE INDEX: Đảm bảo 1 tài xế chỉ có tối đa 1 cuốc xe đang hoạt động
CREATE UNIQUE INDEX IF NOT EXISTS idx_trips_driver_active_unique 
ON trip.trips (driver_id) 
WHERE status IN ('ACCEPTED', 'ARRIVING', 'IN_PROGRESS');

-- Bảng Điểm dừng chuyến xe (Multi-stop)
CREATE TABLE IF NOT EXISTS trip.trip_stops (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trip_id UUID NOT NULL REFERENCES trip.trips(id) ON DELETE CASCADE,
    stop_sequence INT NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude NUMERIC(10, 7) NOT NULL,
    longitude NUMERIC(10, 7) NOT NULL,
    contact_name VARCHAR(100),
    contact_phone VARCHAR(20),
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Lời mời chuyến xe (Driver Offers)
CREATE TABLE IF NOT EXISTS trip.driver_offers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trip_id UUID NOT NULL REFERENCES trip.trips(id) ON DELETE CASCADE,
    driver_id UUID NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    sequence_number INT DEFAULT 1 NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Lịch sử chuyển đổi trạng thái (Audit History)
CREATE TABLE IF NOT EXISTS trip.trip_status_history (
    id BIGSERIAL PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trip.trips(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    actor_id UUID,
    actor_role VARCHAR(30),
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Transactional Outbox Events (Bảo đảm gửi tin nhắn Kafka At-least-once)
CREATE TABLE IF NOT EXISTS platform.outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    retry_count INT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_trips_customer ON trip.trips(customer_id);
CREATE INDEX IF NOT EXISTS idx_trips_status ON trip.trips(status);
CREATE INDEX IF NOT EXISTS idx_driver_offers_trip ON trip.driver_offers(trip_id);
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON platform.outbox_events(status) WHERE status = 'PENDING';
