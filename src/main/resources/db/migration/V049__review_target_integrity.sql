-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.27 Review : exactement une cible
-- --------------------------------------------------------------------------

ALTER TABLE reviews DROP CONSTRAINT ck_reviews_target;
ALTER TABLE reviews ADD CONSTRAINT ck_reviews_exactly_one_target CHECK (
    num_nonnulls(product_id, order_id, menu_id) = 1
);

-- --------------------------------------------------------------------------
