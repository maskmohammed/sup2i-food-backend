-- SUP2I FOOD — Phase 9 (Dashboard Direction)
-- ============================================================================
-- Pure DML: seeds a DIRECTION role and its dashboard:read permission.
-- No table or column is created, altered, or dropped.

INSERT INTO roles (id, code, name, description, is_system)
VALUES (
    gen_random_uuid(),
    'DIRECTION',
    'Direction',
    'Read-only access to executive/financial reporting (dashboard, KPIs).',
    TRUE
);

INSERT INTO permissions (id, code, description)
VALUES (
    gen_random_uuid(),
    'dashboard:read',
    'View the executive dashboard (revenue, order KPIs, top products).'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'DIRECTION'
  AND p.code = 'dashboard:read';

-- ============================================================================
