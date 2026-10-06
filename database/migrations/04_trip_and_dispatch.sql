-- ============================================================================
-- MIGRATION: 04_trip_and_dispatch.sql
-- PURPOSE: Trip Lifecycle, Dispatch Offers, Stops, History and Concurrency Constraints
-- ============================================================================

-- 1. Trips Table (FR-TRIP-001 -> FR-TRIP-015, BR-002, CON-06)
CREATE TABLE IF NOT EXISTS trip.trips (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trip_code VARCHAR(32) NOT NULL UNIQUE,
    service_type VARCHAR(32) NOT NULL CHECK (service_type IN ('RIDE', 'DELIVERY')),
    customer_id UUID NOT NULL REFERENCES iam.users(id),
    driver_id UUID REFERENCES driver.driver_profiles(driver_id),
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED' CHECK (
        status IN ('CREATED', 'MATCHING', 'ACCEPTED', 'PICKING_UP', 'ARRIVED', 'IN_TRIP', 'COMPLETED', 'CANCELLED', 'NO_DRIVER')
    ),
    quote_id UUID NOT NULL REFERENCES pricing.fare_quotes(id),
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    quote_snapshot JSONB NOT NULL,
    delivery_package_snapshot JSONB,
    cancellation_reason TEXT,
    cancelled_by UUID REFERENCES iam.users(id),
    cancellation_fee NUMERIC(12, 2) DEFAULT 0.00 CHECK (cancellation_fee >= 0),
    final_fare NUMERIC(12, 2) CHECK (final_fare IS NULL OR final_fare >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    payment_method VARCHAR(32) NOT NULL CHECK (payment_method IN ('CASH', 'WALLET', 'ELECTRONIC_SANDBOX')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Partial Unique Index (BR-002, CON-06, NFR-REL-003):
-- Guarantees atomicity: A driver CAN NEVER have more than one active trip simultaneously!
CREATE UNIQUE INDEX IF NOT EXISTS uq_driver_single_active_trip 
ON trip.trips (driver_id) 
WHERE status IN ('ACCEPTED', 'PICKING_UP', 'ARRIVED', 'IN_TRIP');

CREATE INDEX IF NOT EXISTS idx_trips_customer_status ON trip.trips (customer_id, status);
CREATE INDEX IF NOT EXISTS idx_trips_created_at ON trip.trips (created_at DESC);

-- 2. Trip Stops (DR-004, FR-TRIP-004)
CREATE TABLE IF NOT EXISTS trip.trip_stops (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trip_id UUID NOT NULL REFERENCES trip.trips(id) ON DELETE CASCADE,
    stop_type VARCHAR(32) NOT NULL CHECK (stop_type IN ('PICKUP', 'DROPOFF')),
    stop_order INT NOT NULL DEFAULT 1,
    coordinates GEOMETRY(Point, 4326) NOT NULL,
    display_address TEXT NOT NULL,
    contact_name VARCHAR(100),
    contact_phone VARCHAR(20),
    arrived_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_trip_stops_trip ON trip.trip_stops (trip_id, stop_order);

-- 3. Driver Offers (FR-MAT-007 -> FR-MAT-015, UC-06)
CREATE TABLE IF NOT EXISTS trip.driver_offers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trip_id UUID NOT NULL REFERENCES trip.trips(id) ON DELETE CASCADE,
    driver_id UUID NOT NULL REFERENCES driver.driver_profiles(driver_id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
    estimated_pickup_distance_meters INT,
    offered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    CONSTRAINT uq_offer_trip_driver UNIQUE (trip_id, driver_id)
);

CREATE INDEX IF NOT EXISTS idx_driver_offers_driver_status ON trip.driver_offers (driver_id, status);
CREATE INDEX IF NOT EXISTS idx_driver_offers_expires ON trip.driver_offers (expires_at) WHERE status = 'PENDING';

-- 4. Trip Status History (FR-TRIP-010, DR-005 Immutable Audit)
CREATE TABLE IF NOT EXISTS trip.trip_status_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trip_id UUID NOT NULL REFERENCES trip.trips(id) ON DELETE CASCADE,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    actor_id UUID REFERENCES iam.users(id),
    actor_role VARCHAR(32),
    reason TEXT,
    metadata JSONB DEFAULT '{}'::jsonb,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trip_history_trip ON trip.trip_status_history (trip_id, recorded_at);
