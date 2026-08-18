-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 17. NOTIFICATIONS
-- ============================================================================

CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    type            VARCHAR(50) NOT NULL,
    channel         VARCHAR(30) NOT NULL,
    title           VARCHAR(180) NOT NULL,
    body            TEXT NOT NULL,
    payload         JSONB,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    sent_at         TIMESTAMPTZ,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_notifications_channel CHECK (
        channel IN ('PUSH','EMAIL','IN_APP')
    ),
    CONSTRAINT ck_notifications_status CHECK (
        status IN ('PENDING','SENT','FAILED','READ')
    )
);

CREATE INDEX idx_notifications_user_status_created
    ON notifications(user_id, status, created_at DESC);

CREATE TABLE notification_preferences (
    user_id                 UUID PRIMARY KEY REFERENCES users(id),
    transactional_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    canteen_enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    marketing_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
