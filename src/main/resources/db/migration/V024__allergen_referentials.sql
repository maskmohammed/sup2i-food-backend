-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.2 Référentiels allergènes / restrictions
-- --------------------------------------------------------------------------

CREATE TABLE allergens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    code            VARCHAR(80) NOT NULL,
    name            VARCHAR(120) NOT NULL,
    description     TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_allergens_org_code UNIQUE(organization_id, code)
);

CREATE TABLE dietary_tags (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    code            VARCHAR(80) NOT NULL,
    name            VARCHAR(120) NOT NULL,
    description     TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_dietary_tags_org_code UNIQUE(organization_id, code)
);

CREATE TABLE student_allergens (
    student_id      UUID NOT NULL REFERENCES students(id),
    allergen_id     UUID NOT NULL REFERENCES allergens(id),
    severity        VARCHAR(30),
    note            TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(student_id, allergen_id),
    CONSTRAINT ck_student_allergens_severity CHECK (
        severity IS NULL OR severity IN ('LOW','MEDIUM','HIGH','UNKNOWN')
    )
);

CREATE TABLE student_dietary_tags (
    student_id      UUID NOT NULL REFERENCES students(id),
    dietary_tag_id  UUID NOT NULL REFERENCES dietary_tags(id),
    preference_type VARCHAR(20) NOT NULL DEFAULT 'REQUIRE',
    note            TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(student_id, dietary_tag_id),
    CONSTRAINT ck_student_dietary_tags_preference CHECK (
        preference_type IN ('REQUIRE','AVOID','PREFER')
    )
);

CREATE TABLE ingredient_allergens (
    ingredient_id   UUID NOT NULL REFERENCES ingredients(id),
    allergen_id     UUID NOT NULL REFERENCES allergens(id),
    PRIMARY KEY(ingredient_id, allergen_id)
);

-- Les tables V1 product_allergens/product_dietary_tags conservent les codes texte
-- pour compatibilité documentaire. Les tables normalisées ci-dessous portent les FK.
CREATE TABLE product_allergen_links (
    product_id      UUID NOT NULL REFERENCES products(id),
    allergen_id     UUID NOT NULL REFERENCES allergens(id),
    note            TEXT,
    PRIMARY KEY(product_id, allergen_id)
);

CREATE TABLE product_dietary_tag_links (
    product_id      UUID NOT NULL REFERENCES products(id),
    dietary_tag_id  UUID NOT NULL REFERENCES dietary_tags(id),
    note            TEXT,
    PRIMARY KEY(product_id, dietary_tag_id)
);

-- --------------------------------------------------------------------------
