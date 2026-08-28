-- SUP2I FOOD - Phase 10 (Promotions & Fidelite)
-- ============================================================================
-- Pure DML: seeds promotion.read / promotion.write / loyalty.read /
-- loyalty.write permissions.
-- No table or column is created, altered, or dropped.
--
-- Cahier des charges, matrice RBAC promotions & fidelite :
--   - Gerer les coupons/promotions et ajuster les soldes de fidelite :
--     operationnel (Resp. Snack) et Admin systeme -> promotion.write /
--     loyalty.write.
--   - Consulter les promotions/coupons et les comptes de fidelite :
--     lecture etendue a la Direction et a l'Administration (reporting),
--     comme subscription.read en V063 -> promotion.read / loyalty.read.
--
-- STUDENT reste couvert par isAuthenticated() + controle de propriete
-- (pattern OrderController). Aucune permission requise.

INSERT INTO permissions (id, code, description)
VALUES
    (
        gen_random_uuid(),
        'promotion.read',
        'View promotions and coupons.'
    ),
    (
        gen_random_uuid(),
        'promotion.write',
        'Create, edit, and deactivate promotions and coupons.'
    ),
    (
        gen_random_uuid(),
        'loyalty.read',
        'View loyalty accounts and balances.'
    ),
    (
        gen_random_uuid(),
        'loyalty.write',
        'Adjust loyalty account balances.'
    );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('ADMINISTRATION', 'DIRECTION', 'SNACK_MANAGER', 'SYSTEM_ADMIN')
  AND p.code IN ('promotion.read', 'loyalty.read');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('SNACK_MANAGER', 'SYSTEM_ADMIN')
  AND p.code IN ('promotion.write', 'loyalty.write');

-- ============================================================================