-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.22 Budget repas étudiant
-- --------------------------------------------------------------------------

CREATE TABLE student_budget_settings (
    student_id          UUID PRIMARY KEY REFERENCES students(id),
    monthly_budget      NUMERIC(12,2) NOT NULL,
    currency            CHAR(3) NOT NULL DEFAULT 'MAD',
    alert_threshold_pct NUMERIC(5,2) NOT NULL DEFAULT 80,
    is_enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_student_budget_amount CHECK (monthly_budget > 0),
    CONSTRAINT ck_student_budget_threshold CHECK (
        alert_threshold_pct > 0 AND alert_threshold_pct <= 100
    )
);

-- --------------------------------------------------------------------------
