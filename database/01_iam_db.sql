-- ============================================================================
-- DATABASE: iam_db (Identity & Access Management Service)
-- Port: 8081 | Microservice: iam-service
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE SCHEMA IF NOT EXISTS iam;

-- Bảng Roles
CREATE TABLE IF NOT EXISTS iam.roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Bảng Users
CREATE TABLE IF NOT EXISTS iam.users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone VARCHAR(20) NOT NULL UNIQUE,
    email VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    role VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Refresh Tokens
CREATE TABLE IF NOT EXISTS iam.refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES iam.users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Audit Logs
CREATE TABLE IF NOT EXISTS iam.audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID,
    action VARCHAR(100) NOT NULL,
    details TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Seed Data
INSERT INTO iam.roles (name, description) VALUES
('ROLE_CUSTOMER', 'Khách hàng sử dụng dịch vụ đặt xe và giao hàng'),
('ROLE_DRIVER', 'Tài xế nhận chuyến và giao vận'),
('ROLE_ADMIN', 'Quản trị viên toàn quyền hệ thống'),
('ROLE_REVIEWER', 'Kiểm duyệt viên hồ sơ tài xế'),
('ROLE_OPERATOR', 'Điều phối viên vận hành')
ON CONFLICT (name) DO NOTHING;

-- Seed Admin User (password: Admin@123)
INSERT INTO iam.users (id, phone, email, password_hash, full_name, role, status) VALUES
('00000000-0000-0000-0000-000000000001', '0901234567', 'admin@ridehailing.local', 
 '$2a$12$e8YkR/q.zN.XQePZ3Fp4q.L0s6kO8R7aU5rP9mJ7mP5qQ4yW8z5W2', 'System Administrator', 'ADMIN', 'ACTIVE')
ON CONFLICT (phone) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_users_phone ON iam.users(phone);
CREATE INDEX IF NOT EXISTS idx_users_email ON iam.users(email);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON iam.refresh_tokens(user_id);
