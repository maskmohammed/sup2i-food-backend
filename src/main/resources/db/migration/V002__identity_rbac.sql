-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 02. IDENTITÉ / UTILISATEURS / RBAC
-- ============================================================================

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(30),
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_users_status CHECK (
        status IN ('ACTIVE','SUSPENDED','BLOCKED','ARCHIVED')
    )
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_organization ON users(organization_id);

CREATE TABLE students (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL UNIQUE REFERENCES users(id),
    campus_id           UUID NOT NULL REFERENCES campuses(id),
    student_number      VARCHAR(80) NOT NULL UNIQUE,
    program             VARCHAR(150),
    level               VARCHAR(80),
    group_name          VARCHAR(80),
    photo_url           TEXT,
    enrollment_status   VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_students_enrollment_status CHECK (
        enrollment_status IN ('ACTIVE','SUSPENDED','GRADUATED','ARCHIVED')
    )
);

CREATE INDEX idx_students_campus ON students(campus_id);
CREATE INDEX idx_students_number ON students(student_number);

CREATE TABLE student_photos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL REFERENCES students(id),
    photo_url       TEXT NOT NULL,
    source          VARCHAR(30) NOT NULL DEFAULT 'ADMINISTRATION',
    is_current      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_student_current_photo
    ON student_photos(student_id)
    WHERE is_current = TRUE AND revoked_at IS NULL;

CREATE TABLE student_dietary_restrictions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL REFERENCES students(id),
    restriction_type VARCHAR(30) NOT NULL,
    restriction_code VARCHAR(80) NOT NULL,
    note            TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_student_restriction_type CHECK (
        restriction_type IN ('ALLERGEN','DIETARY','OTHER')
    ),
    CONSTRAINT uq_student_restriction UNIQUE(student_id, restriction_type, restriction_code)
);

CREATE TABLE roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(80) NOT NULL UNIQUE,
    name            VARCHAR(120) NOT NULL,
    description     TEXT,
    is_system       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE permissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(120) NOT NULL UNIQUE,
    description     TEXT
);

CREATE TABLE user_roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    role_id         UUID NOT NULL REFERENCES roles(id),
    campus_id       UUID REFERENCES campuses(id),
    location_id     UUID REFERENCES locations(id),
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by     UUID REFERENCES users(id),
    CONSTRAINT ck_user_roles_location_requires_campus
        CHECK (location_id IS NULL OR campus_id IS NOT NULL),
    CONSTRAINT uq_user_roles_scope
        UNIQUE NULLS NOT DISTINCT (user_id, role_id, campus_id, location_id)
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role_id);

CREATE TABLE role_permissions (
    role_id         UUID NOT NULL REFERENCES roles(id),
    permission_id   UUID NOT NULL REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

-- ============================================================================
