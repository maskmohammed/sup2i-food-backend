-- SUP2I FOOD - Phase 11 (Gaspillage & Achats fournisseurs)
-- ============================================================================
-- Sections :
--   1. Remplacement du schéma legacy "waste" (V018/V035/V051/V056) par le
--      modèle Phase 11 : cible (recette / ingrédient / ligne de commande),
--      coût côté serveur, type de gaspillage structuré.
--   2. Enrichissement suppliers (contact + status) avec is_active synchronisé
--      par le service (jamais désynchronisé en base).
--   3. Reconstruction de purchase_orders / purchase_order_items (V019 legacy)
--      en gardant les noms de tables, avec drop/re-add des FKs fournisseurs
--      sur stock_receipts / stock_receipt_lines (V026, colonnes inutilisées).
--   4. Réception auto-portée : purchase_order_receipts /
--      purchase_order_receipt_lines + purchase_order_history.
--   5. supplier_contracts : tarifs négociés par fournisseur (produit /
--      variante / ingrédient).
--   6. Seed RBAC : KITCHEN_STAFF + waste.* / supplier.* / purchase.*.
--
-- Cahier des charges, matrice RBAC gaspillage & achats :
--   - Déclarer un gaspillage : Resp. Snack, Admin système et Personnel cuisine
--     -> waste.write (SNACK_MANAGER, SYSTEM_ADMIN, KITCHEN_STAFF).
--   - Consulter les rapports de gaspillage : lecture étendue à la Direction et
--     à l'Administration (reporting) + KITCHEN_STAFF -> waste.read.
--   - Gérer fournisseurs/contrats et bons de commande : Admin système
--     -> supplier.write / purchase.write (SYSTEM_ADMIN uniquement).
--   - Consulter fournisseurs/contrats/bons de commande : lecture pour
--     ADMINISTRATION, DIRECTION, SNACK_MANAGER, SYSTEM_ADMIN ->
--     supplier.read / purchase.read (comme subscription.read V063).

-- ============================================================================
-- 1. GASPILLAGE : REMPLACEMENT DU SCHÉMA LEGACY
-- ============================================================================

-- La table legacy n'est référencée par aucune autre contrainte ; ses propres
-- colonnes pointent vers production_run_items / waste_reasons / stock items.
DROP TABLE IF EXISTS waste_records;
DROP TABLE IF EXISTS waste_reasons;

-- ============================================================================
-- 2. SUPPLIERS : ENRICHISSEMENT
-- ============================================================================

ALTER TABLE suppliers
    ADD COLUMN contact VARCHAR(120);

ALTER TABLE suppliers
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE suppliers
    ADD CONSTRAINT ck_suppliers_status CHECK (
        status IN ('ACTIVE', 'INACTIVE')
    );

-- is_active reste la colonne historique ; le service la synchronise avec
-- status à chaque mise à jour pour ne jamais la désynchroniser.

-- ============================================================================
-- 3. ACHATS : RECONSTRUCTION DE purchase_orders / purchase_order_items
-- ============================================================================

-- Retirer les FKs legacy qui pointent vers les tables à reconstruire.
-- Les colonnes purchase_order_id / purchase_order_item_id n'ont jamais été
-- alimentées par du code ; on écarte d'éventuelles valeurs orphelines.
UPDATE stock_receipts
SET purchase_order_id = NULL
WHERE purchase_order_id IS NOT NULL;

UPDATE stock_receipt_lines
SET purchase_order_item_id = NULL
WHERE purchase_order_item_id IS NOT NULL;

ALTER TABLE stock_receipts
    DROP CONSTRAINT IF EXISTS stock_receipts_purchase_order_id_fkey;

ALTER TABLE stock_receipt_lines
    DROP CONSTRAINT IF EXISTS stock_receipt_lines_purchase_order_item_id_fkey;

DROP TABLE IF EXISTS purchase_order_items;
DROP TABLE IF EXISTS purchase_orders;

CREATE TABLE purchase_orders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organizations(id),
    supplier_id      UUID NOT NULL REFERENCES suppliers(id),
    campus_id        UUID NOT NULL REFERENCES campuses(id),
    reference        VARCHAR(80) NOT NULL UNIQUE,
    status           VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    total_estimated  NUMERIC(12,2),
    notes            TEXT,
    created_by       UUID NOT NULL REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_purchase_orders_status CHECK (
        status IN (
            'DRAFT','SENT','CONFIRMED',
            'PARTIALLY_RECEIVED','RECEIVED','CANCELLED'
        )
    ),
    CONSTRAINT ck_purchase_orders_total CHECK (
        total_estimated IS NULL OR total_estimated >= 0
    )
);

CREATE INDEX idx_purchase_orders_org_status
    ON purchase_orders(organization_id, status, created_at DESC);

CREATE INDEX idx_purchase_orders_supplier
    ON purchase_orders(supplier_id);

CREATE TABLE purchase_order_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    product_id        UUID REFERENCES products(id),
    variant_id        UUID REFERENCES product_variants(id),
    ingredient_id     UUID REFERENCES ingredients(id),
    quantity          NUMERIC(14,3) NOT NULL,
    received_quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
    unit              VARCHAR(20) NOT NULL,
    unit_price        NUMERIC(12,2),
    line_total        NUMERIC(12,2),
    CONSTRAINT ck_purchase_order_items_subject CHECK (
        num_nonnulls(product_id, variant_id, ingredient_id) = 1
    ),
    CONSTRAINT ck_purchase_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_order_items_received CHECK (
        received_quantity >= 0 AND received_quantity <= quantity
    ),
    CONSTRAINT ck_purchase_order_items_unit CHECK (
        unit IN ('PIECE','GRAM','KILOGRAM','MILLILITER','LITER')
    ),
    CONSTRAINT ck_purchase_order_items_price CHECK (
        unit_price IS NULL OR unit_price >= 0
    ),
    CONSTRAINT ck_purchase_order_items_total CHECK (
        line_total IS NULL OR line_total >= 0
    )
);

CREATE INDEX idx_purchase_order_items_order
    ON purchase_order_items(purchase_order_id);

-- Les FKs stock_receipts / stock_receipt_lines sont recréées vers le nouveau
-- modèle ; les colonnes demeurent optionnelles (réceptions hors PO possibles).
ALTER TABLE stock_receipts
    ADD CONSTRAINT stock_receipts_purchase_order_id_fkey
    FOREIGN KEY(purchase_order_id) REFERENCES purchase_orders(id);

ALTER TABLE stock_receipt_lines
    ADD CONSTRAINT stock_receipt_lines_purchase_order_item_id_fkey
    FOREIGN KEY(purchase_order_item_id) REFERENCES purchase_order_items(id);

-- ============================================================================
-- 4. RÉCEPTION AUTO-PORTÉE + HISTORIQUE
-- ============================================================================

CREATE TABLE purchase_order_history (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    event_type        VARCHAR(30) NOT NULL,
    status_before     VARCHAR(30),
    status_after      VARCHAR(30) NOT NULL,
    performed_by      UUID NOT NULL REFERENCES users(id),
    occurred_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes             TEXT,
    CONSTRAINT ck_purchase_order_history_event CHECK (
        event_type IN (
            'CREATED','UPDATED','SENT','CONFIRMED',
            'PARTIALLY_RECEIVED','RECEIVED','CANCELLED'
        )
    )
);

CREATE INDEX idx_purchase_order_history_order_time
    ON purchase_order_history(purchase_order_id, occurred_at);

CREATE TABLE purchase_order_receipts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    stock_location_id UUID NOT NULL REFERENCES stock_locations(id),
    received_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_by       UUID NOT NULL REFERENCES users(id),
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_purchase_order_receipts_order
    ON purchase_order_receipts(purchase_order_id);

CREATE TABLE purchase_order_receipt_lines (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id              UUID NOT NULL REFERENCES purchase_order_receipts(id),
    purchase_order_item_id  UUID NOT NULL REFERENCES purchase_order_items(id),
    quantity                NUMERIC(14,3) NOT NULL,
    unit                    VARCHAR(20) NOT NULL,
    unit_cost               NUMERIC(12,4),
    lot_number              VARCHAR(120),
    expires_at              TIMESTAMPTZ,
    generated_lot_id        UUID REFERENCES stock_lots(id),
    inventory_movement_id   UUID REFERENCES inventory_movements(id),
    CONSTRAINT ck_purchase_order_receipt_lines_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_order_receipt_lines_unit CHECK (
        unit IN ('PIECE','GRAM','KILOGRAM','MILLILITER','LITER')
    ),
    CONSTRAINT ck_purchase_order_receipt_lines_cost CHECK (
        unit_cost IS NULL OR unit_cost >= 0
    )
);

CREATE INDEX idx_purchase_order_receipt_lines_receipt
    ON purchase_order_receipt_lines(receipt_id);

-- ============================================================================
-- 5. CONTRATS FOURNISSEURS
-- ============================================================================

CREATE TABLE supplier_contracts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organizations(id),
    supplier_id      UUID NOT NULL REFERENCES suppliers(id),
    product_id       UUID REFERENCES products(id),
    variant_id       UUID REFERENCES product_variants(id),
    ingredient_id    UUID REFERENCES ingredients(id),
    unit_price       NUMERIC(12,2) NOT NULL,
    unit             VARCHAR(20) NOT NULL,
    min_quantity     NUMERIC(14,3),
    payment_terms    VARCHAR(60),
    lead_time_days   INTEGER,
    start_date       DATE,
    end_date         DATE,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes            TEXT,
    created_by       UUID NOT NULL REFERENCES users(id),
    updated_by       UUID REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_supplier_contracts_subject CHECK (
        num_nonnulls(product_id, variant_id, ingredient_id) = 1
    ),
    CONSTRAINT ck_supplier_contracts_price CHECK (unit_price >= 0),
    CONSTRAINT ck_supplier_contracts_unit CHECK (
        unit IN ('PIECE','GRAM','KILOGRAM','MILLILITER','LITER')
    ),
    CONSTRAINT ck_supplier_contracts_min_quantity CHECK (
        min_quantity IS NULL OR min_quantity > 0
    ),
    CONSTRAINT ck_supplier_contracts_lead_time CHECK (
        lead_time_days IS NULL OR lead_time_days > 0
    ),
    CONSTRAINT ck_supplier_contracts_period CHECK (
        end_date IS NULL OR start_date IS NULL OR end_date > start_date
    ),
    CONSTRAINT ck_supplier_contracts_status CHECK (
        status IN ('ACTIVE','SUSPENDED','EXPIRED')
    )
);

CREATE INDEX idx_supplier_contracts_org_supplier
    ON supplier_contracts(organization_id, supplier_id, status);

CREATE INDEX idx_supplier_contracts_subject
    ON supplier_contracts(organization_id, product_id, ingredient_id)
    WHERE product_id IS NOT NULL OR ingredient_id IS NOT NULL;

-- ============================================================================
-- 6. GASPILLAGE : NOUVEAU MODÈLE
-- ============================================================================
-- Le coût estimé est TOUJOURS calculé côté serveur (jamais fourni par le
-- client). Le stock n'est décrémenté (mouvement WASTE) que lorsque le stock
-- item est non ambigu : ingrédient, ou produit non "prepared" suivi en stock.
-- Les cibles recette / ligne de commande (articles préparés) conservent la
-- traçabilité et le coût sans mouvement d'inventaire.

CREATE TABLE waste_records (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID NOT NULL REFERENCES organizations(id),
    campus_id               UUID REFERENCES campuses(id),
    stock_location_id       UUID REFERENCES stock_locations(id),
    recipe_id               UUID REFERENCES recipes(id),
    ingredient_id           UUID REFERENCES ingredients(id),
    product_id              UUID REFERENCES products(id),
    order_item_id           UUID REFERENCES order_items(id),
    waste_type              VARCHAR(30) NOT NULL,
    quantity                NUMERIC(14,3) NOT NULL,
    unit                    VARCHAR(20) NOT NULL,
    estimated_cost          NUMERIC(12,2) NOT NULL,
    reason_text             TEXT,
    photo_url               VARCHAR(500),
    recorded_by             UUID NOT NULL REFERENCES users(id),
    recorded_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    inventory_movement_id   UUID REFERENCES inventory_movements(id),
    CONSTRAINT ck_waste_records_target CHECK (
        num_nonnulls(recipe_id, ingredient_id, order_item_id) >= 1
    ),
    CONSTRAINT ck_waste_records_type CHECK (
        waste_type IN (
            'UNSOLD','EXPIRED','OVERPRODUCTION','KITCHEN_ERROR',
            'RETURN','NO_SHOW','DAMAGED','BREAKAGE','CONTAMINATION',
            'STOCK_ERROR','OTHER'
        )
    ),
    CONSTRAINT ck_waste_records_quantity CHECK (quantity > 0),
    CONSTRAINT ck_waste_records_unit CHECK (
        unit IN ('PIECE','GRAM','KILOGRAM','MILLILITER','LITER')
    ),
    CONSTRAINT ck_waste_records_cost CHECK (estimated_cost >= 0)
);

CREATE INDEX idx_waste_records_org_date
    ON waste_records(organization_id, recorded_at DESC);

CREATE INDEX idx_waste_records_type
    ON waste_records(organization_id, waste_type, recorded_at DESC);

-- ============================================================================
-- 7. SEED RBAC
-- ============================================================================

INSERT INTO roles (id, code, name, description, is_system)
VALUES (
    gen_random_uuid(),
    'KITCHEN_STAFF',
    'Personnel cuisine',
    'Personnel de cuisine SUP2I : déclare les gaspillages (waste.write) et consulte les rapports (waste.read).',
    TRUE
);

INSERT INTO permissions (id, code, description)
VALUES
    (
        gen_random_uuid(),
        'waste.read',
        'View waste records, reports, and statistics.'
    ),
    (
        gen_random_uuid(),
        'waste.write',
        'Record product, ingredient, and recipe waste.'
    ),
    (
        gen_random_uuid(),
        'supplier.read',
        'View suppliers and negotiated contracts.'
    ),
    (
        gen_random_uuid(),
        'supplier.write',
        'Create, edit, and deactivate suppliers and contracts.'
    ),
    (
        gen_random_uuid(),
        'purchase.read',
        'View purchase orders, receipt history, and status.'
    ),
    (
        gen_random_uuid(),
        'purchase.write',
        'Create, send, confirm, receive, and cancel purchase orders.'
    );

-- Gaspillage : lecture étendue (reporting) + personnel cuisine.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('ADMINISTRATION', 'DIRECTION', 'SNACK_MANAGER', 'SYSTEM_ADMIN', 'KITCHEN_STAFF')
  AND p.code = 'waste.read';

-- Gaspillage : écriture opérationnelle (Resp. Snack, Admin système, cuisine).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('SNACK_MANAGER', 'SYSTEM_ADMIN', 'KITCHEN_STAFF')
  AND p.code = 'waste.write';

-- Achats & fournisseurs : lecture back-office (reporting).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('ADMINISTRATION', 'DIRECTION', 'SNACK_MANAGER', 'SYSTEM_ADMIN')
  AND p.code IN ('supplier.read', 'purchase.read');

-- Achats & fournisseurs : écriture exclusive Admin système (décision métier).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'SYSTEM_ADMIN'
  AND p.code IN ('supplier.write', 'purchase.write');

-- ============================================================================