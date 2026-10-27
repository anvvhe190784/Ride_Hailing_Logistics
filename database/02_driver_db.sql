-- ============================================================================
-- DATABASE: driver_db (Driver & Vehicle Management Service)
-- Port: 8082 | Microservice: driver-service
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE SCHEMA IF NOT EXISTS driver;

-- Bảng Hồ sơ Tài xế
CREATE TABLE IF NOT EXISTS driver.driver_profiles
(
    driver_id           UUID PRIMARY KEY,
    user_id             UUID,
    id_card_number      VARCHAR(50)                                        NOT NULL UNIQUE,
    date_of_birth       DATE                                               NOT NULL,
    address             VARCHAR(255)                                       NOT NULL,
    review_status       VARCHAR(32)              DEFAULT 'DRAFT'           NOT NULL,
    status_reason       VARCHAR(255),
    reviewer_id         UUID,
    reviewed_at         TIMESTAMP WITH TIME ZONE,
    availability_status VARCHAR(32)              DEFAULT 'OFFLINE'         NOT NULL,
    rating              NUMERIC(3, 2)            DEFAULT 5.00              NOT NULL,
    total_trips         INT                      DEFAULT 0                 NOT NULL,
    version             BIGINT                   DEFAULT 0                 NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Phương tiện
CREATE TABLE IF NOT EXISTS driver.vehicles
(
    id            UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    driver_id     UUID                                               NOT NULL REFERENCES driver.driver_profiles (driver_id) ON DELETE CASCADE,
    type          VARCHAR(32)                                        NOT NULL,
    license_plate VARCHAR(32)                                        NOT NULL UNIQUE,
    make          VARCHAR(50),
    brand         VARCHAR(50),
    model         VARCHAR(50),
    year          INT,
    color         VARCHAR(30),
    status        VARCHAR(32)              DEFAULT 'ACTIVE'          NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Giấy tờ & Tài liệu đính kèm
CREATE TABLE IF NOT EXISTS driver.driver_documents
(
    id              UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    driver_id       UUID                                               NOT NULL REFERENCES driver.driver_profiles (driver_id) ON DELETE CASCADE,
    document_type   VARCHAR(50)                                        NOT NULL,
    file_path       VARCHAR(500)                                       NOT NULL,
    mime_type       VARCHAR(100)                                       NOT NULL,
    file_size_bytes BIGINT,
    status          VARCHAR(20)              DEFAULT 'PENDING'         NOT NULL,
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

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_driver_profiles_status ON driver.driver_profiles (review_status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_driver_profiles_available ON driver.driver_profiles (availability_status) WHERE review_status = 'APPROVED';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_driver_vehicles_driver ON driver.vehicles (driver_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_driver_documents_driver ON driver.driver_documents (driver_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_driver_profiles_user_id ON driver.driver_profiles (user_id);
