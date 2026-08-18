-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 16. PROMOTIONS / FIDÉLITÉ
-- ============================================================================

CREATE TABLE promotions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name            VARCHAR(180) NOT NULL,
    code            VARCHAR(80),
    type            VARCHAR(40) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL,
    priority        INTEGER NOT NULL DEFAULT 0,
    stackable       BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_promotions_type CHECK (
        type IN ('PERCENTAGE','FIXED_AMOUNT','COMBO_PRICE','BUY_X_GET_Y','LOYALTY_BONUS')
    ),
    CONSTRAINT ck_promotions_status CHECK (
        status IN ('DRAFT','SCHEDULED','ACTIVE','PAUSED','EXPIRED','CANCELLED')
    ),
    CONSTRAINT ck_promotions_dates CHECK (ends_at > starts_at)
);

CREATE INDEX idx_promotions_org_status_dates
    ON promotions(organization_id, status, starts_at, ends_at);

CREATE TABLE promotion_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_id    UUID NOT NULL REFERENCES promotions(id),
    rule_type       VARCHAR(50) NOT NULL,
    rule_config     JSONB NOT NULL
);

CREATE INDEX idx_promotion_rules_promotion ON promotion_rules(promotion_id);

CREATE TABLE promotion_usages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_id    UUID NOT NULL REFERENCES promotions(id),
    order_id        UUID NOT NULL REFERENCES orders(id),
    student_id      UUID REFERENCES students(id),
    discount_amount NUMERIC(12,2) NOT NULL,
    used_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_promotion_usage_order UNIQUE(promotion_id, order_id),
    CONSTRAINT ck_promotion_usage_discount CHECK (discount_amount >= 0)
);

-- Coupon: architecture V2, conservée dans le schéma complet.
CREATE TABLE coupons (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID NOT NULL REFERENCES organizations(id),
    promotion_id            UUID NOT NULL REFERENCES promotions(id),
    code                    VARCHAR(80) NOT NULL,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at               TIMESTAMPTZ,
    ends_at                 TIMESTAMPTZ,
    max_uses                INTEGER,
    max_uses_per_student    INTEGER,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_coupons_org_code UNIQUE(organization_id, code),
    CONSTRAINT ck_coupons_dates CHECK (
        ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at
    ),
    CONSTRAINT ck_coupons_max_uses CHECK (
        max_uses IS NULL OR max_uses > 0
    ),
    CONSTRAINT ck_coupons_max_per_student CHECK (
        max_uses_per_student IS NULL OR max_uses_per_student > 0
    )
);

CREATE TABLE coupon_usages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coupon_id       UUID NOT NULL REFERENCES coupons(id),
    order_id        UUID NOT NULL REFERENCES orders(id),
    student_id      UUID REFERENCES students(id),
    used_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_coupon_usage_order UNIQUE(coupon_id, order_id)
);

CREATE INDEX idx_coupon_usages_student
    ON coupon_usages(coupon_id, student_id, used_at DESC);

CREATE TABLE loyalty_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL UNIQUE REFERENCES students(id),
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    current_balance INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_loyalty_accounts_status CHECK (
        status IN ('ACTIVE','SUSPENDED','CLOSED')
    ),
    CONSTRAINT ck_loyalty_accounts_balance CHECK (current_balance >= 0)
);

CREATE TABLE loyalty_transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL REFERENCES loyalty_accounts(id),
    type            VARCHAR(30) NOT NULL,
    points          INTEGER NOT NULL,
    reference_type  VARCHAR(40),
    reference_id    UUID,
    reason          TEXT,
    created_by      UUID REFERENCES users(id),
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_loyalty_transactions_type CHECK (
        type IN ('EARN','REDEEM','BONUS','ADJUSTMENT','REVERSAL')
    ),
    CONSTRAINT ck_loyalty_transactions_points CHECK (points <> 0)
);

CREATE INDEX idx_loyalty_transactions_account_created
    ON loyalty_transactions(account_id, created_at);

-- ============================================================================
