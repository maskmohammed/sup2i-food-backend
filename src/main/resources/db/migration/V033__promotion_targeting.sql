-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.11 Promotions : ciblage produit/catégorie/menu/segment + fenêtres horaires
-- --------------------------------------------------------------------------

ALTER TABLE promotions ADD COLUMN description TEXT;
ALTER TABLE promotions ADD COLUMN discount_value NUMERIC(12,2);
ALTER TABLE promotions ADD COLUMN max_discount_amount NUMERIC(12,2);
ALTER TABLE promotions ADD COLUMN min_quantity INTEGER;
ALTER TABLE promotions ADD COLUMN usage_limit_total INTEGER;
ALTER TABLE promotions ADD COLUMN usage_limit_per_student INTEGER;
ALTER TABLE promotions ADD COLUMN mobile_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE promotions ADD COLUMN pos_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE promotions ADD CONSTRAINT ck_promotions_discount_value CHECK (
    discount_value IS NULL OR discount_value >= 0
);
ALTER TABLE promotions ADD CONSTRAINT ck_promotions_percentage_value CHECK (
    type <> 'PERCENTAGE' OR discount_value IS NULL OR discount_value <= 100
);
ALTER TABLE promotions ADD CONSTRAINT ck_promotions_max_discount_amount CHECK (
    max_discount_amount IS NULL OR max_discount_amount >= 0
);
ALTER TABLE promotions ADD CONSTRAINT ck_promotions_min_quantity CHECK (
    min_quantity IS NULL OR min_quantity > 0
);
ALTER TABLE promotions ADD CONSTRAINT ck_promotions_usage_limits CHECK (
    (usage_limit_total IS NULL OR usage_limit_total > 0)
    AND (usage_limit_per_student IS NULL OR usage_limit_per_student > 0)
);

CREATE TABLE student_segments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    code            VARCHAR(80) NOT NULL,
    name            VARCHAR(150) NOT NULL,
    segment_type    VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    rule_config     JSONB,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_student_segments_org_code UNIQUE(organization_id, code),
    CONSTRAINT ck_student_segments_type CHECK (
        segment_type IN ('MANUAL','RULE_BASED','IMPORTED')
    )
);

CREATE TABLE student_segment_memberships (
    segment_id  UUID NOT NULL REFERENCES student_segments(id),
    student_id  UUID NOT NULL REFERENCES students(id),
    source      VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    valid_from  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_to    TIMESTAMPTZ,
    PRIMARY KEY(segment_id, student_id),
    CONSTRAINT ck_student_segment_memberships_source CHECK (
        source IN ('MANUAL','RULE','IMPORT')
    ),
    CONSTRAINT ck_student_segment_memberships_dates CHECK (
        valid_to IS NULL OR valid_to > valid_from
    )
);

CREATE TABLE promotion_targets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_id    UUID NOT NULL REFERENCES promotions(id),
    target_type     VARCHAR(30) NOT NULL,
    target_id       UUID,
    include_target  BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_promotion_targets_type CHECK (
        target_type IN (
            'ALL','PRODUCT','CATEGORY','MENU','STUDENT',
            'STUDENT_SEGMENT','LOCATION'
        )
    ),
    CONSTRAINT ck_promotion_targets_target_id CHECK (
        target_type = 'ALL' OR target_id IS NOT NULL
    )
);

CREATE INDEX idx_promotion_targets_promotion ON promotion_targets(promotion_id);

CREATE TABLE promotion_schedule_windows (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_id    UUID NOT NULL REFERENCES promotions(id),
    day_of_week     SMALLINT,
    starts_at_time  TIME,
    ends_at_time    TIME,
    CONSTRAINT ck_promotion_schedule_day CHECK (
        day_of_week IS NULL OR day_of_week BETWEEN 1 AND 7
    ),
    CONSTRAINT ck_promotion_schedule_time CHECK (
        starts_at_time IS NULL OR ends_at_time IS NULL OR ends_at_time > starts_at_time
    )
);

-- --------------------------------------------------------------------------
