-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.29b Cohérence produit ↔ variante dans toutes les lignes métier
-- --------------------------------------------------------------------------

ALTER TABLE product_variants
    ADD CONSTRAINT uq_product_variants_id_product UNIQUE(id, product_id);

ALTER TABLE product_barcodes
    ADD CONSTRAINT fk_product_barcodes_variant_product
    FOREIGN KEY(variant_id, product_id)
    REFERENCES product_variants(id, product_id);

ALTER TABLE recipes
    ADD CONSTRAINT fk_recipes_variant_product
    FOREIGN KEY(variant_id, product_id)
    REFERENCES product_variants(id, product_id);

ALTER TABLE menu_items
    ADD CONSTRAINT fk_menu_items_variant_product
    FOREIGN KEY(variant_id, product_id)
    REFERENCES product_variants(id, product_id);

ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_variant_product
    FOREIGN KEY(variant_id, product_id)
    REFERENCES product_variants(id, product_id);

ALTER TABLE order_item_menu_selections
    ADD CONSTRAINT fk_order_item_menu_variant_product
    FOREIGN KEY(variant_id, product_id)
    REFERENCES product_variants(id, product_id);

ALTER TABLE shopping_cart_items
    ADD CONSTRAINT fk_cart_items_variant_product
    FOREIGN KEY(variant_id, product_id)
    REFERENCES product_variants(id, product_id);

-- --------------------------------------------------------------------------
