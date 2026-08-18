-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.6 POS : validation superviseur, tickets/reçus, tentatives/événements scan
-- --------------------------------------------------------------------------

ALTER TABLE pos_terminals ADD COLUMN terminal_type VARCHAR(30) NOT NULL DEFAULT 'POS';
ALTER TABLE pos_terminals ADD CONSTRAINT ck_pos_terminals_terminal_type CHECK (
    terminal_type IN ('POS','CANTEEN_SCANNER','KIOSK','ADMIN_STATION','OTHER')
);

ALTER TABLE pos_sessions ADD COLUMN supervisor_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pos_sessions ADD COLUMN validated_by UUID REFERENCES users(id);
ALTER TABLE pos_sessions ADD COLUMN validated_at TIMESTAMPTZ;
ALTER TABLE pos_sessions ADD COLUMN forced_closed_by UUID REFERENCES users(id);

CREATE TABLE pos_session_tender_totals (
    pos_session_id            UUID NOT NULL REFERENCES pos_sessions(id),
    payment_method            VARCHAR(30) NOT NULL,
    theoretical_amount        NUMERIC(12,2) NOT NULL DEFAULT 0,
    counted_or_settled_amount NUMERIC(12,2),
    difference                NUMERIC(12,2),
    PRIMARY KEY(pos_session_id, payment_method),
    CONSTRAINT ck_pos_session_tender_method CHECK (
        payment_method IN ('CASH','CARD_TPE','ONLINE','WALLET')
    ),
    CONSTRAINT ck_pos_session_tender_theoretical CHECK (theoretical_amount >= 0),
    CONSTRAINT ck_pos_session_tender_counted CHECK (
        counted_or_settled_amount IS NULL OR counted_or_settled_amount >= 0
    )
);

ALTER TABLE payments ADD COLUMN tendered_amount NUMERIC(12,2);
ALTER TABLE payments ADD COLUMN change_amount NUMERIC(12,2);
ALTER TABLE payments ADD COLUMN provider_code VARCHAR(80);
ALTER TABLE payments ADD COLUMN failure_code VARCHAR(100);
ALTER TABLE payments ADD COLUMN failure_message TEXT;
ALTER TABLE payments ADD CONSTRAINT ck_payments_tendered_amount CHECK (
    tendered_amount IS NULL OR tendered_amount >= amount
);
ALTER TABLE payments ADD CONSTRAINT ck_payments_change_amount CHECK (
    change_amount IS NULL OR change_amount >= 0
);

CREATE TABLE payment_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id          UUID NOT NULL REFERENCES payments(id),
    event_type          VARCHAR(50) NOT NULL,
    status_before       VARCHAR(40),
    status_after        VARCHAR(40),
    external_reference  VARCHAR(160),
    provider_event_id   VARCHAR(160),
    payload             JSONB,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_events_payment_time
    ON payment_events(payment_id, occurred_at);

CREATE UNIQUE INDEX uq_payment_events_provider_event
    ON payment_events(provider_event_id)
    WHERE provider_event_id IS NOT NULL;

ALTER TABLE refunds ADD COLUMN external_reference VARCHAR(160);
ALTER TABLE refunds ADD COLUMN idempotency_key VARCHAR(160);
ALTER TABLE refunds ADD COLUMN approved_at TIMESTAMPTZ;
ALTER TABLE refunds ADD COLUMN failed_at TIMESTAMPTZ;
ALTER TABLE refunds ADD COLUMN failure_message TEXT;
CREATE UNIQUE INDEX uq_refunds_idempotency
    ON refunds(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE sales_receipts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    order_id        UUID NOT NULL REFERENCES orders(id),
    payment_id      UUID REFERENCES payments(id),
    pos_session_id  UUID REFERENCES pos_sessions(id),
    receipt_number  VARCHAR(80) NOT NULL,
    business_date   DATE NOT NULL,
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    issued_by       UUID REFERENCES users(id),
    status          VARCHAR(30) NOT NULL DEFAULT 'ISSUED',
    fiscal_metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    receipt_snapshot JSONB NOT NULL,
    CONSTRAINT uq_sales_receipts_number UNIQUE(
        organization_id, business_date, receipt_number
    ),
    CONSTRAINT ck_sales_receipts_status CHECK (
        status IN ('ISSUED','VOIDED','REPLACED')
    )
);

CREATE INDEX idx_sales_receipts_order ON sales_receipts(order_id);

CREATE TABLE receipt_print_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id    UUID NOT NULL REFERENCES sales_receipts(id),
    terminal_id   UUID REFERENCES pos_terminals(id),
    printed_by    UUID REFERENCES users(id),
    event_type    VARCHAR(30) NOT NULL DEFAULT 'PRINT',
    result        VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
    error_message TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_receipt_print_event_type CHECK (
        event_type IN ('PRINT','REPRINT')
    ),
    CONSTRAINT ck_receipt_print_event_result CHECK (
        result IN ('SUCCESS','FAILED')
    )
);

CREATE TABLE scan_events (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    terminal_id           UUID REFERENCES pos_terminals(id),
    operator_id           UUID REFERENCES users(id),
    scan_type             VARCHAR(30) NOT NULL,
    result                VARCHAR(30) NOT NULL,
    resolved_reference_id UUID,
    token_fingerprint     VARCHAR(128),
    error_code            VARCHAR(100),
    occurred_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata              JSONB,
    CONSTRAINT ck_scan_events_type CHECK (
        scan_type IN ('PRODUCT_BARCODE','ORDER','FOOD_PASS','LOYALTY','UNKNOWN')
    ),
    CONSTRAINT ck_scan_events_result CHECK (
        result IN ('SUCCESS','REFUSED','UNKNOWN','ERROR')
    )
);

CREATE INDEX idx_scan_events_terminal_time
    ON scan_events(terminal_id, occurred_at DESC);

-- --------------------------------------------------------------------------
