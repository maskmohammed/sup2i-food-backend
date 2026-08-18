-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.7 KDS : action « Problème » / refaire / incident cuisine
-- --------------------------------------------------------------------------

DROP INDEX IF EXISTS uq_kitchen_tickets_order;

CREATE TABLE kitchen_ticket_issues (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kitchen_ticket_id UUID NOT NULL REFERENCES kitchen_tickets(id),
    issue_type        VARCHAR(40) NOT NULL,
    status            VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    description       TEXT NOT NULL,
    reported_by       UUID NOT NULL REFERENCES users(id),
    reported_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_by       UUID REFERENCES users(id),
    resolved_at       TIMESTAMPTZ,
    resolution_note   TEXT,
    CONSTRAINT ck_kitchen_ticket_issues_type CHECK (
        issue_type IN (
            'OUT_OF_STOCK','PREPARATION_ERROR','REMAKE',
            'EQUIPMENT','ALLERGEN_CONCERN','OTHER'
        )
    ),
    CONSTRAINT ck_kitchen_ticket_issues_status CHECK (
        status IN ('OPEN','RESOLVED','CANCELLED')
    )
);

-- --------------------------------------------------------------------------
