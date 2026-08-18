-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.5 Commandes : type, paiement, taxes, menus choisis, créneau atomique
-- --------------------------------------------------------------------------

ALTER TABLE orders ADD COLUMN order_type VARCHAR(40) NOT NULL DEFAULT 'MOBILE_SNACK';
ALTER TABLE orders ADD COLUMN payment_status VARCHAR(40) NOT NULL DEFAULT 'PENDING';
ALTER TABLE orders ADD COLUMN tax_total NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN promised_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN no_show_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN customer_note TEXT;

ALTER TABLE orders ADD CONSTRAINT ck_orders_order_type CHECK (
    order_type IN ('MOBILE_SNACK','POS_DIRECT','CANTEEN_REDEMPTION','GROUP_ORDER','EVENT_ORDER')
);

ALTER TABLE orders ADD CONSTRAINT ck_orders_payment_status CHECK (
    payment_status IN (
        'NOT_REQUIRED','PENDING','COMPLETED','FAILED',
        'CANCELLED','REFUNDED','PARTIALLY_REFUNDED'
    )
);

ALTER TABLE orders ADD CONSTRAINT ck_orders_tax_total CHECK (tax_total >= 0);

ALTER TABLE order_items ADD COLUMN variant_name_snapshot VARCHAR(120);
ALTER TABLE order_items ADD COLUMN sku_snapshot VARCHAR(80);
ALTER TABLE order_items ADD COLUMN tax_rate_snapshot NUMERIC(5,2) NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD COLUMN line_tax NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD CONSTRAINT ck_order_items_tax_rate CHECK (
    tax_rate_snapshot BETWEEN 0 AND 100
);
ALTER TABLE order_items ADD CONSTRAINT ck_order_items_line_tax CHECK (line_tax >= 0);

CREATE TABLE order_item_menu_selections (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_item_id         UUID NOT NULL REFERENCES order_items(id),
    menu_item_id          UUID REFERENCES menu_items(id),
    product_id            UUID NOT NULL REFERENCES products(id),
    variant_id            UUID REFERENCES product_variants(id),
    product_name_snapshot VARCHAR(180) NOT NULL,
    variant_name_snapshot VARCHAR(120),
    quantity              NUMERIC(14,3) NOT NULL DEFAULT 1,
    price_delta_snapshot  NUMERIC(12,2) NOT NULL DEFAULT 0,
    CONSTRAINT ck_order_item_menu_selections_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_order_item_menu_selections_item
    ON order_item_menu_selections(order_item_id);

CREATE TABLE order_discounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID NOT NULL REFERENCES orders(id),
    order_item_id       UUID REFERENCES order_items(id),
    source_type         VARCHAR(30) NOT NULL,
    source_id           UUID,
    code_snapshot       VARCHAR(100),
    label_snapshot      VARCHAR(180) NOT NULL,
    amount              NUMERIC(12,2) NOT NULL,
    manually_applied_by UUID REFERENCES users(id),
    reason              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_order_discounts_source_type CHECK (
        source_type IN ('PROMOTION','COUPON','LOYALTY','MANUAL','OTHER')
    ),
    CONSTRAINT ck_order_discounts_amount CHECK (amount >= 0)
);

CREATE INDEX idx_order_discounts_order ON order_discounts(order_id);

CREATE TABLE time_slot_reservations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    time_slot_id    UUID NOT NULL REFERENCES time_slots(id),
    order_id        UUID NOT NULL REFERENCES orders(id),
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    reserved_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at     TIMESTAMPTZ,
    release_reason  TEXT,
    CONSTRAINT ck_time_slot_reservations_status CHECK (
        status IN ('ACTIVE','FULFILLED','RELEASED','EXPIRED','CANCELLED')
    )
);

CREATE UNIQUE INDEX uq_time_slot_active_order
    ON time_slot_reservations(order_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_time_slot_reservations_slot_status
    ON time_slot_reservations(time_slot_id, status);

ALTER TABLE stock_reservations ADD COLUMN order_item_id UUID REFERENCES order_items(id);
ALTER TABLE stock_reservations ADD COLUMN consumed_at TIMESTAMPTZ;
ALTER TABLE stock_reservations ADD COLUMN released_at TIMESTAMPTZ;

CREATE TABLE document_sequences (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    location_id     UUID REFERENCES locations(id),
    sequence_type   VARCHAR(40) NOT NULL,
    business_date   DATE,
    prefix          VARCHAR(20),
    current_value   BIGINT NOT NULL DEFAULT 0,
    padding         INTEGER NOT NULL DEFAULT 3,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_sequences_scope UNIQUE NULLS NOT DISTINCT (
        organization_id, location_id, sequence_type, business_date, prefix
    ),
    CONSTRAINT ck_document_sequences_value CHECK (current_value >= 0),
    CONSTRAINT ck_document_sequences_padding CHECK (padding BETWEEN 1 AND 12)
);

-- Panier serveur : utile au MVP multi-device et explicitement nécessaire pour
-- le futur score de popularité basé sur les paniers abandonnés.
CREATE TABLE shopping_carts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id          UUID NOT NULL REFERENCES students(id),
    location_id         UUID NOT NULL REFERENCES locations(id),
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    currency            CHAR(3) NOT NULL DEFAULT 'MAD',
    converted_order_id  UUID REFERENCES orders(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    abandoned_at        TIMESTAMPTZ,
    CONSTRAINT ck_shopping_carts_status CHECK (
        status IN ('ACTIVE','CONVERTED','ABANDONED','EXPIRED')
    )
);

CREATE UNIQUE INDEX uq_shopping_cart_active_student_location
    ON shopping_carts(student_id, location_id)
    WHERE status = 'ACTIVE';

CREATE TABLE shopping_cart_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id         UUID NOT NULL REFERENCES shopping_carts(id),
    product_id      UUID NOT NULL REFERENCES products(id),
    variant_id      UUID REFERENCES product_variants(id),
    quantity        INTEGER NOT NULL DEFAULT 1,
    client_note     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_shopping_cart_items_quantity CHECK (quantity > 0)
);

CREATE TABLE shopping_cart_item_options (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_item_id      UUID NOT NULL REFERENCES shopping_cart_items(id),
    product_option_id UUID NOT NULL REFERENCES product_options(id),
    quantity          INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT ck_shopping_cart_item_options_quantity CHECK (quantity > 0)
);

CREATE TABLE shopping_cart_item_menu_selections (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_item_id UUID NOT NULL REFERENCES shopping_cart_items(id),
    menu_item_id UUID NOT NULL REFERENCES menu_items(id),
    quantity     NUMERIC(14,3) NOT NULL DEFAULT 1,
    CONSTRAINT ck_shopping_cart_item_menu_quantity CHECK (quantity > 0)
);

-- --------------------------------------------------------------------------
