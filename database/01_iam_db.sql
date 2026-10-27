-- ============================================================================
-- DATABASE: iam_db (Identity & Access Management Service)
-- Port: 8081 | Microservice: iam-service
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE SCHEMA IF NOT EXISTS iam;

-- Bảng Users
CREATE TABLE IF NOT EXISTS iam.users
(
    id                    UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    phone                 VARCHAR(20)  NOT NULL UNIQUE,
    email                 VARCHAR(255) UNIQUE,
    password_hash         VARCHAR(255) NOT NULL,
    full_name             VARCHAR(100) NOT NULL,
    avatar_url            VARCHAR(500),
    role                  VARCHAR(32)  NOT NULL    DEFAULT 'CUSTOMER',
    status                VARCHAR(32)  NOT NULL    DEFAULT 'ACTIVE',
    failed_login_attempts INT                      DEFAULT 0                 NOT NULL,
    locked_until          TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Refresh Tokens
CREATE TABLE IF NOT EXISTS iam.refresh_tokens
(
    id         UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    user_id    UUID                                               NOT NULL REFERENCES iam.users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255)                                       NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE                           NOT NULL,
    revoked    BOOLEAN                  DEFAULT FALSE             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Audit Logs
CREATE TABLE IF NOT EXISTS iam.audit_logs
(
    id         UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    user_id    UUID,
    action     VARCHAR(100)                                       NOT NULL,
    details    TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Seed Admin User with secure bcrypt hashed credentials
INSERT INTO iam.users (id, phone, email, password_hash, full_name, role, status)
VALUES ('00000000-0000-0000-0000-000000000001', '0901234567', 'admin@ridehailing.local',
        '$2a$12$e8YkR/q.zN.XQePZ3Fp4q.L0s6kO8R7aU5rP9mJ7mP5qQ4yW8z5W2', 'System Administrator', 'ADMIN', 'ACTIVE')
ON CONFLICT (phone) DO NOTHING;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_refresh_tokens_user ON iam.refresh_tokens (user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_user ON iam.audit_logs (user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_created ON iam.audit_logs (created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_role_status ON iam.users (role, status);
