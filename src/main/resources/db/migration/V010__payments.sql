-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 10. PAIEMENTS / REMBOURSEMENTS
-- ============================================================================

CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID NOT NULL REFERENCES orders(id),
    pos_session_id      UUID REFERENCES pos_sessions(id),
    method              VARCHAR(30) NOT NULL,
    status              VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    amount              NUMERIC(12,2) NOT NULL,
    currency            CHAR(3) NOT NULL DEFAULT 'MAD',
    external_reference  VARCHAR(160),
    idempotency_key     VARCHAR(160),
    received_by         UUID REFERENCES users(id),
    paid_at             TIMESTAMPTZ,
    reversed_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payments_idempotency UNIQUE(idempotency_key),
    CONSTRAINT ck_payments_method CHECK (
        method IN ('CASH','CARD_TPE','ONLINE','WALLET')
    ),
    CONSTRAINT ck_payments_status CHECK (
        status IN (
            'PENDING','COMPLETED','FAILED','CANCELLED',
            'REFUNDED','PARTIALLY_REFUNDED'
        )
    ),
    CONSTRAINT ck_payments_amount CHECK (amount > 0)
);

CREATE INDEX idx_payments_order ON payments(order_id);
CREATE INDEX idx_payments_external_reference ON payments(external_reference);

CREATE TABLE refunds (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      UUID NOT NULL REFERENCES payments(id),
    amount          NUMERIC(12,2) NOT NULL,
    reason          TEXT NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    requested_by    UUID NOT NULL REFERENCES users(id),
    approved_by     UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMPTZ,
    CONSTRAINT ck_refunds_amount CHECK (amount > 0),
    CONSTRAINT ck_refunds_status CHECK (
        status IN (
            'REQUESTED','APPROVED','REJECTED',
            'PROCESSING','COMPLETED','FAILED'
        )
    )
);

CREATE INDEX idx_refunds_payment ON refunds(payment_id);

-- ============================================================================
