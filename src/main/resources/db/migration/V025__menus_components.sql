-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.3 Produits composés / Menu / MenuItem / options qui consomment du stock
-- --------------------------------------------------------------------------

ALTER TABLE products DROP CONSTRAINT ck_products_type;
ALTER TABLE products ADD CONSTRAINT ck_products_type CHECK (
    product_type IN ('PACKAGED','PREPARED','SERVICE','COMBO','MENU')
);

ALTER TABLE product_barcodes ADD COLUMN variant_id UUID REFERENCES product_variants(id);
CREATE INDEX idx_product_barcodes_variant ON product_barcodes(variant_id);

ALTER TABLE recipes ADD COLUMN variant_id UUID REFERENCES product_variants(id);
ALTER TABLE recipes DROP CONSTRAINT uq_recipes_product_version;
CREATE UNIQUE INDEX uq_recipes_product_variant_version
    ON recipes(product_id, COALESCE(variant_id, '00000000-0000-0000-0000-000000000000'::UUID), version);
DROP INDEX uq_recipes_active_product;
CREATE UNIQUE INDEX uq_recipes_active_product_variant
    ON recipes(product_id, COALESCE(variant_id, '00000000-0000-0000-0000-000000000000'::UUID))
    WHERE is_active = TRUE AND effective_to IS NULL;

CREATE TABLE product_option_components (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_option_id    UUID NOT NULL REFERENCES product_options(id),
    component_product_id UUID REFERENCES products(id),
    component_variant_id UUID REFERENCES product_variants(id),
    ingredient_id        UUID REFERENCES ingredients(id),
    quantity             NUMERIC(14,3) NOT NULL DEFAULT 1,
    unit                 VARCHAR(20) NOT NULL DEFAULT 'PIECE',
    CONSTRAINT ck_product_option_components_subject CHECK (
        num_nonnulls(component_product_id, component_variant_id, ingredient_id) = 1
    ),
    CONSTRAINT ck_product_option_components_quantity CHECK (quantity > 0),
    CONSTRAINT ck_product_option_components_unit CHECK (
        unit IN ('PIECE','GRAM','KILOGRAM','MILLILITER','LITER')
    )
);

CREATE INDEX idx_product_option_components_option
    ON product_option_components(product_option_id);

CREATE TABLE menus (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL UNIQUE REFERENCES products(id),
    pricing_mode    VARCHAR(20) NOT NULL DEFAULT 'FIXED',
    description     TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_menus_pricing_mode CHECK (
        pricing_mode IN ('FIXED','CALCULATED')
    )
);

CREATE TABLE menu_sections (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_id         UUID NOT NULL REFERENCES menus(id),
    code            VARCHAR(80),
    name            VARCHAR(120) NOT NULL,
    min_select      INTEGER NOT NULL DEFAULT 1,
    max_select      INTEGER NOT NULL DEFAULT 1,
    display_order   INTEGER NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_menu_sections_code UNIQUE(menu_id, code),
    CONSTRAINT ck_menu_sections_selection CHECK (
        min_select >= 0 AND max_select >= 0 AND min_select <= max_select
    ),
    CONSTRAINT ck_menu_sections_order CHECK (display_order >= 0)
);

CREATE TABLE menu_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_section_id UUID NOT NULL REFERENCES menu_sections(id),
    product_id      UUID NOT NULL REFERENCES products(id),
    variant_id      UUID REFERENCES product_variants(id),
    quantity        NUMERIC(14,3) NOT NULL DEFAULT 1,
    price_delta     NUMERIC(12,2) NOT NULL DEFAULT 0,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    display_order   INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_menu_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_menu_items_order CHECK (display_order >= 0)
);

CREATE INDEX idx_menu_items_section ON menu_items(menu_section_id);

CREATE TABLE product_substitutions (
    product_id            UUID NOT NULL REFERENCES products(id),
    substitute_product_id UUID NOT NULL REFERENCES products(id),
    priority              INTEGER NOT NULL DEFAULT 0,
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(product_id, substitute_product_id),
    CONSTRAINT ck_product_substitutions_not_self CHECK (product_id <> substitute_product_id),
    CONSTRAINT ck_product_substitutions_priority CHECK (priority >= 0)
);

-- --------------------------------------------------------------------------
