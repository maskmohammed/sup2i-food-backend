-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 09. POS / CAISSE
-- ============================================================================

CREATE TABLE pos_terminals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id     UUID NOT NULL REFERENCES locations(id),
    code            VARCHAR(80) NOT NULL,
    name            VARCHAR(120) NOT NULL,
    software_type   VARCHAR(40) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pos_terminal_location_code UNIQUE(location_id, code),
    CONSTRAINT ck_pos_terminal_software CHECK (
        software_type IN ('CACTUS_PHP','SUP2I_POS','OTHER')
    )
);

CREATE TABLE pos_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    terminal_id     UUID NOT NULL REFERENCES pos_terminals(id),
    cashier_id      UUID NOT NULL REFERENCES users(id),
    opened_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at       TIMESTAMPTZ,
    opening_cash    NUMERIC(12,2) NOT NULL DEFAULT 0,
    expected_cash   NUMERIC(12,2),
    counted_cash    NUMERIC(12,2),
    card_total      NUMERIC(12,2),
    difference      NUMERIC(12,2),
    close_reason    TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    CONSTRAINT ck_pos_sessions_status CHECK (
        status IN ('OPEN','CLOSED','FORCED_CLOSED')
    ),
    CONSTRAINT ck_pos_sessions_opening_cash CHECK (opening_cash >= 0),
    CONSTRAINT ck_pos_sessions_expected_cash CHECK (
        expected_cash IS NULL OR expected_cash >= 0
    ),
    CONSTRAINT ck_pos_sessions_counted_cash CHECK (
        counted_cash IS NULL OR counted_cash >= 0
    ),
    CONSTRAINT ck_pos_sessions_card_total CHECK (
        card_total IS NULL OR card_total >= 0
    )
);

CREATE UNIQUE INDEX uq_pos_terminal_open_session
    ON pos_sessions(terminal_id)
    WHERE status = 'OPEN';

CREATE INDEX idx_pos_sessions_cashier ON pos_sessions(cashier_id, opened_at DESC);

CREATE TABLE cash_movements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pos_session_id  UUID NOT NULL REFERENCES pos_sessions(id),
    type            VARCHAR(30) NOT NULL,
    amount          NUMERIC(12,2) NOT NULL,
    reason          TEXT,
    performed_by    UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cash_movements_type CHECK (
        type IN ('SALE','REFUND','CASH_IN','CASH_OUT','ADJUSTMENT')
    ),
    CONSTRAINT ck_cash_movements_amount CHECK (amount > 0)
);

CREATE INDEX idx_cash_movements_session_created
    ON cash_movements(pos_session_id, created_at);

-- ============================================================================
