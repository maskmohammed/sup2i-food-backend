-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- 01. ORGANISATION / MULTI-CAMPUS
-- ============================================================================

CREATE TABLE organizations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(150) NOT NULL,
    code            VARCHAR(50) NOT NULL UNIQUE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE campuses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name            VARCHAR(150) NOT NULL,
    code            VARCHAR(50) NOT NULL,
    address         TEXT,
    timezone        VARCHAR(50) NOT NULL DEFAULT 'Africa/Casablanca',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_campuses_org_code UNIQUE (organization_id, code)
);

CREATE INDEX idx_campuses_organization ON campuses(organization_id);

CREATE TABLE locations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campus_id       UUID NOT NULL REFERENCES campuses(id),
    name            VARCHAR(150) NOT NULL,
    code            VARCHAR(50) NOT NULL,
    type            VARCHAR(30) NOT NULL,
    floor           VARCHAR(30),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_locations_campus_code UNIQUE (campus_id, code),
    CONSTRAINT ck_locations_type CHECK (
        type IN ('SNACK','CANTEEN','KITCHEN','STORAGE','OTHER')
    )
);

CREATE INDEX idx_locations_campus ON locations(campus_id);

-- ============================================================================
