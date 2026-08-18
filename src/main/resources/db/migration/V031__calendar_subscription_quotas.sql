-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.9 Calendrier / abonnements / quota périodique / historique et corrections
-- --------------------------------------------------------------------------

CREATE TABLE academic_calendars (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campus_id   UUID NOT NULL REFERENCES campuses(id),
    name        VARCHAR(150) NOT NULL,
    starts_on   DATE NOT NULL,
    ends_on     DATE NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_academic_calendars_dates CHECK (ends_on >= starts_on)
);

CREATE TABLE academic_calendar_events (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    academic_calendar_id UUID NOT NULL REFERENCES academic_calendars(id),
    event_type           VARCHAR(40) NOT NULL,
    title                VARCHAR(180) NOT NULL,
    starts_at            TIMESTAMPTZ NOT NULL,
    ends_at              TIMESTAMPTZ NOT NULL,
    affects_service      BOOLEAN NOT NULL DEFAULT TRUE,
    metadata             JSONB,
    CONSTRAINT ck_academic_calendar_events_type CHECK (
        event_type IN ('COURSE_PERIOD','EXAM','HOLIDAY','VACATION','EVENT','CLOSURE','OTHER')
    ),
    CONSTRAINT ck_academic_calendar_events_dates CHECK (ends_at > starts_at)
);

CREATE INDEX idx_academic_calendar_events_dates
    ON academic_calendar_events(academic_calendar_id, starts_at, ends_at);

ALTER TABLE subscription_plans ADD COLUMN academic_calendar_id UUID REFERENCES academic_calendars(id);
ALTER TABLE subscription_plans ADD COLUMN quota_period_type VARCHAR(30) NOT NULL DEFAULT 'SUBSCRIPTION';
ALTER TABLE subscription_plans ADD COLUMN renewal_policy VARCHAR(30) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE subscription_plans ADD COLUMN suspension_policy VARCHAR(30) NOT NULL DEFAULT 'BLOCK_USAGE';
ALTER TABLE subscription_plans ADD COLUMN reservation_deadline TIME;
ALTER TABLE subscription_plans ADD COLUMN reservation_cancellation_deadline TIME;
ALTER TABLE subscription_plans ADD COLUMN sale_starts_at TIMESTAMPTZ;
ALTER TABLE subscription_plans ADD COLUMN sale_ends_at TIMESTAMPTZ;

ALTER TABLE subscription_plans ADD CONSTRAINT ck_subscription_plans_quota_period_type CHECK (
    quota_period_type IN ('SUBSCRIPTION','WEEK','MONTH','DAY')
);
ALTER TABLE subscription_plans ADD CONSTRAINT ck_subscription_plans_renewal_policy CHECK (
    renewal_policy IN ('MANUAL','AUTO')
);
ALTER TABLE subscription_plans ADD CONSTRAINT ck_subscription_plans_suspension_policy CHECK (
    suspension_policy IN ('BLOCK_USAGE','PAUSE_VALIDITY','CUSTOM')
);
ALTER TABLE subscription_plans ADD CONSTRAINT ck_subscription_plans_sale_range CHECK (
    sale_ends_at IS NULL OR sale_starts_at IS NULL OR sale_ends_at > sale_starts_at
);

CREATE TABLE subscription_plan_services (
    subscription_plan_id UUID NOT NULL REFERENCES subscription_plans(id),
    service_type         VARCHAR(30) NOT NULL,
    PRIMARY KEY(subscription_plan_id, service_type),
    CONSTRAINT ck_subscription_plan_services_type CHECK (
        service_type IN ('BREAKFAST','LUNCH','DINNER','OTHER')
    )
);

ALTER TABLE subscriptions ADD COLUMN renewed_from_id UUID REFERENCES subscriptions(id);
ALTER TABLE subscriptions ADD COLUMN administrative_payment_amount NUMERIC(12,2);
ALTER TABLE subscriptions ADD CONSTRAINT ck_subscriptions_admin_payment_amount CHECK (
    administrative_payment_amount IS NULL OR administrative_payment_amount >= 0
);

CREATE TABLE subscription_status_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES subscriptions(id),
    from_status     VARCHAR(30),
    to_status       VARCHAR(30) NOT NULL,
    changed_by      UUID REFERENCES users(id),
    reason          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_subscription_status_history_subscription
    ON subscription_status_history(subscription_id, created_at);

ALTER TABLE meal_entitlements ADD COLUMN quota_period_type VARCHAR(30) NOT NULL DEFAULT 'SUBSCRIPTION';
ALTER TABLE meal_entitlements ADD COLUMN reservation_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE meal_entitlements ADD CONSTRAINT ck_meal_entitlements_quota_period_type CHECK (
    quota_period_type IN ('SUBSCRIPTION','WEEK','MONTH','DAY')
);

CREATE TABLE meal_entitlement_adjustments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entitlement_id UUID NOT NULL REFERENCES meal_entitlements(id),
    quota_delta    INTEGER NOT NULL,
    effective_date DATE NOT NULL DEFAULT CURRENT_DATE,
    reason         TEXT NOT NULL,
    adjusted_by    UUID NOT NULL REFERENCES users(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_meal_entitlement_adjustments_delta CHECK (quota_delta <> 0)
);

-- --------------------------------------------------------------------------
