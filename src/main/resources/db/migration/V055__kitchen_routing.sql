-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.32.1 Routage point de vente -> cuisine
-- --------------------------------------------------------------------------

CREATE TABLE preparation_routes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_location_id  UUID NOT NULL REFERENCES locations(id),
    kitchen_location_id UUID NOT NULL REFERENCES locations(id),
    category_id         UUID REFERENCES categories(id),
    product_id          UUID REFERENCES products(id),
    variant_id          UUID REFERENCES product_variants(id),
    priority            INTEGER NOT NULL DEFAULT 0,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from          TIMESTAMPTZ,
    valid_to            TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_preparation_routes_scope CHECK (
        num_nonnulls(category_id, product_id, variant_id) <= 1
    ),
    CONSTRAINT ck_preparation_routes_locations CHECK (
        source_location_id <> kitchen_location_id
    ),
    CONSTRAINT ck_preparation_routes_priority CHECK (priority >= 0),
    CONSTRAINT ck_preparation_routes_validity CHECK (
        valid_to IS NULL OR valid_from IS NULL OR valid_to > valid_from
    ),
    CONSTRAINT uq_preparation_routes_scope UNIQUE NULLS NOT DISTINCT (
        source_location_id,
        kitchen_location_id,
        category_id,
        product_id,
        variant_id,
        valid_from
    )
);

CREATE INDEX idx_preparation_routes_lookup
    ON preparation_routes(source_location_id, is_active, priority DESC);

-- L'ancien modèle imposait un seul ticket cuisine par commande. Le routage
-- multi-point doit autoriser plusieurs tickets, un par cuisine concernée.
ALTER TABLE kitchen_tickets
    DROP CONSTRAINT IF EXISTS kitchen_tickets_order_id_key;

ALTER TABLE kitchen_tickets
    ADD COLUMN preparation_route_id UUID REFERENCES preparation_routes(id);

CREATE UNIQUE INDEX uq_kitchen_tickets_order_kitchen
    ON kitchen_tickets(order_id, kitchen_location_id);

-- Permet de vérifier qu'une sélection de menu appartient bien à la ligne
-- d'ordre stockée dans kitchen_ticket_items.
ALTER TABLE order_item_menu_selections
    ADD CONSTRAINT uq_order_item_menu_selections_id_item
    UNIQUE(id, order_item_id);

CREATE TABLE kitchen_ticket_items (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kitchen_ticket_id       UUID NOT NULL REFERENCES kitchen_tickets(id),
    order_item_id           UUID NOT NULL REFERENCES order_items(id),
    menu_selection_id       UUID,
    quantity                NUMERIC(14,3) NOT NULL,
    status                  VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    started_at              TIMESTAMPTZ,
    ready_at                TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    issue_note              TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kitchen_ticket_items_menu_selection
        FOREIGN KEY(menu_selection_id, order_item_id)
        REFERENCES order_item_menu_selections(id, order_item_id),
    CONSTRAINT ck_kitchen_ticket_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_kitchen_ticket_items_status CHECK (
        status IN ('QUEUED','PREPARING','READY','CANCELLED')
    ),
    CONSTRAINT uq_kitchen_ticket_item UNIQUE NULLS NOT DISTINCT (
        kitchen_ticket_id, order_item_id, menu_selection_id
    )
);

CREATE INDEX idx_kitchen_ticket_items_ticket_status
    ON kitchen_ticket_items(kitchen_ticket_id, status);

CREATE INDEX idx_kitchen_ticket_items_order_item
    ON kitchen_ticket_items(order_item_id);

-- --------------------------------------------------------------------------
