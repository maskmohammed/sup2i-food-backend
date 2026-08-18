-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 19. ACHATS / BONS DE COMMANDE
-- ============================================================================

CREATE TABLE purchase_orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id     UUID NOT NULL REFERENCES suppliers(id),
    campus_id       UUID NOT NULL REFERENCES campuses(id),
    reference       VARCHAR(80) NOT NULL UNIQUE,
    status          VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    total_estimated NUMERIC(12,2),
    created_by      UUID NOT NULL REFERENCES users(id),
    approved_by     UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_purchase_orders_status CHECK (
        status IN (
            'DRAFT','SUBMITTED','APPROVED','ORDERED',
            'PARTIALLY_RECEIVED','RECEIVED','CANCELLED'
        )
    ),
    CONSTRAINT ck_purchase_orders_total CHECK (
        total_estimated IS NULL OR total_estimated >= 0
    )
);

CREATE TABLE purchase_order_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id   UUID NOT NULL REFERENCES purchase_orders(id),
    stock_item_id       UUID NOT NULL REFERENCES stock_items(id),
    quantity            NUMERIC(14,3) NOT NULL,
    unit                VARCHAR(20) NOT NULL,
    unit_price          NUMERIC(12,2),
    CONSTRAINT ck_purchase_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_order_items_unit CHECK (
        unit IN ('PIECE','GRAM','KILOGRAM','MILLILITER','LITER')
    ),
    CONSTRAINT ck_purchase_order_items_price CHECK (
        unit_price IS NULL OR unit_price >= 0
    )
);

-- ============================================================================
