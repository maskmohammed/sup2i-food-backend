-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 18. GASPILLAGE
-- ============================================================================

CREATE TABLE waste_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_item_id       UUID NOT NULL REFERENCES stock_items(id),
    stock_location_id   UUID NOT NULL REFERENCES stock_locations(id),
    quantity            NUMERIC(14,3) NOT NULL,
    unit                VARCHAR(20) NOT NULL,
    reason              VARCHAR(40) NOT NULL,
    estimated_cost      NUMERIC(12,2),
    notes               TEXT,
    recorded_by         UUID NOT NULL REFERENCES users(id),
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    inventory_movement_id UUID REFERENCES inventory_movements(id),
    CONSTRAINT ck_waste_records_quantity CHECK (quantity > 0),
    CONSTRAINT ck_waste_records_unit CHECK (
        unit IN ('PIECE','GRAM','KILOGRAM','MILLILITER','LITER')
    ),
    CONSTRAINT ck_waste_records_reason CHECK (
        reason IN (
            'UNSOLD','EXPIRED','OVERPRODUCTION','KITCHEN_ERROR',
            'NO_SHOW','DAMAGED','BREAKAGE','OTHER'
        )
    ),
    CONSTRAINT ck_waste_records_cost CHECK (
        estimated_cost IS NULL OR estimated_cost >= 0
    )
);

CREATE INDEX idx_waste_records_item_date
    ON waste_records(stock_item_id, recorded_at DESC);

-- ============================================================================
