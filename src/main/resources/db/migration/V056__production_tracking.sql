-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.32.2 Production réelle : préparé -> vendu/distribué -> gaspillé
-- --------------------------------------------------------------------------

CREATE TABLE production_runs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    campus_id           UUID NOT NULL REFERENCES campuses(id),
    service_location_id UUID NOT NULL REFERENCES locations(id),
    kitchen_location_id UUID NOT NULL REFERENCES locations(id),
    canteen_menu_id     UUID REFERENCES canteen_menus(id),
    campus_event_id     UUID REFERENCES campus_events(id),
    production_date     DATE NOT NULL,
    production_type     VARCHAR(30) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'PLANNED',
    target_source       VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    created_by          UUID NOT NULL REFERENCES users(id),
    approved_by         UUID REFERENCES users(id),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_production_runs_type CHECK (
        production_type IN (
            'CANTEEN_BATCH','SNACK_BATCH','EVENT_BATCH','OTHER'
        )
    ),
    CONSTRAINT ck_production_runs_status CHECK (
        status IN ('PLANNED','IN_PROGRESS','COMPLETED','CANCELLED')
    ),
    CONSTRAINT ck_production_runs_target_source CHECK (
        target_source IN (
            'MANUAL','RESERVATIONS','FORECAST','HISTORICAL','MIXED'
        )
    )
);

CREATE INDEX idx_production_runs_service_date
    ON production_runs(service_location_id, production_date, status);

CREATE INDEX idx_production_runs_canteen_menu
    ON production_runs(canteen_menu_id)
    WHERE canteen_menu_id IS NOT NULL;

CREATE TABLE production_run_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    production_run_id   UUID NOT NULL REFERENCES production_runs(id),
    product_id          UUID NOT NULL REFERENCES products(id),
    variant_id          UUID REFERENCES product_variants(id),
    recipe_id           UUID REFERENCES recipes(id),
    target_quantity     NUMERIC(14,3) NOT NULL DEFAULT 0,
    prepared_quantity   NUMERIC(14,3) NOT NULL DEFAULT 0,
    unit                VARCHAR(20) NOT NULL DEFAULT 'PIECE',
    estimated_unit_cost NUMERIC(12,4),
    preparation_started_at TIMESTAMPTZ,
    preparation_completed_at TIMESTAMPTZ,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_production_run_items_variant_product
        FOREIGN KEY(variant_id, product_id)
        REFERENCES product_variants(id, product_id),
    CONSTRAINT ck_production_run_items_target CHECK (target_quantity >= 0),
    CONSTRAINT ck_production_run_items_prepared CHECK (prepared_quantity >= 0),
    CONSTRAINT ck_production_run_items_unit CHECK (
        unit IN ('PIECE','GRAM','KILOGRAM','MILLILITER','LITER')
    ),
    CONSTRAINT ck_production_run_items_cost CHECK (
        estimated_unit_cost IS NULL OR estimated_unit_cost >= 0
    ),
    CONSTRAINT uq_production_run_item UNIQUE NULLS NOT DISTINCT (
        production_run_id, product_id, variant_id
    )
);

CREATE INDEX idx_production_run_items_run
    ON production_run_items(production_run_id);

CREATE TABLE production_run_movements (
    production_run_item_id UUID NOT NULL REFERENCES production_run_items(id),
    inventory_movement_id  UUID NOT NULL UNIQUE REFERENCES inventory_movements(id),
    movement_role          VARCHAR(30) NOT NULL,
    PRIMARY KEY(production_run_item_id, inventory_movement_id),
    CONSTRAINT ck_production_run_movements_role CHECK (
        movement_role IN ('CONSUMPTION','RETURN','WASTE','ADJUSTMENT')
    )
);

-- Une vente/distribution peut être rattachée au lot de production qui l'a
-- effectivement fournie. Cela rend le KPI préparé/distribué/gaspillé exact,
-- même si plusieurs productions ont lieu le même jour.
CREATE TABLE production_allocations (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    production_run_item_id  UUID NOT NULL REFERENCES production_run_items(id),
    order_item_id           UUID REFERENCES order_items(id),
    meal_usage_id           UUID REFERENCES meal_usages(id),
    quantity                NUMERIC(14,3) NOT NULL,
    allocated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    allocated_by            UUID REFERENCES users(id),
    CONSTRAINT ck_production_allocations_target CHECK (
        num_nonnulls(order_item_id, meal_usage_id) = 1
    ),
    CONSTRAINT ck_production_allocations_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_production_allocations_run_item
    ON production_allocations(production_run_item_id);

CREATE INDEX idx_production_allocations_order_item
    ON production_allocations(order_item_id)
    WHERE order_item_id IS NOT NULL;

CREATE INDEX idx_production_allocations_meal_usage
    ON production_allocations(meal_usage_id)
    WHERE meal_usage_id IS NOT NULL;

ALTER TABLE waste_records
    ADD COLUMN production_run_item_id UUID REFERENCES production_run_items(id);

CREATE INDEX idx_waste_records_production_run_item
    ON waste_records(production_run_item_id)
    WHERE production_run_item_id IS NOT NULL;

-- --------------------------------------------------------------------------
