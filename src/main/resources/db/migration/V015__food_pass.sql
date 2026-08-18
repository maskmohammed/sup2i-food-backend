-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 15. FOOD PASS
-- ============================================================================

CREATE TABLE food_passes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL REFERENCES students(id),
    credential_id   UUID NOT NULL UNIQUE REFERENCES qr_credentials(id),
    card_number     VARCHAR(100) NOT NULL UNIQUE,
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMPTZ,
    replaced_by_id  UUID REFERENCES food_passes(id),
    blocked_at      TIMESTAMPTZ,
    block_reason    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_food_passes_status CHECK (
        status IN ('ACTIVE','BLOCKED','LOST','REVOKED','EXPIRED','REPLACED')
    ),
    CONSTRAINT ck_food_passes_expiry CHECK (
        expires_at IS NULL OR expires_at > issued_at
    )
);

CREATE UNIQUE INDEX uq_one_active_food_pass_per_student
    ON food_passes(student_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_food_passes_student_status
    ON food_passes(student_id, status);

CREATE TABLE food_pass_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    food_pass_id    UUID NOT NULL REFERENCES food_passes(id),
    event_type      VARCHAR(40) NOT NULL,
    reason          TEXT,
    performed_by    UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_food_pass_events_type CHECK (
        event_type IN (
            'ISSUED','BLOCKED','LOST','REVOKED',
            'REPLACED','REACTIVATED','EXPIRED'
        )
    )
);

CREATE INDEX idx_food_pass_events_pass_created
    ON food_pass_events(food_pass_id, created_at);

CREATE TABLE meal_usages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entitlement_id  UUID NOT NULL REFERENCES meal_entitlements(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    menu_id         UUID REFERENCES canteen_menus(id),
    usage_date      DATE NOT NULL,
    meal_type       VARCHAR(30) NOT NULL,
    food_pass_id    UUID REFERENCES food_passes(id),
    consumed_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validated_by    UUID NOT NULL REFERENCES users(id),
    terminal_id     UUID REFERENCES pos_terminals(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'VALID',
    reversed_at     TIMESTAMPTZ,
    reversed_by     UUID REFERENCES users(id),
    reversal_reason TEXT,
    CONSTRAINT ck_meal_usages_type CHECK (
        meal_type IN ('BREAKFAST','LUNCH','DINNER','OTHER')
    ),
    CONSTRAINT ck_meal_usages_status CHECK (
        status IN ('VALID','REVERSED')
    )
);

-- MVP gelé : 1 utilisation VALIDE max / étudiant / date / type.
-- Partial unique permet une nouvelle distribution après une reversal autorisée/auditée.
CREATE UNIQUE INDEX uq_meal_usage_valid_student_day_type
    ON meal_usages(student_id, usage_date, meal_type)
    WHERE status = 'VALID';

CREATE INDEX idx_meal_usages_entitlement
    ON meal_usages(entitlement_id, usage_date);

-- ============================================================================
