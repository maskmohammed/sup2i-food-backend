-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.21 Wallet futur + recharge parent/sponsor
-- --------------------------------------------------------------------------

CREATE TABLE wallet_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL UNIQUE REFERENCES students(id),
    currency        CHAR(3) NOT NULL DEFAULT 'MAD',
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    current_balance NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_wallet_accounts_status CHECK (
        status IN ('ACTIVE','SUSPENDED','CLOSED')
    ),
    CONSTRAINT ck_wallet_accounts_balance CHECK (current_balance >= 0)
);

CREATE TABLE wallet_transactions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_account_id UUID NOT NULL REFERENCES wallet_accounts(id),
    transaction_type  VARCHAR(30) NOT NULL,
    amount            NUMERIC(12,2) NOT NULL,
    balance_after     NUMERIC(12,2) NOT NULL,
    order_id          UUID REFERENCES orders(id),
    payment_id        UUID REFERENCES payments(id),
    refund_id         UUID REFERENCES refunds(id),
    reference         VARCHAR(160),
    status            VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_wallet_transactions_type CHECK (
        transaction_type IN ('TOPUP','PAYMENT','REFUND','ADJUSTMENT','REVERSAL')
    ),
    CONSTRAINT ck_wallet_transactions_amount CHECK (amount <> 0),
    CONSTRAINT ck_wallet_transactions_balance CHECK (balance_after >= 0),
    CONSTRAINT ck_wallet_transactions_status CHECK (
        status IN ('PENDING','COMPLETED','FAILED','CANCELLED','REVERSED')
    )
);

CREATE INDEX idx_wallet_transactions_account_created
    ON wallet_transactions(wallet_account_id, created_at DESC);

CREATE TABLE wallet_topups (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_account_id     UUID NOT NULL REFERENCES wallet_accounts(id),
    wallet_transaction_id UUID REFERENCES wallet_transactions(id),
    amount                NUMERIC(12,2) NOT NULL,
    payer_type            VARCHAR(30) NOT NULL,
    payer_name            VARCHAR(180),
    payment_method        VARCHAR(30),
    external_reference    VARCHAR(160),
    status                VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at          TIMESTAMPTZ,
    CONSTRAINT ck_wallet_topups_amount CHECK (amount > 0),
    CONSTRAINT ck_wallet_topups_payer_type CHECK (
        payer_type IN ('STUDENT','PARENT','SPONSOR','ADMINISTRATION','OTHER')
    ),
    CONSTRAINT ck_wallet_topups_status CHECK (
        status IN ('PENDING','COMPLETED','FAILED','CANCELLED')
    )
);

-- --------------------------------------------------------------------------
