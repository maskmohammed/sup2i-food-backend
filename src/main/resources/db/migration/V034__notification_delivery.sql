-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.12 Notifications : priorité, planification, déduplication/anti-spam
-- --------------------------------------------------------------------------

ALTER TABLE notifications ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE notifications ADD COLUMN deduplication_key VARCHAR(180);
ALTER TABLE notifications ADD COLUMN scheduled_at TIMESTAMPTZ;
ALTER TABLE notifications ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE notifications ADD COLUMN last_error TEXT;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_priority CHECK (
    priority IN ('LOW','NORMAL','HIGH','CRITICAL')
);
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_retry_count CHECK (retry_count >= 0);

CREATE UNIQUE INDEX uq_notifications_dedupe
    ON notifications(user_id, channel, deduplication_key)
    WHERE deduplication_key IS NOT NULL AND status IN ('PENDING','SENT');

-- La V1 conserve des colonnes de préférences simples. Cette table permet le
-- réglage par catégorie sans casser l'API MVP.
CREATE TABLE notification_category_preferences (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id),
    category          VARCHAR(30) NOT NULL,
    push_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    in_app_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    quiet_hours_start TIME,
    quiet_hours_end   TIME,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_category_preferences UNIQUE(user_id, category),
    CONSTRAINT ck_notification_category_preferences_category CHECK (
        category IN ('TRANSACTIONAL','CANTEEN','MARKETING','LOYALTY','SYSTEM')
    )
);

-- --------------------------------------------------------------------------
