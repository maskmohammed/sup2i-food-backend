-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.32.4 Historisation immuable des conditions d'abonnement
-- --------------------------------------------------------------------------

CREATE TABLE subscription_plan_versions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id             UUID NOT NULL REFERENCES subscription_plans(id),
    version_number      INTEGER NOT NULL,
    audience_type       VARCHAR(20) NOT NULL,
    billing_period      VARCHAR(30) NOT NULL,
    price               NUMERIC(12,2) NOT NULL,
    included_meals      INTEGER,
    validity_days       INTEGER,
    quota_type          VARCHAR(30),
    quota_period_type   VARCHAR(30) NOT NULL,
    quota_value         INTEGER,
    max_per_day         INTEGER,
    allowed_days        SMALLINT[],
    reservation_required BOOLEAN NOT NULL DEFAULT FALSE,
    reservation_deadline TIME,
    reservation_cancellation_deadline TIME,
    renewal_policy      VARCHAR(30) NOT NULL,
    suspension_policy   VARCHAR(30) NOT NULL,
    academic_calendar_id UUID REFERENCES academic_calendars(id),
    sale_starts_at      TIMESTAMPTZ,
    sale_ends_at        TIMESTAMPTZ,
    rules               JSONB NOT NULL DEFAULT '{}'::JSONB,
    effective_from      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to        TIMESTAMPTZ,
    created_by          UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_subscription_plan_versions_plan_version
        UNIQUE(plan_id, version_number),
    CONSTRAINT uq_subscription_plan_versions_id_plan
        UNIQUE(id, plan_id),
    CONSTRAINT ck_subscription_plan_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_subscription_plan_versions_audience CHECK (
        audience_type IN ('STUDENT','STAFF','EVENT','ANY')
    ),
    CONSTRAINT ck_subscription_plan_versions_period CHECK (
        billing_period IN ('WEEK','MONTH','SEMESTER','SCHOOL_YEAR','MEAL_PACK','CUSTOM')
    ),
    CONSTRAINT ck_subscription_plan_versions_price CHECK (price >= 0),
    CONSTRAINT ck_subscription_plan_versions_included_meals CHECK (
        included_meals IS NULL OR included_meals > 0
    ),
    CONSTRAINT ck_subscription_plan_versions_validity_days CHECK (
        validity_days IS NULL OR validity_days > 0
    ),
    CONSTRAINT ck_subscription_plan_versions_quota_period CHECK (
        quota_period_type IN ('SUBSCRIPTION','WEEK','MONTH','DAY')
    ),
    CONSTRAINT ck_subscription_plan_versions_quota_value CHECK (
        quota_value IS NULL OR quota_value > 0
    ),
    CONSTRAINT ck_subscription_plan_versions_max_per_day CHECK (
        max_per_day IS NULL OR max_per_day > 0
    ),
    CONSTRAINT ck_subscription_plan_versions_allowed_days CHECK (
        allowed_days IS NULL OR allowed_days <@ ARRAY[1,2,3,4,5,6,7]::SMALLINT[]
    ),
    CONSTRAINT ck_subscription_plan_versions_renewal CHECK (
        renewal_policy IN ('MANUAL','AUTO')
    ),
    CONSTRAINT ck_subscription_plan_versions_suspension CHECK (
        suspension_policy IN ('BLOCK_USAGE','PAUSE_VALIDITY','CUSTOM')
    ),
    CONSTRAINT ck_subscription_plan_versions_sale_range CHECK (
        sale_ends_at IS NULL OR sale_starts_at IS NULL OR sale_ends_at > sale_starts_at
    ),
    CONSTRAINT ck_subscription_plan_versions_effective_range CHECK (
        effective_to IS NULL OR effective_to > effective_from
    )
);

CREATE INDEX idx_subscription_plan_versions_plan_effective
    ON subscription_plan_versions(plan_id, effective_from DESC);

CREATE UNIQUE INDEX uq_subscription_plan_versions_current
    ON subscription_plan_versions(plan_id)
    WHERE effective_to IS NULL;

CREATE TABLE subscription_plan_version_services (
    plan_version_id     UUID NOT NULL REFERENCES subscription_plan_versions(id),
    service_type        VARCHAR(30) NOT NULL,
    PRIMARY KEY(plan_version_id, service_type),
    CONSTRAINT ck_subscription_plan_version_services_type CHECK (
        service_type IN ('BREAKFAST','LUNCH','DINNER','OTHER')
    )
);

ALTER TABLE subscriptions
    ADD COLUMN plan_version_id UUID NOT NULL;

ALTER TABLE subscriptions
    ADD CONSTRAINT fk_subscriptions_plan_version_plan
    FOREIGN KEY(plan_version_id, plan_id)
    REFERENCES subscription_plan_versions(id, plan_id);

COMMENT ON TABLE production_runs IS
'Production réelle/batch. Les quantités préparées sont persistées ici pour permettre le KPI préparé -> vendu/distribué -> gaspillé exigé par le MASTER.';

COMMENT ON TABLE production_allocations IS
'Allocation d une quantité réellement préparée à une vente ou distribution Cantine. Évite de deviner le batch de production dans les rapports.';

COMMENT ON TABLE preparation_routes IS
'Routage configurable d un point de service vers la cuisine responsable, avec override catégorie/produit/variante.';

COMMENT ON TABLE kitchen_ticket_items IS
'Granularité ligne KDS nécessaire lorsque plusieurs cuisines préparent des composants d une même commande.';

COMMENT ON TABLE meal_beneficiaries IS
'Bénéficiaires non étudiants des futures formules personnel/événement. Le Food Pass étudiant reste lié à students.';

COMMENT ON TABLE subscription_plan_versions IS
'Snapshot versionné et immuable des conditions de plan. Une subscription référence la version effectivement souscrite.';

-- ============================================================================
