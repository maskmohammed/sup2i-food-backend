-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 08. COMMANDES
-- ============================================================================

CREATE TABLE orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    campus_id           UUID NOT NULL REFERENCES campuses(id),
    location_id         UUID NOT NULL REFERENCES locations(id),
    student_id          UUID REFERENCES students(id),
    order_number        VARCHAR(40) NOT NULL,
    business_date       DATE NOT NULL,
    source              VARCHAR(30) NOT NULL,
    status              VARCHAR(40) NOT NULL,
    slot_id             UUID REFERENCES time_slots(id),
    subtotal            NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_total      NUMERIC(12,2) NOT NULL DEFAULT 0,
    total               NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency            CHAR(3) NOT NULL DEFAULT 'MAD',
    payment_expires_at  TIMESTAMPTZ,
    paid_at             TIMESTAMPTZ,
    ready_at            TIMESTAMPTZ,
    collected_at        TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    version             INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_orders_public_number UNIQUE(location_id, business_date, order_number),
    CONSTRAINT ck_orders_source CHECK (source IN ('MOBILE','POS','ADMIN')),
    CONSTRAINT ck_orders_status CHECK (
        status IN (
            'DRAFT','CREATED','AWAITING_PAYMENT','PAID','QUEUED',
            'PREPARING','READY','COLLECTED','COMPLETED','CANCELLED',
            'EXPIRED','REFUNDED','NO_SHOW'
        )
    ),
    CONSTRAINT ck_orders_subtotal CHECK (subtotal >= 0),
    CONSTRAINT ck_orders_discount CHECK (discount_total >= 0),
    CONSTRAINT ck_orders_total CHECK (total >= 0),
    CONSTRAINT ck_orders_version CHECK (version >= 0)
);

CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_student_created ON orders(student_id, created_at DESC);
CREATE INDEX idx_orders_location_status ON orders(location_id, status);
CREATE INDEX idx_orders_slot ON orders(slot_id);
CREATE INDEX idx_orders_number ON orders(order_number);

CREATE TABLE order_items (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                UUID NOT NULL REFERENCES orders(id),
    product_id              UUID NOT NULL REFERENCES products(id),
    variant_id              UUID REFERENCES product_variants(id),
    product_name_snapshot   VARCHAR(180) NOT NULL,
    unit_price              NUMERIC(12,2) NOT NULL,
    quantity                INTEGER NOT NULL,
    discount_amount         NUMERIC(12,2) NOT NULL DEFAULT 0,
    line_total              NUMERIC(12,2) NOT NULL,
    special_instructions    TEXT,
    CONSTRAINT ck_order_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_discount CHECK (discount_amount >= 0),
    CONSTRAINT ck_order_items_line_total CHECK (line_total >= 0)
);

CREATE INDEX idx_order_items_order ON order_items(order_id);

CREATE TABLE order_item_options (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_item_id           UUID NOT NULL REFERENCES order_items(id),
    product_option_id       UUID REFERENCES product_options(id),
    option_name_snapshot    VARCHAR(150) NOT NULL,
    price_delta             NUMERIC(12,2) NOT NULL DEFAULT 0,
    quantity                INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT ck_order_item_options_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_order_item_options_item ON order_item_options(order_item_id);

CREATE TABLE order_status_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL REFERENCES orders(id),
    from_status     VARCHAR(40),
    to_status       VARCHAR(40) NOT NULL,
    changed_by      UUID REFERENCES users(id),
    reason          TEXT,
    source          VARCHAR(30) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_order_status_history_source CHECK (
        source IN ('MOBILE','POS','WEB','SYSTEM','ADMIN')
    )
);

CREATE INDEX idx_order_status_history_order_created
    ON order_status_history(order_id, created_at);

CREATE TABLE stock_reservations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID NOT NULL REFERENCES orders(id),
    stock_item_id       UUID NOT NULL REFERENCES stock_items(id),
    stock_location_id   UUID NOT NULL REFERENCES stock_locations(id),
    quantity            NUMERIC(14,3) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_stock_reservations_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_reservations_status CHECK (
        status IN ('ACTIVE','CONSUMED','RELEASED','EXPIRED')
    )
);

CREATE INDEX idx_stock_reservations_order ON stock_reservations(order_id);
CREATE INDEX idx_stock_reservations_active
    ON stock_reservations(stock_item_id, stock_location_id)
    WHERE status = 'ACTIVE';

-- ============================================================================
