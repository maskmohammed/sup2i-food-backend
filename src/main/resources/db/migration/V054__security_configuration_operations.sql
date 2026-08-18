-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.31 SÉCURITÉ, CONFIGURATION ET OPÉRATIONS — DERNIER AUDIT DE COMPLÉTUDE
-- ============================================================================

-- 2FA/MFA pour comptes sensibles (notamment SYSTEM_ADMIN/ADMINISTRATION).
-- Les secrets MFA ne doivent jamais être stockés en clair.
CREATE TABLE user_mfa_methods (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    method_type         VARCHAR(30) NOT NULL,
    label               VARCHAR(120),
    secret_ciphertext   BYTEA,
    external_credential_id VARCHAR(500),
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    is_primary          BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at         TIMESTAMPTZ,
    last_used_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    disabled_at         TIMESTAMPTZ,
    CONSTRAINT ck_user_mfa_methods_type CHECK (
        method_type IN ('TOTP','WEBAUTHN','EMAIL_OTP','OTHER')
    ),
    CONSTRAINT ck_user_mfa_methods_status CHECK (
        status IN ('PENDING','ACTIVE','DISABLED','REVOKED')
    ),
    CONSTRAINT ck_user_mfa_methods_secret CHECK (
        method_type <> 'TOTP' OR secret_ciphertext IS NOT NULL
    )
);

CREATE INDEX idx_user_mfa_methods_user_status
    ON user_mfa_methods(user_id, status);

CREATE UNIQUE INDEX uq_user_mfa_primary
    ON user_mfa_methods(user_id)
    WHERE is_primary = TRUE AND status = 'ACTIVE';

CREATE TABLE user_mfa_recovery_codes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mfa_method_id       UUID NOT NULL REFERENCES user_mfa_methods(id),
    code_hash           VARCHAR(255) NOT NULL UNIQUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at             TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ
);

CREATE INDEX idx_user_mfa_recovery_codes_active
    ON user_mfa_recovery_codes(mfa_method_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;

-- Préférences générales de l'application. Les préférences de notifications
-- restent volontairement dans notification_preferences.
CREATE TABLE user_preferences (
    user_id             UUID PRIMARY KEY REFERENCES users(id),
    locale              VARCHAR(20) NOT NULL DEFAULT 'fr',
    timezone            VARCHAR(80),
    default_location_id UUID REFERENCES locations(id),
    accessibility       JSONB NOT NULL DEFAULT '{}'::JSONB,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_user_preferences_locale CHECK (
        locale ~ '^[A-Za-z]{2,3}([_-][A-Za-z0-9]{2,8})?$'
    )
);

-- Catalogue des paramètres configurables : évite de disperser les valeurs
-- par défaut (expiration commande, max commandes actives, tolérance retrait,
-- seuil stock, deadline Cantine, règles fidélité/promotions, etc.).
CREATE TABLE setting_definitions (
    setting_key         VARCHAR(150) PRIMARY KEY,
    value_type          VARCHAR(20) NOT NULL,
    scope_type          VARCHAR(30) NOT NULL DEFAULT 'ANY',
    default_value       JSONB,
    description         TEXT NOT NULL,
    is_secret           BOOLEAN NOT NULL DEFAULT FALSE,
    is_runtime_editable BOOLEAN NOT NULL DEFAULT TRUE,
    validation_rules    JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_setting_definitions_value_type CHECK (
        value_type IN ('STRING','INTEGER','DECIMAL','BOOLEAN','DURATION','JSON')
    ),
    CONSTRAINT ck_setting_definitions_scope CHECK (
        scope_type IN ('GLOBAL','ORGANIZATION','CAMPUS','LOCATION','ANY')
    )
);

ALTER TABLE system_settings
    ADD CONSTRAINT fk_system_settings_definition
    FOREIGN KEY(setting_key) REFERENCES setting_definitions(setting_key);

-- Exceptions ponctuelles aux horaires normaux : fermeture exceptionnelle,
-- horaire réduit, journée spéciale, etc.
CREATE TABLE location_schedule_exceptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id         UUID NOT NULL REFERENCES locations(id),
    exception_date      DATE NOT NULL,
    is_closed           BOOLEAN NOT NULL DEFAULT FALSE,
    opens_at            TIME,
    closes_at           TIME,
    reason              VARCHAR(200),
    created_by          UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_location_schedule_exception UNIQUE(location_id, exception_date),
    CONSTRAINT ck_location_schedule_exception_time CHECK (
        is_closed = TRUE
        OR (opens_at IS NOT NULL AND closes_at IS NOT NULL AND closes_at > opens_at)
    )
);

-- Transfert de stock : donne une vraie entité de référence aux mouvements
-- TRANSFER_OUT / TRANSFER_IN et garantit la traçabilité entre emplacements.
CREATE TABLE stock_transfers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_stock_location_id UUID NOT NULL REFERENCES stock_locations(id),
    destination_stock_location_id UUID NOT NULL REFERENCES stock_locations(id),
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    requested_by        UUID NOT NULL REFERENCES users(id),
    approved_by         UUID REFERENCES users(id),
    dispatched_by       UUID REFERENCES users(id),
    received_by         UUID REFERENCES users(id),
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_at       TIMESTAMPTZ,
    received_at         TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    reason              TEXT,
    CONSTRAINT ck_stock_transfers_locations CHECK (
        source_stock_location_id <> destination_stock_location_id
    ),
    CONSTRAINT ck_stock_transfers_status CHECK (
        status IN ('DRAFT','APPROVED','IN_TRANSIT','RECEIVED','CANCELLED')
    )
);

CREATE INDEX idx_stock_transfers_source_status
    ON stock_transfers(source_stock_location_id, status, requested_at DESC);

CREATE INDEX idx_stock_transfers_destination_status
    ON stock_transfers(destination_stock_location_id, status, requested_at DESC);

CREATE TABLE stock_transfer_lines (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_transfer_id   UUID NOT NULL REFERENCES stock_transfers(id),
    stock_item_id       UUID NOT NULL REFERENCES stock_items(id),
    quantity            NUMERIC(14,3) NOT NULL,
    unit                VARCHAR(20) NOT NULL,
    transfer_out_movement_id UUID REFERENCES inventory_movements(id),
    transfer_in_movement_id  UUID REFERENCES inventory_movements(id),
    CONSTRAINT uq_stock_transfer_line UNIQUE(stock_transfer_id, stock_item_id),
    CONSTRAINT ck_stock_transfer_lines_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_transfer_lines_unit CHECK (
        unit IN ('PIECE','GRAM','KILOGRAM','MILLILITER','LITER')
    )
);

-- Historique explicite de la state machine Refund. L'audit global reste en plus.
CREATE TABLE refund_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    refund_id           UUID NOT NULL REFERENCES refunds(id),
    event_type          VARCHAR(50) NOT NULL,
    status_before       VARCHAR(30),
    status_after        VARCHAR(30),
    performed_by        UUID REFERENCES users(id),
    external_reference  VARCHAR(160),
    payload             JSONB,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refund_events_refund_time
    ON refund_events(refund_id, occurred_at);

-- Politique de conservation des données demandée par la gouvernance.
CREATE TABLE data_retention_policies (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    resource_type       VARCHAR(80) NOT NULL,
    retention_days      INTEGER,
    action              VARCHAR(30) NOT NULL,
    legal_basis_note    TEXT,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by          UUID NOT NULL REFERENCES users(id),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_data_retention_policy UNIQUE(organization_id, resource_type),
    CONSTRAINT ck_data_retention_days CHECK (
        retention_days IS NULL OR retention_days > 0
    ),
    CONSTRAINT ck_data_retention_action CHECK (
        action IN ('KEEP','ARCHIVE','ANONYMIZE','DELETE')
    )
);

-- Un paramètre secret contient une référence vers un secret manager/env, jamais le secret brut.
ALTER TABLE system_settings ALTER COLUMN value DROP NOT NULL;
ALTER TABLE system_settings ADD COLUMN secret_ref VARCHAR(500);
ALTER TABLE system_settings ADD CONSTRAINT ck_system_settings_value_or_secret CHECK (
    num_nonnulls(value, secret_ref) = 1
);

-- Corrélation technique↔audit métier pour investigation sans stocker les logs
-- applicatifs eux-mêmes dans PostgreSQL.
ALTER TABLE audit_logs ADD COLUMN trace_id VARCHAR(100);
CREATE INDEX idx_audit_logs_trace_id
    ON audit_logs(trace_id)
    WHERE trace_id IS NOT NULL;

-- Les secrets des connecteurs ne vivent pas dans config JSON.
ALTER TABLE integration_connectors ADD COLUMN secret_ref VARCHAR(500);

-- Les totaux par moyen de paiement sont normalisés dans pos_session_tender_totals.
ALTER TABLE pos_sessions DROP COLUMN card_total;

-- Le tableau historique libre reste disponible uniquement pour les restrictions
-- non couvertes par les référentiels normalisés allergènes / dietary tags.
ALTER TABLE student_dietary_restrictions
    DROP CONSTRAINT ck_student_restriction_type;
ALTER TABLE student_dietary_restrictions
    ADD CONSTRAINT ck_student_restriction_type CHECK (
        restriction_type IN ('OTHER','MEDICAL_NOTE')
    );

-- Le cahier détaillé distingue les ingrédients suivis ou non en stock.
-- Le seuil quantitatif reste normalisé dans stock_items pour éviter une duplication.
ALTER TABLE ingredients ADD COLUMN track_stock BOOLEAN NOT NULL DEFAULT TRUE;

-- StockMovement final : deltas signés séparés physique/réservé.
-- Cela représente sans ambiguïté RESERVATION (+reserved), RELEASE (-reserved),
-- vente/consommation (-physical, éventuellement -reserved) et entrée (+physical).
ALTER TABLE inventory_movements RENAME COLUMN quantity TO physical_delta;
ALTER TABLE inventory_movements DROP CONSTRAINT ck_inventory_movements_quantity;
ALTER TABLE inventory_movements ALTER COLUMN physical_delta SET DEFAULT 0;
ALTER TABLE inventory_movements ADD COLUMN reserved_delta NUMERIC(14,3) NOT NULL DEFAULT 0;
ALTER TABLE inventory_movements ADD CONSTRAINT ck_inventory_movements_nonzero_delta CHECK (
    physical_delta <> 0 OR reserved_delta <> 0
);

-- Normalisation finale identité/photo : une seule source de vérité.
ALTER TABLE students DROP COLUMN photo_url;

-- Une identité externe doit être révocable et son subject fournisseur stable
-- ne peut être rattaché à deux comptes.
ALTER TABLE auth_identities ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE auth_identities ADD COLUMN revoked_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_auth_identity_provider_subject
    ON auth_identities(provider_code, provider_subject)
    WHERE provider_subject IS NOT NULL;
CREATE UNIQUE INDEX uq_auth_identity_primary
    ON auth_identities(user_id)
    WHERE is_primary = TRUE AND is_active = TRUE;

-- Un seul credential actif d'un même usage pour un même sujet.
CREATE UNIQUE INDEX uq_qr_credentials_active_subject
    ON qr_credentials(credential_type, subject_id)
    WHERE status = 'ACTIVE';

COMMENT ON TABLE user_mfa_methods IS
'2FA/MFA des comptes sensibles. Les secrets TOTP doivent être chiffrés avec une clé hors base ou remplacés par un secret_ref/credential externe.';

COMMENT ON TABLE setting_definitions IS
'Catalogue normatif des paramètres métier. system_settings contient seulement les overrides par organisation/campus/location.';

COMMENT ON TABLE data_retention_policies IS
'Politique de conservation à valider avec SUP2I; la suppression/anonymisation effective doit être réalisée par des jobs audités.';


-- ============================================================================
-- 23.32 AUDIT DE COMPLÉTUDE ULTIME — PRODUCTION / ROUTAGE CUISINE /
--       FORMULES PERSONNEL-ÉVÉNEMENT / VERSIONNAGE ABONNEMENTS
-- ============================================================================
--
-- Ce bloc ferme les derniers écarts trouvés en recroisant le MASTER complet
-- avec le schéma physique:
--   • MASTER §9.5 / §22.10 / §29.2 : quantité réellement préparée;
--   • MASTER §22.4 : formule personnel + formule événement;
--   • MASTER §13.2 : historique fiable des conditions souscrites;
--   • MASTER §8 / §26 + architecture multi-point : routage vers cuisine et
--     granularité des lignes KDS.
--
-- Ces structures sont persistantes; les calculs de queue/ETA restent dérivés.

-- --------------------------------------------------------------------------
