-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 06. STOCK
-- ============================================================================

CREATE TABLE stock_locations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id     UUID NOT NULL REFERENCES locations(id),
    name            VARCHAR(120) NOT NULL,
    type            VARCHAR(30) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_stock_locations_type CHECK (
        type IN ('COUNTER','STORAGE','FRIDGE','FREEZER','KITCHEN','OTHER')
    )
);

CREATE INDEX idx_stock_locations_location ON stock_locations(location_id);

CREATE TABLE stock_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    product_id          UUID REFERENCES products(id),
    ingredient_id       UUID REFERENCES ingredients(id),
    base_unit           VARCHAR(20) NOT NULL,
    low_stock_threshold NUMERIC(14,3),
    track_expiry        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_stock_items_subject CHECK (
        (product_id IS NOT NULL AND ingredient_id IS NULL)
        OR
        (product_id IS NULL AND ingredient_id IS NOT NULL)
    ),
    CONSTRAINT ck_stock_items_unit CHECK (
        base_unit IN ('PIECE','GRAM','KILOGRAM','MILLILITER','LITER')
    ),
    CONSTRAINT ck_stock_items_threshold CHECK (
        low_stock_threshold IS NULL OR low_stock_threshold >= 0
    )
);

CREATE UNIQUE INDEX uq_stock_items_product
    ON stock_items(organization_id, product_id)
    WHERE product_id IS NOT NULL;

CREATE UNIQUE INDEX uq_stock_items_ingredient
    ON stock_items(organization_id, ingredient_id)
    WHERE ingredient_id IS NOT NULL;

CREATE TABLE stock_balances (
    stock_item_id       UUID NOT NULL REFERENCES stock_items(id),
    stock_location_id   UUID NOT NULL REFERENCES stock_locations(id),
    physical_quantity   NUMERIC(14,3) NOT NULL DEFAULT 0,
    reserved_quantity   NUMERIC(14,3) NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (stock_item_id, stock_location_id),
    CONSTRAINT ck_stock_balances_physical CHECK (physical_quantity >= 0),
    CONSTRAINT ck_stock_balances_reserved CHECK (reserved_quantity >= 0),
    CONSTRAINT ck_stock_balances_reserved_physical CHECK (
        reserved_quantity <= physical_quantity
    )
);

CREATE TABLE inventory_movements (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_item_id       UUID NOT NULL REFERENCES stock_items(id),
    stock_location_id   UUID NOT NULL REFERENCES stock_locations(id),
    movement_type       VARCHAR(40) NOT NULL,
    quantity            NUMERIC(14,3) NOT NULL,
    unit                VARCHAR(20) NOT NULL,
    unit_cost           NUMERIC(12,2),
    reference_type      VARCHAR(40),
    reference_id        UUID,
    reason              VARCHAR(150),
    comment             TEXT,
    performed_by        UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inventory_movements_type CHECK (
        movement_type IN (
            'PURCHASE_IN','SALE_OUT','RECIPE_CONSUMPTION','RESERVATION',
            'RESERVATION_RELEASE','WASTE','ADJUSTMENT','TRANSFER_IN',
            'TRANSFER_OUT','RETURN_IN'
        )
    ),
    CONSTRAINT ck_inventory_movements_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inventory_movements_unit CHECK (
        unit IN ('PIECE','GRAM','KILOGRAM','MILLILITER','LITER')
    ),
    CONSTRAINT ck_inventory_movements_cost CHECK (
        unit_cost IS NULL OR unit_cost >= 0
    )
);

CREATE INDEX idx_inventory_movements_item_created
    ON inventory_movements(stock_item_id, created_at DESC);

CREATE INDEX idx_inventory_movements_reference
    ON inventory_movements(reference_type, reference_id);

CREATE TABLE stock_lots (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_item_id       UUID NOT NULL REFERENCES stock_items(id),
    stock_location_id   UUID NOT NULL REFERENCES stock_locations(id),
    lot_number          VARCHAR(120),
    supplier_id         UUID REFERENCES suppliers(id),
    received_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMPTZ,
    quantity_received   NUMERIC(14,3) NOT NULL,
    quantity_remaining  NUMERIC(14,3) NOT NULL,
    unit_cost           NUMERIC(12,2),
    CONSTRAINT ck_stock_lots_received CHECK (quantity_received > 0),
    CONSTRAINT ck_stock_lots_remaining CHECK (
        quantity_remaining >= 0 AND quantity_remaining <= quantity_received
    ),
    CONSTRAINT ck_stock_lots_cost CHECK (unit_cost IS NULL OR unit_cost >= 0)
);

CREATE INDEX idx_stock_lots_expiry
    ON stock_lots(expires_at)
    WHERE expires_at IS NOT NULL AND quantity_remaining > 0;

CREATE TABLE stock_alerts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_item_id       UUID NOT NULL REFERENCES stock_items(id),
    stock_location_id   UUID REFERENCES stock_locations(id),
    alert_type          VARCHAR(40) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    threshold_value     NUMERIC(14,3),
    observed_value      NUMERIC(14,3),
    lot_id              UUID REFERENCES stock_lots(id),
    detected_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_by     UUID REFERENCES users(id),
    acknowledged_at     TIMESTAMPTZ,
    resolved_at         TIMESTAMPTZ,
    metadata            JSONB,
    CONSTRAINT ck_stock_alerts_type CHECK (
        alert_type IN (
            'LOW_STOCK','OUT_OF_STOCK','EXPIRY','UNUSUAL_CONSUMPTION','REORDER'
        )
    ),
    CONSTRAINT ck_stock_alerts_status CHECK (
        status IN ('OPEN','ACKNOWLEDGED','RESOLVED')
    )
);

CREATE INDEX idx_stock_alerts_open
    ON stock_alerts(status, detected_at DESC);

-- ============================================================================
