-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 21. AUDIT / CONFIGURATION
-- ============================================================================

CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id         UUID REFERENCES users(id),
    actor_role_code VARCHAR(80),
    action          VARCHAR(120) NOT NULL,
    resource_type   VARCHAR(80) NOT NULL,
    resource_id     UUID,
    before_data     JSONB,
    after_data      JSONB,
    reason          TEXT,
    source          VARCHAR(40) NOT NULL,
    terminal_id     UUID REFERENCES pos_terminals(id),
    ip_address      INET,
    result          VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_audit_logs_result CHECK (
        result IN ('SUCCESS','DENIED','FAILED')
    )
);

CREATE INDEX idx_audit_resource
    ON audit_logs(resource_type, resource_id);

CREATE INDEX idx_audit_user_created
    ON audit_logs(user_id, created_at DESC);

CREATE INDEX idx_audit_created
    ON audit_logs(created_at DESC);

CREATE TABLE system_settings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    campus_id       UUID REFERENCES campuses(id),
    location_id     UUID REFERENCES locations(id),
    setting_key     VARCHAR(150) NOT NULL,
    value           JSONB NOT NULL,
    updated_by      UUID NOT NULL REFERENCES users(id),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_system_settings_location_requires_campus
        CHECK (location_id IS NULL OR campus_id IS NOT NULL),
    CONSTRAINT uq_system_settings_scope
        UNIQUE NULLS NOT DISTINCT (organization_id, campus_id, location_id, setting_key)
);

CREATE INDEX idx_system_settings_lookup
    ON system_settings(organization_id, setting_key);

-- ============================================================================
