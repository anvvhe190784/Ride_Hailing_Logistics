-- ============================================================================
-- DATABASE: payment_db (Billing, Payment & Immutable Wallet Ledger Service)
-- Port: 8086 | Microservice: payment-service
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE SCHEMA IF NOT EXISTS billing;
CREATE SCHEMA IF NOT EXISTS platform;

-- Bảng Ví Tài xế
CREATE TABLE IF NOT EXISTS billing.wallets
(
    id         UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    driver_id  UUID                                               NOT NULL UNIQUE,
    balance    NUMERIC(15, 2)           DEFAULT 0.00              NOT NULL CHECK (balance >= -100000.00),
    currency   VARCHAR(3)               DEFAULT 'VND'             NOT NULL,
    status     VARCHAR(32)              DEFAULT 'ACTIVE'          NOT NULL,
    version    BIGINT                   DEFAULT 0                 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Sổ cái Kế toán Bất biến (Immutable Financial Ledger Entries)
-- CẤM LỆNH UPDATE/DELETE: Mọi biến động đều sinh bút toán bù trừ
CREATE TABLE IF NOT EXISTS billing.wallet_entries
(
    id             UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    wallet_id      UUID                                               NOT NULL REFERENCES billing.wallets (id),
    trip_id        UUID,
    entry_type     VARCHAR(32)                                        NOT NULL,
    amount         NUMERIC(15, 2)                                     NOT NULL CHECK (amount > 0),
    balance_after  NUMERIC(15, 2)                                     NOT NULL,
    reference_type VARCHAR(32)              DEFAULT 'TRIP'            NOT NULL,
    reference_id   VARCHAR(64)              DEFAULT '',
    description    TEXT,
    status         VARCHAR(32)              DEFAULT 'POSTED'          NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Function & Trigger bảo vệ tính bất biến tuyệt đối của sổ cái tài chính
CREATE OR REPLACE FUNCTION billing.fn_prevent_wallet_entries_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Wallet entries are strictly immutable. UPDATE or DELETE operation is prohibited on billing.wallet_entries.';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_wallet_entries_immutable ON billing.wallet_entries;
CREATE TRIGGER trg_wallet_entries_immutable
BEFORE UPDATE OR DELETE ON billing.wallet_entries
FOR EACH ROW EXECUTE FUNCTION billing.fn_prevent_wallet_entries_mutation();

-- Bảng Giao dịch Thanh toán
CREATE TABLE IF NOT EXISTS billing.payments
(
    id              UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    trip_id         UUID                                               NOT NULL,
    customer_id     UUID                                               NOT NULL,
    amount          NUMERIC(15, 2)                                     NOT NULL CHECK (amount >= 0),
    currency        VARCHAR(3)               DEFAULT 'VND'             NOT NULL,
    provider        VARCHAR(32)                                        NOT NULL,
    status          VARCHAR(32)              DEFAULT 'PENDING'         NOT NULL,
    provider_ref    VARCHAR(128),
    idempotency_key VARCHAR(128)                                       NOT NULL UNIQUE,
    error_message   VARCHAR(255),
    paid_at         TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Hoàn tiền (Refunds)
CREATE TABLE IF NOT EXISTS billing.refunds
(
    id            UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    payment_id    UUID                                               NOT NULL REFERENCES billing.payments (id),
    trip_id       UUID                                               NOT NULL,
    amount        NUMERIC(12, 2)                                     NOT NULL CHECK (amount > 0),
    status        VARCHAR(20)              DEFAULT 'COMPLETED'       NOT NULL,
    refund_reason TEXT,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Kiểm soát Trùng lặp (Idempotency Records)
CREATE TABLE IF NOT EXISTS platform.idempotency_keys
(
    key_value        VARCHAR(255) PRIMARY KEY,
    response_payload JSONB,
    status_code      INT,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at       TIMESTAMP WITH TIME ZONE                           NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wallets_driver ON billing.wallets (driver_id);
CREATE INDEX IF NOT EXISTS idx_wallet_entries_wallet ON billing.wallet_entries (wallet_id);
CREATE INDEX IF NOT EXISTS idx_wallet_entries_ref ON billing.wallet_entries (reference_id);
CREATE INDEX IF NOT EXISTS idx_payments_trip ON billing.payments (trip_id);
CREATE INDEX IF NOT EXISTS idx_payments_customer ON billing.payments (customer_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON billing.payments (status);
CREATE INDEX IF NOT EXISTS idx_idempotency_expires ON platform.idempotency_keys (expires_at);
