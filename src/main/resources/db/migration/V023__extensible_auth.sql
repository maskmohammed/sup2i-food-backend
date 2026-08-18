-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.1 Authentification extensible : local + identité institutionnelle
-- --------------------------------------------------------------------------

ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE auth_identities (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    provider_type       VARCHAR(30) NOT NULL,
    provider_code       VARCHAR(80) NOT NULL,
    provider_subject    VARCHAR(255),
    login_identifier    VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(255),
    is_verified         BOOLEAN NOT NULL DEFAULT FALSE,
    is_primary          BOOLEAN NOT NULL DEFAULT FALSE,
    last_used_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_auth_identities_provider_type CHECK (
        provider_type IN ('LOCAL','INSTITUTIONAL','OIDC','SAML','LDAP','OTHER')
    ),
    CONSTRAINT ck_auth_identity_local_password CHECK (
        provider_type <> 'LOCAL' OR password_hash IS NOT NULL
    ),
    CONSTRAINT uq_auth_identity_provider_login UNIQUE(provider_code, login_identifier)
);

CREATE INDEX idx_auth_identities_user ON auth_identities(user_id);

CREATE TABLE password_reset_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    CONSTRAINT ck_password_reset_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX idx_password_reset_tokens_active
    ON password_reset_tokens(user_id, expires_at)
    WHERE used_at IS NULL AND revoked_at IS NULL;

CREATE TABLE auth_login_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id),
    identifier_hash VARCHAR(128),
    result          VARCHAR(30) NOT NULL,
    failure_reason  VARCHAR(100),
    ip_address      INET,
    user_agent      TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_auth_login_events_result CHECK (
        result IN ('SUCCESS','FAILED','BLOCKED','RATE_LIMITED')
    )
);

CREATE INDEX idx_auth_login_events_user_time
    ON auth_login_events(user_id, occurred_at DESC);

-- --------------------------------------------------------------------------
