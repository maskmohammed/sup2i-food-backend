-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.15 ReportSnapshot + exports Direction/Finance
-- --------------------------------------------------------------------------

CREATE TABLE report_snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    campus_id       UUID REFERENCES campuses(id),
    location_id     UUID REFERENCES locations(id),
    report_type     VARCHAR(60) NOT NULL,
    period_start    TIMESTAMPTZ NOT NULL,
    period_end      TIMESTAMPTZ NOT NULL,
    data            JSONB NOT NULL,
    generated_by    UUID REFERENCES users(id),
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_report_snapshots_period CHECK (period_end > period_start)
);

CREATE INDEX idx_report_snapshots_lookup
    ON report_snapshots(organization_id, report_type, period_start, period_end);

CREATE TABLE file_asset_links (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_asset_id UUID NOT NULL REFERENCES file_assets(id),
    resource_type VARCHAR(80) NOT NULL,
    resource_id   UUID NOT NULL,
    purpose       VARCHAR(80),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_file_asset_link UNIQUE(
        file_asset_id, resource_type, resource_id, purpose
    )
);

CREATE TABLE report_exports (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_snapshot_id UUID REFERENCES report_snapshots(id),
    organization_id    UUID NOT NULL REFERENCES organizations(id),
    export_type        VARCHAR(20) NOT NULL,
    status             VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    requested_by       UUID NOT NULL REFERENCES users(id),
    requested_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at       TIMESTAMPTZ,
    file_asset_id      UUID REFERENCES file_assets(id),
    parameters         JSONB,
    error_message      TEXT,
    CONSTRAINT ck_report_exports_type CHECK (
        export_type IN ('CSV','XLSX','PDF')
    ),
    CONSTRAINT ck_report_exports_status CHECK (
        status IN ('PENDING','PROCESSING','COMPLETED','FAILED')
    )
);

-- --------------------------------------------------------------------------
