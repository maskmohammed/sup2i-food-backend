-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.4 Stock par variante + inventaire physique + réceptions fournisseurs
-- --------------------------------------------------------------------------

ALTER TABLE stock_items ADD COLUMN variant_id UUID REFERENCES product_variants(id);
ALTER TABLE stock_items DROP CONSTRAINT ck_stock_items_subject;
ALTER TABLE stock_items ADD CONSTRAINT ck_stock_items_subject CHECK (
    num_nonnulls(product_id, variant_id, ingredient_id) = 1
);

CREATE UNIQUE INDEX uq_stock_items_variant
    ON stock_items(organization_id, variant_id)
    WHERE variant_id IS NOT NULL;

ALTER TABLE stock_alerts ADD COLUMN severity VARCHAR(30) NOT NULL DEFAULT 'LOW';
ALTER TABLE stock_alerts ADD CONSTRAINT ck_stock_alerts_severity CHECK (
    severity IN ('INFO','LOW','CRITICAL','OUT_OF_STOCK')
);

CREATE TABLE inventory_movement_lots (
    inventory_movement_id UUID NOT NULL REFERENCES inventory_movements(id),
    stock_lot_id          UUID NOT NULL REFERENCES stock_lots(id),
    quantity_delta        NUMERIC(14,3) NOT NULL,
    PRIMARY KEY(inventory_movement_id, stock_lot_id),
    CONSTRAINT ck_inventory_movement_lots_delta CHECK (quantity_delta <> 0)
);

CREATE TABLE inventory_sessions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_location_id UUID NOT NULL REFERENCES stock_locations(id),
    status            VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    started_by        UUID NOT NULL REFERENCES users(id),
    started_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_by      UUID REFERENCES users(id),
    completed_at      TIMESTAMPTZ,
    applied_by        UUID REFERENCES users(id),
    applied_at        TIMESTAMPTZ,
    notes             TEXT,
    CONSTRAINT ck_inventory_sessions_status CHECK (
        status IN ('OPEN','COUNTING','COMPLETED','APPLIED','CANCELLED')
    )
);

CREATE INDEX idx_inventory_sessions_location_status
    ON inventory_sessions(stock_location_id, status);

CREATE TABLE inventory_count_lines (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_session_id     UUID NOT NULL REFERENCES inventory_sessions(id),
    stock_item_id            UUID NOT NULL REFERENCES stock_items(id),
    system_physical_quantity NUMERIC(14,3) NOT NULL,
    system_reserved_quantity NUMERIC(14,3) NOT NULL,
    counted_quantity         NUMERIC(14,3),
    difference_quantity      NUMERIC(14,3),
    counted_by               UUID REFERENCES users(id),
    counted_at               TIMESTAMPTZ,
    adjustment_movement_id   UUID REFERENCES inventory_movements(id),
    reason                   TEXT,
    CONSTRAINT uq_inventory_count_line UNIQUE(inventory_session_id, stock_item_id),
    CONSTRAINT ck_inventory_count_physical CHECK (system_physical_quantity >= 0),
    CONSTRAINT ck_inventory_count_reserved CHECK (system_reserved_quantity >= 0),
    CONSTRAINT ck_inventory_count_counted CHECK (
        counted_quantity IS NULL OR counted_quantity >= 0
    )
);

CREATE TABLE stock_receipts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_location_id UUID NOT NULL REFERENCES stock_locations(id),
    supplier_id       UUID REFERENCES suppliers(id),
    purchase_order_id UUID REFERENCES purchase_orders(id),
    receipt_reference VARCHAR(100),
    received_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_by       UUID NOT NULL REFERENCES users(id),
    status            VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_stock_receipts_status CHECK (
        status IN ('DRAFT','RECEIVED','CANCELLED')
    )
);

CREATE INDEX idx_stock_receipts_purchase_order
    ON stock_receipts(purchase_order_id);

CREATE TABLE stock_receipt_lines (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_receipt_id      UUID NOT NULL REFERENCES stock_receipts(id),
    stock_item_id         UUID NOT NULL REFERENCES stock_items(id),
    purchase_order_item_id UUID REFERENCES purchase_order_items(id),
    quantity              NUMERIC(14,3) NOT NULL,
    unit                  VARCHAR(20) NOT NULL,
    unit_cost             NUMERIC(12,4),
    lot_number            VARCHAR(120),
    expires_at            TIMESTAMPTZ,
    generated_lot_id      UUID REFERENCES stock_lots(id),
    inventory_movement_id UUID REFERENCES inventory_movements(id),
    CONSTRAINT ck_stock_receipt_lines_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_receipt_lines_unit CHECK (
        unit IN ('PIECE','GRAM','KILOGRAM','MILLILITER','LITER')
    ),
    CONSTRAINT ck_stock_receipt_lines_cost CHECK (
        unit_cost IS NULL OR unit_cost >= 0
    )
);

-- --------------------------------------------------------------------------
