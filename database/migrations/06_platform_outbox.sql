-- ============================================================================
-- MIGRATION: 06_platform_outbox.sql
-- PURPOSE: Transactional Outbox, Immutable Audit Records and Idempotency Records
-- ============================================================================

-- 1. Transactional Outbox Pattern (FR-EVT-001 -> FR-EVT-008, COM-009)
CREATE TABLE IF NOT EXISTS platform.outbox_events
(
    id             UUID PRIMARY KEY      DEFAULT uuid_generate_v4(),
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   VARCHAR(64)  NOT NULL,
    event_type     VARCHAR(128) NOT NULL,
    event_version  INT          NOT NULL DEFAULT 1,
    payload        JSONB        NOT NULL,
    status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    correlation_id VARCHAR(128) NOT NULL,
    retry_count    INT          NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_pending
    ON platform.outbox_events (status, created_at)
    WHERE status = 'PENDING';

-- 2. Audit Records (FR-ADM-004 -> FR-ADM-006, BR-014, DR-005)
-- Audit records can NEVER be modified or deleted by business actors!
CREATE TABLE IF NOT EXISTS platform.audit_records
(
    id             UUID PRIMARY KEY      DEFAULT uuid_generate_v4(),
    actor_id       UUID,
    actor_role     VARCHAR(32),
    action         VARCHAR(64)  NOT NULL,
    target_type    VARCHAR(64)  NOT NULL,
    target_id      VARCHAR(64)  NOT NULL,
    delta          JSONB                 DEFAULT '{}'::jsonb,
    result         VARCHAR(32)  NOT NULL DEFAULT 'SUCCESS',
    ip_address     VARCHAR(45),
    correlation_id VARCHAR(128) NOT NULL,
    timestamp      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_records_target ON platform.audit_records (target_type, target_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_records_actor ON platform.audit_records (actor_id, timestamp DESC);

-- 3. Idempotency Records (COM-008, BR-015)
CREATE TABLE IF NOT EXISTS platform.idempotency_records
(
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_hash    VARCHAR(64) NOT NULL,
    scope           VARCHAR(64) NOT NULL,
    state           VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS' CHECK (state IN ('IN_PROGRESS', 'SUCCEEDED', 'FAILED')),
    response_code   INT,
    response_body   JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_idempotency_expires_at ON platform.idempotency_records (expires_at);
