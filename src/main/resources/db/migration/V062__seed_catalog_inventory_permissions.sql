-- Corrige le gap RBAC decouvert lors de la tache precedente : product.write,
-- category.write et catalog.read sont references par ~60 endpoints
-- catalog/inventory (@PreAuthorize) mais n'ont jamais ete seedes.
--
-- Cahier des charges section 31 (matrice RBAC) :
--   "Modifier produit/prix" -> Resp. Snack: CU, Admin systeme: CU,
--                              Direction: R, Administration: selon politique
--   "Gerer stock"           -> Resp. Snack: CUA, Admin systeme: selon politique,
--                              Direction: R, Administration: -- (interdit)
--   "Voir catalogue"        -> R pour les 8 roles, sans exception
--
-- product.write proteque a la fois les endpoints catalogue (produit/prix)
-- ET inventaire/stock dans le code actuel (un seul flag pour deux actions
-- distinctes dans la matrice). Administration est explicitement interdite
-- ("--") sur la ligne stock : on ne lui accorde donc PAS product.write/
-- category.write, seulement catalog.read (universellement accorde).
--
-- SNACK_MANAGER (Resp. Snack) a un droit d'ecriture inconditionnel sur les
-- deux lignes concernees. SYSTEM_ADMIN (Admin systeme) est deja reference
-- dans application.yaml (sup2i.security.mfa.required-roles) sans jamais
-- avoir ete seede.

INSERT INTO roles (id, code, name, description, is_system)
VALUES
    (
        gen_random_uuid(),
        'SNACK_MANAGER',
        'Responsable Snack',
        'Gestion operationnelle du Snack : produits, prix, stock (cahier des charges 31, ligne Resp. Snack).',
        TRUE
    ),
    (
        gen_random_uuid(),
        'SYSTEM_ADMIN',
        'Administrateur systeme',
        'Administration technique complete (cahier des charges 31, ligne Admin systeme). Deja reference dans la configuration MFA.',
        TRUE
    );

INSERT INTO permissions (id, code, description)
VALUES
    (
        gen_random_uuid(),
        'product.write',
        'Create, update, or manage catalog products/prices and inventory/stock.'
    ),
    (
        gen_random_uuid(),
        'category.write',
        'Create or update catalog categories.'
    ),
    (
        gen_random_uuid(),
        'catalog.read',
        'Browse the catalog: categories, products, menus.'
    );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('SNACK_MANAGER', 'SYSTEM_ADMIN')
  AND p.code IN ('product.write', 'category.write', 'catalog.read');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('DIRECTION', 'ADMINISTRATION')
  AND p.code = 'catalog.read';
