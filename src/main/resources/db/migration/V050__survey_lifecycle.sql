-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.28 Surveys : cycle de vie complet
-- --------------------------------------------------------------------------

ALTER TABLE surveys ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE surveys ADD COLUMN description TEXT;
ALTER TABLE surveys ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE surveys ADD CONSTRAINT ck_surveys_status CHECK (
    status IN ('DRAFT','ACTIVE','CLOSED','ARCHIVED')
);

ALTER TABLE survey_questions ADD COLUMN required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE survey_questions ADD CONSTRAINT ck_survey_questions_type CHECK (
    type IN ('TEXT','SINGLE_CHOICE','MULTIPLE_CHOICE','RATING','BOOLEAN','NUMBER')
);


-- ============================================================================
