-- ============================================================================
-- MIGRATION: 05_billing_and_wallet.sql
-- PURPOSE: Payments, Driver Wallets, Immutable Ledger and Refunds (FR-PAY, FR-WAL, BR-011)
-- ============================================================================

-- 1. Payments Table (FR-PAY-001 -> FR-PAY-008)
CREATE TABLE IF NOT EXISTS billing.payments
(
    id              UUID PRIMARY KEY        DEFAULT uuid_generate_v4(),
    trip_id         UUID           NOT NULL REFERENCES trip.trips (id),
    customer_id     UUID           NOT NULL REFERENCES iam.users (id),
    amount          NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    currency        VARCHAR(3)     NOT NULL DEFAULT 'VND',
    provider        VARCHAR(32)    NOT NULL CHECK (provider IN ('ELECTRONIC_SANDBOX', 'CASH', 'IN_APP_WALLET')),
    status          VARCHAR(32)    NOT NULL DEFAULT 'PENDING' CHECK (
        status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUND_PENDING', 'REFUNDED', 'PARTIALLY_REFUNDED')
        ),
    provider_ref    VARCHAR(128),
    idempotency_key VARCHAR(128)   NOT NULL UNIQUE,
    error_message   TEXT,
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payments_trip ON billing.payments (trip_id);
CREATE INDEX IF NOT EXISTS idx_payments_customer ON billing.payments (customer_id);

-- 2. Driver Wallets (FR-WAL-001 -> FR-WAL-010)
CREATE TABLE IF NOT EXISTS billing.wallets
(
    id         UUID PRIMARY KEY        DEFAULT uuid_generate_v4(),
    driver_id  UUID           NOT NULL UNIQUE REFERENCES driver.driver_profiles (driver_id) ON DELETE CASCADE,
    currency   VARCHAR(3)     NOT NULL DEFAULT 'VND',
    balance    NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    status     VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED', 'FROZEN')),
    created_at TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Immutable Financial Ledger: Wallet Entries (FR-WAL-002, BR-011, DR-005)
-- Balance is derived purely from valid immutable accounting entries!
CREATE TABLE IF NOT EXISTS billing.wallet_entries
(
    id             UUID PRIMARY KEY        DEFAULT uuid_generate_v4(),
    wallet_id      UUID           NOT NULL REFERENCES billing.wallets (id) ON DELETE CASCADE,
    entry_type     VARCHAR(32)    NOT NULL CHECK (
        entry_type IN
        ('TRIP_EARNING', 'PLATFORM_COMMISSION', 'TRIP_ADJUSTMENT', 'REFUND', 'PENALTY', 'TOPUP', 'WITHDRAWAL')
        ),
    amount         NUMERIC(12, 2) NOT NULL, -- Positive: Credit, Negative: Debit
    balance_after  NUMERIC(12, 2) NOT NULL,
    reference_type VARCHAR(32)    NOT NULL CHECK (reference_type IN ('TRIP', 'PAYMENT', 'MANUAL_ADJUSTMENT')),
    reference_id   VARCHAR(64)    NOT NULL,
    description    TEXT,
    status         VARCHAR(32)    NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED', 'VOID')),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wallet_entries_wallet_time ON billing.wallet_entries (wallet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wallet_entries_reference ON billing.wallet_entries (reference_type, reference_id);

-- 4. Refunds (FR-PAY-009 -> FR-PAY-013, BR-010)
CREATE TABLE IF NOT EXISTS billing.refunds
(
    id                  UUID PRIMARY KEY        DEFAULT uuid_generate_v4(),
    payment_id          UUID           NOT NULL REFERENCES billing.payments (id),
    requested_by        UUID           NOT NULL REFERENCES iam.users (id),
    amount              NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    reason              TEXT           NOT NULL,
    status              VARCHAR(32)    NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMPLETED', 'REJECTED')),
    provider_refund_ref VARCHAR(128),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_refunds_payment ON billing.refunds (payment_id);
