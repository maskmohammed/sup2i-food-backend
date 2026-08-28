-- SUP2I FOOD - Phase 7 (Cantine, Abonnements & Food Pass)
-- ============================================================================
-- Pure DML: seeds subscription.read / subscription.write permissions.
-- No table or column is created, altered, or dropped.
--
-- Cahier des charges section 31 (matrice RBAC), lignes simplifiees pour la
-- cantine/abonnements :
--   - Parametrer les plans, activer/suspendre/annuler une souscription,
--     emettre/bloquer un Food Pass, valider une consommation : operationnel
--     (Resp. Snack) et back-office (Administration), Admin systeme aussi.
--   - Consulter les souscriptions / food passes / consommations : lecture
--     etendue a la Direction (reporting), comme dashboard:read en V060.
--
-- STUDENT reste couvert par isAuthenticated() + controle de propriete dans
-- les services (pattern OrderController). Aucune permission requise.

INSERT INTO permissions (id, code, description)
VALUES
    (
        gen_random_uuid(),
        'subscription.read',
        'View subscription plans, subscriptions, food passes, and meal usage.'
    ),
    (
        gen_random_uuid(),
        'subscription.write',
        'Manage subscription plans, activate/suspend/cancel subscriptions, issue or block food passes, and record meal consumption.'
    );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('ADMINISTRATION', 'SNACK_MANAGER', 'SYSTEM_ADMIN')
  AND p.code IN ('subscription.read', 'subscription.write');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'DIRECTION'
  AND p.code = 'subscription.read';

-- ============================================================================