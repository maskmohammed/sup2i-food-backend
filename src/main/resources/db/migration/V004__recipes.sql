-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 04. INGRÉDIENTS / RECETTES
-- ============================================================================

CREATE TABLE ingredients (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    code            VARCHAR(80) NOT NULL,
    name            VARCHAR(150) NOT NULL,
    base_unit       VARCHAR(20) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ingredients_org_code UNIQUE(organization_id, code),
    CONSTRAINT ck_ingredients_unit CHECK (
        base_unit IN ('PIECE','GRAM','KILOGRAM','MILLILITER','LITER')
    )
);

CREATE TABLE recipes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL REFERENCES products(id),
    version         INTEGER NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_recipes_product_version UNIQUE(product_id, version),
    CONSTRAINT ck_recipes_version CHECK (version > 0),
    CONSTRAINT ck_recipes_effective_range CHECK (
        effective_to IS NULL OR effective_to > effective_from
    )
);

CREATE UNIQUE INDEX uq_recipes_active_product
    ON recipes(product_id)
    WHERE is_active = TRUE AND effective_to IS NULL;

CREATE TABLE recipe_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id       UUID NOT NULL REFERENCES recipes(id),
    ingredient_id   UUID NOT NULL REFERENCES ingredients(id),
    quantity        NUMERIC(14,3) NOT NULL,
    unit            VARCHAR(20) NOT NULL,
    waste_factor    NUMERIC(6,4),
    is_critical     BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_recipe_item UNIQUE(recipe_id, ingredient_id),
    CONSTRAINT ck_recipe_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_recipe_items_unit CHECK (
        unit IN ('PIECE','GRAM','KILOGRAM','MILLILITER','LITER')
    ),
    CONSTRAINT ck_recipe_items_waste_factor CHECK (
        waste_factor IS NULL OR (waste_factor >= 0 AND waste_factor < 1)
    )
);

CREATE INDEX idx_recipe_items_ingredient ON recipe_items(ingredient_id);

-- ============================================================================
