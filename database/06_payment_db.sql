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
    id          UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    driver_id   UUID                                               NOT NULL UNIQUE,
    balance     NUMERIC(15, 2)           DEFAULT 0.00              NOT NULL CHECK (balance >= 0),
    currency    VARCHAR(10)              DEFAULT 'VND'             NOT NULL,
    is_locked   BOOLEAN                  DEFAULT FALSE             NOT NULL,
    lock_reason VARCHAR(255),
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Sổ cái Kế toán Bất biến (Immutable Financial Ledger Entries)
-- CẤM LỆNH UPDATE/DELETE: Mọi biến động đều sinh bút toán bù trừ
CREATE TABLE IF NOT EXISTS billing.wallet_entries
(
    id            UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    wallet_id     UUID                                               NOT NULL REFERENCES billing.wallets (id),
    entry_type    VARCHAR(20)                                        NOT NULL, -- CREDIT, DEBIT, TOPUP, WITHDRAWAL, COMMISSION, TIP, REFUND
    amount        NUMERIC(15, 2)                                     NOT NULL CHECK (amount > 0),
    balance_after NUMERIC(15, 2)                                     NOT NULL,
    reference_id  VARCHAR(100),                                                -- trip_id hoặc payment_id
    description   TEXT,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Bảng Giao dịch Thanh toán
CREATE TABLE IF NOT EXISTS billing.payments
(
    id                    UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    trip_id               UUID                                               NOT NULL UNIQUE,
    customer_id           UUID                                               NOT NULL,
    driver_id             UUID,
    payment_method        VARCHAR(30)                                        NOT NULL, -- CASH, WALLET, CARD, VNPAY, MOMO
    amount                NUMERIC(12, 2)                                     NOT NULL CHECK (amount >= 0),
    tip_amount            NUMERIC(12, 2)           DEFAULT 0.00,
    status                VARCHAR(20)              DEFAULT 'PENDING'         NOT NULL, -- PENDING, SUCCESS, FAILED, REFUNDED
    transaction_reference VARCHAR(100),
    failure_reason        TEXT,
    created_at            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
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

CREATE INDEX IF NOT EXISTS idx_wallet_entries_wallet ON billing.wallet_entries (wallet_id);
CREATE INDEX IF NOT EXISTS idx_wallet_entries_ref ON billing.wallet_entries (reference_id);
CREATE INDEX IF NOT EXISTS idx_payments_customer ON billing.payments (customer_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON billing.payments (status);
CREATE INDEX IF NOT EXISTS idx_idempotency_expires ON platform.idempotency_keys (expires_at);
