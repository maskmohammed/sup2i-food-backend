-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 20. AVIS / SONDAGES
-- ============================================================================

CREATE TABLE reviews (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL REFERENCES students(id),
    product_id      UUID REFERENCES products(id),
    order_id        UUID REFERENCES orders(id),
    menu_id         UUID REFERENCES canteen_menus(id),
    rating          SMALLINT NOT NULL,
    comment         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_target CHECK (
        product_id IS NOT NULL OR order_id IS NOT NULL OR menu_id IS NOT NULL
    )
);

CREATE INDEX idx_reviews_product ON reviews(product_id, created_at DESC);
CREATE INDEX idx_reviews_menu ON reviews(menu_id, created_at DESC);

CREATE TABLE surveys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    title           VARCHAR(180) NOT NULL,
    status          VARCHAR(30) NOT NULL,
    starts_at       TIMESTAMPTZ,
    ends_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_surveys_dates CHECK (
        ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at
    )
);

CREATE TABLE survey_questions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    survey_id       UUID NOT NULL REFERENCES surveys(id),
    question        TEXT NOT NULL,
    type            VARCHAR(30) NOT NULL,
    options         JSONB,
    display_order   INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_survey_questions_order CHECK (display_order >= 0)
);

CREATE TABLE survey_responses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    survey_id       UUID NOT NULL REFERENCES surveys(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    answers         JSONB NOT NULL,
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_survey_response UNIQUE(survey_id, student_id)
);

-- ============================================================================
