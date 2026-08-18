-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.32.3 Formules personnel / événement
-- --------------------------------------------------------------------------

-- Les étudiants restent modélisés par students pour le MVP. Cette abstraction
-- couvre uniquement les bénéficiaires non-étudiants explicitement prévus par
-- MASTER §22.4 (personnel / événement) sans casser Food Pass étudiant.
CREATE TABLE meal_beneficiaries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    user_id             UUID NOT NULL REFERENCES users(id),
    beneficiary_type    VARCHAR(20) NOT NULL,
    campus_id           UUID REFERENCES campuses(id),
    campus_event_id     UUID REFERENCES campus_events(id),
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    valid_from          DATE,
    valid_to            DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_meal_beneficiaries_type CHECK (
        beneficiary_type IN ('STAFF','EVENT')
    ),
    CONSTRAINT ck_meal_beneficiaries_status CHECK (
        status IN ('ACTIVE','SUSPENDED','EXPIRED','ARCHIVED')
    ),
    CONSTRAINT ck_meal_beneficiaries_event CHECK (
        beneficiary_type <> 'EVENT' OR campus_event_id IS NOT NULL
    ),
    CONSTRAINT ck_meal_beneficiaries_validity CHECK (
        valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from
    ),
    CONSTRAINT uq_meal_beneficiaries_scope UNIQUE NULLS NOT DISTINCT (
        user_id, beneficiary_type, campus_event_id
    )
);

CREATE INDEX idx_meal_beneficiaries_user_status
    ON meal_beneficiaries(user_id, status);

ALTER TABLE subscription_plans
    ADD COLUMN audience_type VARCHAR(20) NOT NULL DEFAULT 'STUDENT';

ALTER TABLE subscription_plans
    ADD CONSTRAINT ck_subscription_plans_audience_type CHECK (
        audience_type IN ('STUDENT','STAFF','EVENT','ANY')
    );

ALTER TABLE subscriptions
    ALTER COLUMN student_id DROP NOT NULL;

ALTER TABLE subscriptions
    ADD COLUMN meal_beneficiary_id UUID REFERENCES meal_beneficiaries(id);

ALTER TABLE subscriptions
    ADD CONSTRAINT ck_subscriptions_beneficiary CHECK (
        num_nonnulls(student_id, meal_beneficiary_id) = 1
    );

CREATE INDEX idx_subscriptions_beneficiary_status
    ON subscriptions(meal_beneficiary_id, status)
    WHERE meal_beneficiary_id IS NOT NULL;

ALTER TABLE canteen_reservations
    ALTER COLUMN student_id DROP NOT NULL;

ALTER TABLE canteen_reservations
    ADD COLUMN meal_beneficiary_id UUID REFERENCES meal_beneficiaries(id);

ALTER TABLE canteen_reservations
    ADD CONSTRAINT ck_canteen_reservations_beneficiary CHECK (
        num_nonnulls(student_id, meal_beneficiary_id) = 1
    );

CREATE UNIQUE INDEX uq_canteen_reservation_beneficiary_menu
    ON canteen_reservations(meal_beneficiary_id, menu_id)
    WHERE meal_beneficiary_id IS NOT NULL;

ALTER TABLE meal_usages
    ALTER COLUMN student_id DROP NOT NULL;

ALTER TABLE meal_usages
    ADD COLUMN meal_beneficiary_id UUID REFERENCES meal_beneficiaries(id);

ALTER TABLE meal_usages
    ADD CONSTRAINT ck_meal_usages_beneficiary CHECK (
        num_nonnulls(student_id, meal_beneficiary_id) = 1
    );

CREATE UNIQUE INDEX uq_meal_usage_valid_beneficiary_day_type
    ON meal_usages(meal_beneficiary_id, usage_date, meal_type)
    WHERE status = 'VALID' AND meal_beneficiary_id IS NOT NULL;

-- --------------------------------------------------------------------------
