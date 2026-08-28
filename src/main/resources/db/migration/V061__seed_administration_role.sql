INSERT INTO roles (id, code, name, description, is_system)
VALUES (
    gen_random_uuid(),
    'ADMINISTRATION',
    'Administration',
    'Back-office SUP2I: manage user accounts, roles, and permissions (cahier des charges 11.1).',
    TRUE
);

INSERT INTO permissions (id, code, description)
VALUES
    (
        gen_random_uuid(),
        'user.read',
        'View user accounts and their role assignments.'
    ),
    (
        gen_random_uuid(),
        'user.write',
        'Manage user accounts: activate/deactivate, assign or revoke roles.'
    );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'ADMINISTRATION'
  AND p.code IN ('user.read', 'user.write');
