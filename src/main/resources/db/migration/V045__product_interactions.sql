-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.23 Popularité / interactions / paniers abandonnés / alternatives
-- --------------------------------------------------------------------------

CREATE TABLE product_interaction_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id  UUID REFERENCES students(id),
    product_id  UUID NOT NULL REFERENCES products(id),
    event_type  VARCHAR(40) NOT NULL,
    cart_id     UUID REFERENCES shopping_carts(id),
    order_id    UUID REFERENCES orders(id),
    location_id UUID REFERENCES locations(id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata    JSONB,
    CONSTRAINT ck_product_interaction_events_type CHECK (
        event_type IN (
            'VIEW','FAVORITE','UNFAVORITE','CART_ADD','CART_REMOVE',
            'CART_ABANDON','ORDER','RESERVATION','SUBSTITUTION_SHOWN',
            'SUBSTITUTION_ACCEPTED'
        )
    )
);

CREATE INDEX idx_product_interactions_product_time
    ON product_interaction_events(product_id, occurred_at DESC);

-- --------------------------------------------------------------------------
