-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 11. QR / CREDENTIALS
-- ============================================================================

CREATE TABLE qr_credentials (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credential_type     VARCHAR(30) NOT NULL,
    subject_id          UUID NOT NULL,
    token_hash          VARCHAR(255) NOT NULL UNIQUE,
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    metadata            JSONB,
    CONSTRAINT ck_qr_credentials_type CHECK (
        credential_type IN ('ORDER','FOOD_PASS','LOYALTY')
    ),
    CONSTRAINT ck_qr_credentials_status CHECK (
        status IN ('ACTIVE','USED','REVOKED','EXPIRED')
    ),
    CONSTRAINT ck_qr_credentials_expiry CHECK (
        expires_at IS NULL OR expires_at > issued_at
    )
);

CREATE INDEX idx_qr_credentials_subject
    ON qr_credentials(credential_type, subject_id);

-- ============================================================================
