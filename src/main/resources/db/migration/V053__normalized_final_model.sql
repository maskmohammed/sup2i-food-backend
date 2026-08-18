-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.30 Nettoyage des structures V1 remplacées par le modèle final normalisé
-- --------------------------------------------------------------------------

-- Les credentials locaux/institutionnels vivent désormais dans auth_identities.
ALTER TABLE users DROP COLUMN password_hash;

-- Un code-barres appartient à product_barcodes; pas directement à la variante.
ALTER TABLE product_variants DROP COLUMN barcode;

-- Remplacement des liaisons allergènes/tags texte par les référentiels FK.
DROP TABLE product_allergens;
ALTER TABLE product_allergen_links RENAME TO product_allergens;

DROP TABLE product_dietary_tags;
ALTER TABLE product_dietary_tag_links RENAME TO product_dietary_tags;

-- canteen_menu_choices porte à la fois la présence dans le menu et la capacité
-- de réservation par choix; l'ancienne table simple devient inutile.
DROP TABLE canteen_menu_products;

-- Services d'abonnement normalisés pour ne pas dépendre d'un tableau texte.
ALTER TABLE subscription_plans DROP COLUMN services;

-- Préférences de notifications normalisées par catégorie/canal.
DROP TABLE notification_preferences;
ALTER TABLE notification_category_preferences RENAME TO notification_preferences;


-- ============================================================================
