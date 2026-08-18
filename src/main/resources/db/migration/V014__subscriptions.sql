-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 14. ABONNEMENTS / QUOTAS
-- ============================================================================

CREATE TABLE subscription_plans (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID NOT NULL REFERENCES organizations(id),
    name                    VARCHAR(150) NOT NULL,
    code                    VARCHAR(80) NOT NULL,
    billing_period          VARCHAR(30) NOT NULL,
    price                   NUMERIC(12,2) NOT NULL,
    included_meals          INTEGER,
    validity_days           INTEGER,
    quota_type              VARCHAR(30),
    quota_value             INTEGER,
    max_per_day             INTEGER,
    allowed_days            SMALLINT[],
    services                VARCHAR(30)[],
    reservation_required    BOOLEAN NOT NULL DEFAULT FALSE,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    rules                   JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_subscription_plans_org_code UNIQUE(organization_id, code),
    CONSTRAINT ck_subscription_plans_period CHECK (
        billing_period IN ('WEEK','MONTH','SEMESTER','SCHOOL_YEAR','MEAL_PACK','CUSTOM')
    ),
    CONSTRAINT ck_subscription_plans_price CHECK (price >= 0),
    CONSTRAINT ck_subscription_plans_included_meals CHECK (
        included_meals IS NULL OR included_meals > 0
    ),
    CONSTRAINT ck_subscription_plans_validity CHECK (
        validity_days IS NULL OR validity_days > 0
    ),
    CONSTRAINT ck_subscription_plans_quota CHECK (
        quota_value IS NULL OR quota_value > 0
    ),
    CONSTRAINT ck_subscription_plans_max_per_day CHECK (
        max_per_day IS NULL OR max_per_day > 0
    ),
    CONSTRAINT ck_subscription_plans_allowed_days CHECK (
        allowed_days IS NULL OR allowed_days <@ ARRAY[1,2,3,4,5,6,7]::SMALLINT[]
    )
);

CREATE TABLE subscriptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id          UUID NOT NULL REFERENCES students(id),
    plan_id             UUID NOT NULL REFERENCES subscription_plans(id),
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    starts_at           DATE NOT NULL,
    ends_at             DATE NOT NULL,
    payment_reference   VARCHAR(160),
    activated_by        UUID REFERENCES users(id),
    activated_at        TIMESTAMPTZ,
    suspended_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_subscriptions_status CHECK (
        status IN ('PENDING','ACTIVE','SUSPENDED','EXPIRED','CANCELLED')
    ),
    CONSTRAINT ck_subscriptions_dates CHECK (ends_at >= starts_at)
);

CREATE INDEX idx_subscriptions_student_status
    ON subscriptions(student_id, status);

CREATE TABLE meal_entitlements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES subscriptions(id),
    meal_type       VARCHAR(30) NOT NULL,
    valid_from      DATE NOT NULL,
    valid_to        DATE NOT NULL,
    allowed_days    SMALLINT[],
    total_quota     INTEGER,
    daily_limit     INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_meal_entitlements_type CHECK (
        meal_type IN ('BREAKFAST','LUNCH','DINNER','OTHER')
    ),
    CONSTRAINT ck_meal_entitlements_dates CHECK (valid_to >= valid_from),
    CONSTRAINT ck_meal_entitlements_days CHECK (
        allowed_days IS NULL OR allowed_days <@ ARRAY[1,2,3,4,5,6,7]::SMALLINT[]
    ),
    CONSTRAINT ck_meal_entitlements_quota CHECK (
        total_quota IS NULL OR total_quota > 0
    ),
    CONSTRAINT ck_meal_entitlements_daily CHECK (daily_limit > 0)
);

CREATE INDEX idx_meal_entitlements_subscription
    ON meal_entitlements(subscription_id);

-- ============================================================================
