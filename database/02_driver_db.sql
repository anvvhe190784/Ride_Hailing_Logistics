-- ============================================================================
-- DATABASE: driver_db (Driver & Vehicle Management Service)
-- Port: 8082 | Microservice: driver-service
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE SCHEMA IF NOT EXISTS driver;

-- Bảng Hồ sơ Tài xế
CREATE TABLE IF NOT EXISTS driver.driver_profiles
(
    id                  UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    user_id             UUID                                               NOT NULL UNIQUE,
    license_number      VARCHAR(50)                                        NOT NULL UNIQUE,
    national_id         VARCHAR(50)                                        NOT NULL UNIQUE,
    status              VARCHAR(30)                                        NOT NULL DEFAULT 'DRAFT',
    availability_status VARCHAR(20)                                        NOT NULL DEFAULT 'OFFLINE',
    rating              NUMERIC(3, 2)            DEFAULT 5.00              NOT NULL,
    total_trips         INT                      DEFAULT 0                 NOT NULL,
    rejection_reason    TEXT,
    reviewed_by         UUID,
    reviewed_at         TIMESTAMP WITH TIME ZONE,
    last_online_at      TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Phương tiện
CREATE TABLE IF NOT EXISTS driver.vehicles
(
    id            UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    driver_id     UUID                                               NOT NULL REFERENCES driver.driver_profiles (id) ON DELETE CASCADE,
    vehicle_type  VARCHAR(32)                                        NOT NULL,
    license_plate VARCHAR(20)                                        NOT NULL UNIQUE,
    brand         VARCHAR(50)                                        NOT NULL,
    model         VARCHAR(50)                                        NOT NULL,
    color         VARCHAR(30),
    year          INT,
    status        VARCHAR(20)                                        NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Giấy tờ & Tài liệu đính kèm
CREATE TABLE IF NOT EXISTS driver.driver_documents
(
    id              UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    driver_id       UUID         NOT NULL REFERENCES driver.driver_profiles (id) ON DELETE CASCADE,
    document_type   VARCHAR(50)  NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT,
    status          VARCHAR(20)  NOT NULL    DEFAULT 'PENDING',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Audit Log Tài xế
CREATE TABLE IF NOT EXISTS driver.driver_audit_logs
(
    id          UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    driver_id   UUID                                               NOT NULL,
    action      VARCHAR(100)                                       NOT NULL,
    reviewer_id UUID,
    notes       TEXT,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_driver_profiles_user ON driver.driver_profiles (user_id);
CREATE INDEX IF NOT EXISTS idx_driver_profiles_status ON driver.driver_profiles (status);
CREATE INDEX IF NOT EXISTS idx_driver_vehicles_driver ON driver.vehicles (driver_id);
