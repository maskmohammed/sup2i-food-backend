-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.16 Intégrations : Cactus/PHP, ERP étudiants, TPE, online, imports
-- --------------------------------------------------------------------------

CREATE TABLE integration_connectors (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    code            VARCHAR(80) NOT NULL,
    connector_type  VARCHAR(40) NOT NULL,
    direction       VARCHAR(20) NOT NULL DEFAULT 'BIDIRECTIONAL',
    status          VARCHAR(30) NOT NULL DEFAULT 'DISABLED',
    config          JSONB NOT NULL DEFAULT '{}'::JSONB,
    last_success_at TIMESTAMPTZ,
    last_error_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_integration_connectors_org_code UNIQUE(organization_id, code),
    CONSTRAINT ck_integration_connectors_type CHECK (
        connector_type IN (
            'CACTUS_POS','STUDENT_ERP','TPE','ONLINE_PAYMENT',
            'FILE_IMPORT','EMAIL','PUSH','OTHER'
        )
    ),
    CONSTRAINT ck_integration_connectors_direction CHECK (
        direction IN ('INBOUND','OUTBOUND','BIDIRECTIONAL')
    ),
    CONSTRAINT ck_integration_connectors_status CHECK (
        status IN ('DISABLED','ACTIVE','ERROR','PAUSED')
    )
);

CREATE TABLE external_entity_refs (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_id         UUID NOT NULL REFERENCES integration_connectors(id),
    local_entity_type    VARCHAR(80) NOT NULL,
    local_entity_id      UUID NOT NULL,
    external_entity_type VARCHAR(80),
    external_id          VARCHAR(255) NOT NULL,
    external_version     VARCHAR(120),
    last_synced_at       TIMESTAMPTZ,
    metadata             JSONB,
    CONSTRAINT uq_external_entity_ref UNIQUE(
        connector_id, local_entity_type, local_entity_id
    ),
    CONSTRAINT uq_external_entity_external UNIQUE(
        connector_id, external_entity_type, external_id
    )
);

CREATE TABLE integration_sync_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_id    UUID NOT NULL REFERENCES integration_connectors(id),
    sync_type       VARCHAR(50) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'RUNNING',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMPTZ,
    processed_count INTEGER NOT NULL DEFAULT 0,
    success_count   INTEGER NOT NULL DEFAULT 0,
    failure_count   INTEGER NOT NULL DEFAULT 0,
    initiated_by    UUID REFERENCES users(id),
    error_summary   TEXT,
    CONSTRAINT ck_integration_sync_runs_status CHECK (
        status IN ('RUNNING','COMPLETED','PARTIAL','FAILED','CANCELLED')
    ),
    CONSTRAINT ck_integration_sync_runs_counts CHECK (
        processed_count >= 0 AND success_count >= 0 AND failure_count >= 0
    )
);

CREATE TABLE integration_sync_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sync_run_id     UUID NOT NULL REFERENCES integration_sync_runs(id),
    entity_type     VARCHAR(80),
    external_id     VARCHAR(255),
    local_entity_id UUID,
    status          VARCHAR(30) NOT NULL,
    action          VARCHAR(30),
    error_code      VARCHAR(100),
    error_message   TEXT,
    payload         JSONB,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_integration_sync_items_status CHECK (
        status IN ('SUCCESS','SKIPPED','FAILED','CONFLICT')
    )
);

CREATE TABLE integration_inbox_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_id      UUID NOT NULL REFERENCES integration_connectors(id),
    external_event_id VARCHAR(200),
    event_type        VARCHAR(120) NOT NULL,
    payload           JSONB NOT NULL,
    status            VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    received_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at      TIMESTAMPTZ,
    retry_count       INTEGER NOT NULL DEFAULT 0,
    last_error        TEXT,
    CONSTRAINT ck_integration_inbox_events_status CHECK (
        status IN ('RECEIVED','PROCESSING','PROCESSED','FAILED','IGNORED')
    ),
    CONSTRAINT ck_integration_inbox_events_retry CHECK (retry_count >= 0)
);

CREATE UNIQUE INDEX uq_integration_inbox_external_event
    ON integration_inbox_events(connector_id, external_event_id)
    WHERE external_event_id IS NOT NULL;

CREATE TABLE import_jobs (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id      UUID NOT NULL REFERENCES organizations(id),
    import_type          VARCHAR(40) NOT NULL,
    source_file_asset_id UUID REFERENCES file_assets(id),
    status               VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    requested_by         UUID NOT NULL REFERENCES users(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at           TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    total_rows           INTEGER NOT NULL DEFAULT 0,
    success_rows         INTEGER NOT NULL DEFAULT 0,
    failed_rows          INTEGER NOT NULL DEFAULT 0,
    error_summary        TEXT,
    CONSTRAINT ck_import_jobs_type CHECK (
        import_type IN ('STUDENTS','PRODUCTS','STOCK','SUPPLIERS','OTHER')
    ),
    CONSTRAINT ck_import_jobs_status CHECK (
        status IN ('PENDING','VALIDATING','PROCESSING','COMPLETED','PARTIAL','FAILED','CANCELLED')
    ),
    CONSTRAINT ck_import_jobs_counts CHECK (
        total_rows >= 0 AND success_rows >= 0 AND failed_rows >= 0
    )
);

CREATE TABLE import_job_rows (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    import_job_id   UUID NOT NULL REFERENCES import_jobs(id),
    row_number      INTEGER NOT NULL,
    raw_data        JSONB NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    local_entity_id UUID,
    error_code      VARCHAR(100),
    error_message   TEXT,
    processed_at    TIMESTAMPTZ,
    CONSTRAINT uq_import_job_row UNIQUE(import_job_id, row_number),
    CONSTRAINT ck_import_job_rows_number CHECK (row_number > 0),
    CONSTRAINT ck_import_job_rows_status CHECK (
        status IN ('PENDING','SUCCESS','FAILED','SKIPPED')
    )
);

-- --------------------------------------------------------------------------
