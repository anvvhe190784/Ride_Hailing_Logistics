-- ============================================================================
-- MIGRATION: 02_iam_and_driver.sql
-- PURPOSE: Identity, Driver Profiles, Vehicles and Driver Documents
-- ============================================================================

-- 1. IAM Users (FR-IAM-001 -> FR-IAM-011)
CREATE TABLE IF NOT EXISTS iam.users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(255),
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    avatar_url TEXT,
    role VARCHAR(32) NOT NULL CHECK (role IN ('CUSTOMER', 'DRIVER', 'REVIEWER', 'SUPPORT_STAFF', 'FINANCE_STAFF', 'ADMIN')),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PENDING_VERIFICATION')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_phone ON iam.users (phone);
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email ON iam.users (email) WHERE email IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_role_status ON iam.users (role, status);

-- 2. Driver Profiles (FR-DRV-001 -> FR-DRV-014)
CREATE TABLE IF NOT EXISTS driver.driver_profiles (
    driver_id UUID PRIMARY KEY REFERENCES iam.users(id) ON DELETE CASCADE,
    id_card_number VARCHAR(50),
    date_of_birth DATE,
    address TEXT,
    review_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' CHECK (review_status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    status_reason TEXT,
    reviewer_id UUID REFERENCES iam.users(id),
    reviewed_at TIMESTAMPTZ,
    availability_status VARCHAR(32) NOT NULL DEFAULT 'OFFLINE' CHECK (availability_status IN ('OFFLINE', 'AVAILABLE', 'OFFERED', 'BUSY')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_driver_id_card ON driver.driver_profiles (id_card_number) WHERE id_card_number IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_driver_avail_status ON driver.driver_profiles (availability_status, review_status);

-- 3. Vehicles (FR-DRV-007)
CREATE TABLE IF NOT EXISTS driver.vehicles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    driver_id UUID NOT NULL REFERENCES driver.driver_profiles(driver_id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL CHECK (type IN ('BIKE', 'CAR_4_SEAT', 'CAR_7_SEAT', 'DELIVERY_BIKE', 'TRUCK')),
    license_plate VARCHAR(32) NOT NULL,
    brand VARCHAR(50),
    model VARCHAR(50),
    color VARCHAR(30),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_vehicle_plate ON driver.vehicles (license_plate);
CREATE INDEX IF NOT EXISTS idx_vehicle_driver ON driver.vehicles (driver_id);

-- 4. Driver Documents (FR-DRV-001, FR-DRV-002)
CREATE TABLE IF NOT EXISTS driver.driver_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    driver_id UUID NOT NULL REFERENCES driver.driver_profiles(driver_id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL CHECK (type IN ('DRIVER_LICENSE', 'VEHICLE_REGISTRATION', 'CITIZEN_ID', 'VEHICLE_INSURANCE')),
    document_number VARCHAR(100),
    expiry_date DATE,
    file_url TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_driver_docs_driver ON driver.driver_documents (driver_id, type);
