-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.29 DERNIÈRES CORRECTIONS DE COHÉRENCE DU FREEZE FINAL
-- ============================================================================

-- Canaux futurs déjà explicitement prévus (web/kiosque/connecteurs).
ALTER TABLE orders DROP CONSTRAINT ck_orders_source;
ALTER TABLE orders ADD CONSTRAINT ck_orders_source CHECK (
    source IN ('MOBILE','POS','WEB','KIOSK','ADMIN','API')
);

ALTER TABLE order_status_history DROP CONSTRAINT ck_order_status_history_source;
ALTER TABLE order_status_history ADD CONSTRAINT ck_order_status_history_source CHECK (
    source IN ('MOBILE','POS','WEB','KIOSK','SYSTEM','ADMIN','API')
);

-- WasteReason est désormais la source structurée; reason devient seulement un
-- snapshot/texte historique facultatif.
ALTER TABLE waste_records DROP CONSTRAINT ck_waste_records_reason;
ALTER TABLE waste_records ALTER COLUMN reason DROP NOT NULL;
ALTER TABLE waste_records ALTER COLUMN waste_reason_id SET NOT NULL;

-- Traçabilité des contributions dans une commande groupée.
ALTER TABLE order_items ADD COLUMN group_order_member_id UUID REFERENCES group_order_members(id);

-- Code promotionnel unique dans l'organisation lorsqu'il est renseigné.
CREATE UNIQUE INDEX uq_promotions_org_code
    ON promotions(organization_id, code)
    WHERE code IS NOT NULL;

-- Le menu Cantine historique peut continuer d'utiliser canteen_menu_products;
-- canteen_menu_choices ajoute la notion de choix réservable et de capacité.

-- --------------------------------------------------------------------------
