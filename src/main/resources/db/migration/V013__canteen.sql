-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 13. CANTINE
-- ============================================================================

CREATE TABLE canteen_menus (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id         UUID NOT NULL REFERENCES locations(id),
    menu_date           DATE NOT NULL,
    meal_type           VARCHAR(30) NOT NULL,
    title               VARCHAR(180) NOT NULL,
    description         TEXT,
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    estimated_cost      NUMERIC(12,2),
    planned_quantity    INTEGER,
    created_by          UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_canteen_menu_service UNIQUE(location_id, menu_date, meal_type),
    CONSTRAINT ck_canteen_menu_meal_type CHECK (
        meal_type IN ('BREAKFAST','LUNCH','DINNER','OTHER')
    ),
    CONSTRAINT ck_canteen_menu_status CHECK (
        status IN ('DRAFT','PUBLISHED','CLOSED','CANCELLED')
    ),
    CONSTRAINT ck_canteen_menu_estimated_cost CHECK (
        estimated_cost IS NULL OR estimated_cost >= 0
    ),
    CONSTRAINT ck_canteen_menu_planned_quantity CHECK (
        planned_quantity IS NULL OR planned_quantity >= 0
    )
);

CREATE INDEX idx_canteen_menus_date
    ON canteen_menus(location_id, menu_date, meal_type);

CREATE TABLE canteen_menu_products (
    canteen_menu_id UUID NOT NULL REFERENCES canteen_menus(id),
    product_id      UUID NOT NULL REFERENCES products(id),
    PRIMARY KEY(canteen_menu_id, product_id)
);

CREATE TABLE canteen_reservations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL REFERENCES students(id),
    menu_id         UUID NOT NULL REFERENCES canteen_menus(id),
    status          VARCHAR(30) NOT NULL DEFAULT 'RESERVED',
    reserved_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at    TIMESTAMPTZ,
    consumed_at     TIMESTAMPTZ,
    CONSTRAINT uq_canteen_reservation UNIQUE(student_id, menu_id),
    CONSTRAINT ck_canteen_reservation_status CHECK (
        status IN ('RESERVED','CANCELLED','CONSUMED','NO_SHOW')
    )
);

CREATE INDEX idx_canteen_reservations_menu_status
    ON canteen_reservations(menu_id, status);

-- ============================================================================
