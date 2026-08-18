-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 22. TABLES TECHNIQUES
-- ============================================================================

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    device_info     VARCHAR(255),
    ip_address      INET,
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by_id  UUID REFERENCES refresh_tokens(id),
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX idx_refresh_tokens_user_active
    ON refresh_tokens(user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE device_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    platform        VARCHAR(20) NOT NULL,
    token           VARCHAR(512) NOT NULL UNIQUE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_device_tokens_platform CHECK (
        platform IN ('ANDROID','IOS','WEB')
    )
);

CREATE INDEX idx_device_tokens_user_active
    ON device_tokens(user_id, is_active);

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(80) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(120) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMPTZ,
    retry_count     INTEGER NOT NULL DEFAULT 0,
    last_error      TEXT,
    CONSTRAINT ck_outbox_events_status CHECK (
        status IN ('PENDING','PROCESSING','PUBLISHED','FAILED')
    ),
    CONSTRAINT ck_outbox_events_retry CHECK (retry_count >= 0)
);

CREATE INDEX idx_outbox_events_pending
    ON outbox_events(status, occurred_at)
    WHERE status IN ('PENDING','FAILED');

CREATE TABLE idempotency_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key     VARCHAR(160) NOT NULL,
    scope               VARCHAR(120) NOT NULL,
    user_id             UUID REFERENCES users(id),
    request_hash        VARCHAR(128) NOT NULL,
    response_status     INTEGER,
    response_body       JSONB,
    resource_type       VARCHAR(80),
    resource_id         UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency_scope_key UNIQUE(scope, idempotency_key),
    CONSTRAINT ck_idempotency_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_idempotency_records_expires
    ON idempotency_records(expires_at);

CREATE TABLE file_assets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    storage_key     VARCHAR(500) NOT NULL UNIQUE,
    original_name   VARCHAR(255),
    mime_type       VARCHAR(120),
    size_bytes      BIGINT,
    checksum_sha256 VARCHAR(64),
    public_url      TEXT,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT ck_file_assets_size CHECK (
        size_bytes IS NULL OR size_bytes >= 0
    )
);

-- ============================================================================
-- 23. FINALISATION DU MODÈLE — RÉCONCILIATIONS DU FREEZE FINAL
-- ============================================================================

-- --------------------------------------------------------------------------
