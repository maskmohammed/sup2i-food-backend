-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 03. CATALOGUE
-- ============================================================================

CREATE TABLE categories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    parent_id       UUID REFERENCES categories(id),
    name            VARCHAR(120) NOT NULL,
    slug            VARCHAR(140) NOT NULL,
    display_order   INTEGER NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_categories_org_slug UNIQUE (organization_id, slug),
    CONSTRAINT ck_categories_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_categories_parent ON categories(parent_id);
CREATE INDEX idx_categories_org_active ON categories(organization_id, is_active);

CREATE TABLE products (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    category_id         UUID NOT NULL REFERENCES categories(id),
    sku                 VARCHAR(80) NOT NULL,
    name                VARCHAR(180) NOT NULL,
    description         TEXT,
    image_url           TEXT,
    product_type        VARCHAR(30) NOT NULL,
    base_price          NUMERIC(12,2) NOT NULL,
    tax_rate            NUMERIC(5,2) NOT NULL DEFAULT 0,
    preparation_minutes INTEGER,
    track_stock         BOOLEAN NOT NULL DEFAULT TRUE,
    is_prepared         BOOLEAN NOT NULL DEFAULT FALSE,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_products_org_sku UNIQUE (organization_id, sku),
    CONSTRAINT ck_products_type CHECK (
        product_type IN ('PACKAGED','PREPARED','SERVICE','COMBO')
    ),
    CONSTRAINT ck_products_price CHECK (base_price >= 0),
    CONSTRAINT ck_products_tax CHECK (tax_rate >= 0 AND tax_rate <= 100),
    CONSTRAINT ck_products_prep_minutes CHECK (
        preparation_minutes IS NULL OR preparation_minutes >= 0
    )
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_active ON products(is_active);
CREATE INDEX idx_products_org_active ON products(organization_id, is_active);

CREATE TABLE product_price_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL REFERENCES products(id),
    price           NUMERIC(12,2) NOT NULL,
    tax_rate        NUMERIC(5,2) NOT NULL DEFAULT 0,
    effective_from  TIMESTAMPTZ NOT NULL,
    effective_to    TIMESTAMPTZ,
    changed_by      UUID REFERENCES users(id),
    reason          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_product_price_history_price CHECK (price >= 0),
    CONSTRAINT ck_product_price_history_tax CHECK (tax_rate >= 0 AND tax_rate <= 100),
    CONSTRAINT ck_product_price_history_range CHECK (
        effective_to IS NULL OR effective_to > effective_from
    )
);

CREATE INDEX idx_product_price_history_product
    ON product_price_history(product_id, effective_from DESC);

CREATE UNIQUE INDEX uq_product_price_history_open
    ON product_price_history(product_id)
    WHERE effective_to IS NULL;

CREATE TABLE product_barcodes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL REFERENCES products(id),
    barcode         VARCHAR(120) NOT NULL UNIQUE,
    pack_quantity   NUMERIC(14,3) NOT NULL DEFAULT 1,
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_product_barcodes_pack_quantity CHECK (pack_quantity > 0)
);

CREATE INDEX idx_product_barcodes_product ON product_barcodes(product_id);

CREATE UNIQUE INDEX uq_product_primary_barcode
    ON product_barcodes(product_id)
    WHERE is_primary = TRUE AND is_active = TRUE;

CREATE TABLE product_variants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL REFERENCES products(id),
    name            VARCHAR(120) NOT NULL,
    sku             VARCHAR(80),
    barcode         VARCHAR(120),
    price_delta     NUMERIC(12,2) NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    display_order   INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_product_variants_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_product_variants_product ON product_variants(product_id);

CREATE TABLE product_option_groups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL REFERENCES products(id),
    name            VARCHAR(120) NOT NULL,
    min_select      INTEGER NOT NULL DEFAULT 0,
    max_select      INTEGER NOT NULL DEFAULT 1,
    required        BOOLEAN NOT NULL DEFAULT FALSE,
    display_order   INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_product_option_groups_selection CHECK (
        min_select >= 0 AND max_select >= 0 AND min_select <= max_select
    ),
    CONSTRAINT ck_product_option_groups_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_product_option_groups_product ON product_option_groups(product_id);

CREATE TABLE product_options (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    option_group_id UUID NOT NULL REFERENCES product_option_groups(id),
    name            VARCHAR(120) NOT NULL,
    price_delta     NUMERIC(12,2) NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    display_order   INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_product_options_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_product_options_group ON product_options(option_group_id);

CREATE TABLE product_allergens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL REFERENCES products(id),
    allergen_code   VARCHAR(80) NOT NULL,
    note            TEXT,
    CONSTRAINT uq_product_allergens UNIQUE (product_id, allergen_code)
);

CREATE TABLE product_dietary_tags (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL REFERENCES products(id),
    tag_code        VARCHAR(80) NOT NULL,
    note            TEXT,
    CONSTRAINT uq_product_dietary_tags UNIQUE(product_id, tag_code)
);

CREATE TABLE product_location_settings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id          UUID NOT NULL REFERENCES products(id),
    location_id         UUID NOT NULL REFERENCES locations(id),
    is_enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    allowed_days        SMALLINT[],
    available_from_time TIME,
    available_to_time   TIME,
    preparation_minutes INTEGER,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_product_location UNIQUE(product_id, location_id),
    CONSTRAINT ck_product_location_days CHECK (
        allowed_days IS NULL OR allowed_days <@ ARRAY[1,2,3,4,5,6,7]::SMALLINT[]
    ),
    CONSTRAINT ck_product_location_time CHECK (
        available_from_time IS NULL
        OR available_to_time IS NULL
        OR available_to_time > available_from_time
    ),
    CONSTRAINT ck_product_location_prep CHECK (
        preparation_minutes IS NULL OR preparation_minutes >= 0
    )
);

-- Favoris: inclus pour la fonction "favoris / commander à nouveau".
CREATE TABLE favorites (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL REFERENCES students(id),
    product_id      UUID NOT NULL REFERENCES products(id),
    configuration   JSONB,
    label           VARCHAR(120),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_favorites_student ON favorites(student_id, created_at DESC);

-- ============================================================================
