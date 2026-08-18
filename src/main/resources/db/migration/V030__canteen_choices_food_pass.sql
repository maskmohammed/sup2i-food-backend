-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.8 Cantine : choix de plat dans réservation + Food Pass complet
-- --------------------------------------------------------------------------

ALTER TABLE food_passes DROP CONSTRAINT ck_food_passes_status;
ALTER TABLE food_passes ALTER COLUMN status SET DEFAULT 'PENDING_ISSUE';
ALTER TABLE food_passes ADD COLUMN issued_by UUID REFERENCES users(id);
ALTER TABLE food_passes ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE food_passes ADD CONSTRAINT ck_food_passes_status CHECK (
    status IN ('PENDING_ISSUE','ACTIVE','BLOCKED','LOST','REVOKED','EXPIRED','REPLACED')
);

CREATE TABLE canteen_menu_choices (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    canteen_menu_id  UUID NOT NULL REFERENCES canteen_menus(id),
    product_id       UUID NOT NULL REFERENCES products(id),
    label            VARCHAR(180),
    display_order    INTEGER NOT NULL DEFAULT 0,
    max_reservations INTEGER,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_canteen_menu_choice UNIQUE(canteen_menu_id, product_id),
    CONSTRAINT ck_canteen_menu_choice_order CHECK (display_order >= 0),
    CONSTRAINT ck_canteen_menu_choice_max CHECK (
        max_reservations IS NULL OR max_reservations > 0
    )
);

CREATE TABLE canteen_reservation_items (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id         UUID NOT NULL REFERENCES canteen_reservations(id),
    canteen_menu_choice_id UUID NOT NULL REFERENCES canteen_menu_choices(id),
    quantity               INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT uq_canteen_reservation_choice UNIQUE(
        reservation_id, canteen_menu_choice_id
    ),
    CONSTRAINT ck_canteen_reservation_items_quantity CHECK (quantity > 0)
);

ALTER TABLE meal_usages ADD COLUMN canteen_menu_choice_id UUID REFERENCES canteen_menu_choices(id);
ALTER TABLE meal_usages ADD COLUMN reservation_id UUID REFERENCES canteen_reservations(id);
ALTER TABLE meal_usages ADD COLUMN override_reason TEXT;

-- --------------------------------------------------------------------------
