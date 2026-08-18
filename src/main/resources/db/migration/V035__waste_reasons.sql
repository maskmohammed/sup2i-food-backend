-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.13 WasteReason explicite + motifs opérationnels complets
-- --------------------------------------------------------------------------

CREATE TABLE waste_reasons (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organizations(id),
    code             VARCHAR(80) NOT NULL,
    name             VARCHAR(150) NOT NULL,
    category         VARCHAR(40) NOT NULL,
    requires_comment BOOLEAN NOT NULL DEFAULT FALSE,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_waste_reasons_org_code UNIQUE(organization_id, code),
    CONSTRAINT ck_waste_reasons_category CHECK (
        category IN (
            'UNSOLD','EXPIRED','OVERPRODUCTION','KITCHEN_ERROR',
            'RETURN','NO_SHOW','DAMAGED','BREAKAGE','CONTAMINATION',
            'STOCK_ERROR','OTHER'
        )
    )
);

ALTER TABLE waste_records ADD COLUMN waste_reason_id UUID REFERENCES waste_reasons(id);
ALTER TABLE waste_records ADD COLUMN product_id UUID REFERENCES products(id);
ALTER TABLE waste_records ADD COLUMN order_item_id UUID REFERENCES order_items(id);

-- L'ancien champ reason reste temporairement présent comme snapshot texte pour
-- préserver la lisibilité de l'historique. Les nouvelles écritures utilisent la FK.

-- --------------------------------------------------------------------------
