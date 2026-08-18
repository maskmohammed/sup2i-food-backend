-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.25 Gamification responsable (roadmap explicite)
-- --------------------------------------------------------------------------

CREATE TABLE gamification_badges (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    code            VARCHAR(80) NOT NULL,
    name            VARCHAR(150) NOT NULL,
    description     TEXT,
    rule_config     JSONB NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_gamification_badges_org_code UNIQUE(organization_id, code)
);

CREATE TABLE student_badges (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id       UUID NOT NULL REFERENCES students(id),
    badge_id         UUID NOT NULL REFERENCES gamification_badges(id),
    awarded_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_reference VARCHAR(160),
    CONSTRAINT uq_student_badge UNIQUE(student_id, badge_id)
);

-- --------------------------------------------------------------------------
