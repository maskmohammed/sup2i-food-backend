-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.26 Canteen menu ↔ menu générique + marge de sécurité opérationnelle
-- --------------------------------------------------------------------------

ALTER TABLE canteen_menus ADD COLUMN generic_menu_id UUID REFERENCES menus(id);
ALTER TABLE canteen_menus ADD COLUMN safety_margin_quantity INTEGER;
ALTER TABLE canteen_menus ADD CONSTRAINT ck_canteen_menus_safety_margin CHECK (
    safety_margin_quantity IS NULL OR safety_margin_quantity >= 0
);

-- --------------------------------------------------------------------------
