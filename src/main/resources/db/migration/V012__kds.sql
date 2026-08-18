-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 12. KDS / CUISINE
-- ============================================================================

CREATE TABLE kitchen_tickets (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID NOT NULL UNIQUE REFERENCES orders(id),
    kitchen_location_id UUID NOT NULL REFERENCES locations(id),
    status              VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    priority            INTEGER NOT NULL DEFAULT 0,
    queued_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at         TIMESTAMPTZ,
    started_at          TIMESTAMPTZ,
    ready_at            TIMESTAMPTZ,
    assigned_to         UUID REFERENCES users(id),
    CONSTRAINT ck_kitchen_tickets_status CHECK (
        status IN ('QUEUED','ACCEPTED','PREPARING','READY','CANCELLED')
    )
);

CREATE INDEX idx_kitchen_tickets_location_status
    ON kitchen_tickets(kitchen_location_id, status, priority DESC, queued_at);

-- ============================================================================
