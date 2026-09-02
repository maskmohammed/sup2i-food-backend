-- SUP2I FOOD - B27.1 contract/runtime alignment
-- PostgreSQL 17+

-- ---------------------------------------------------------------------------
-- 1. Canteen reservation cancellation permission.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (
    id,
    code,
    description
)
VALUES (
    gen_random_uuid(),
    'canteen.cancel_reservation',
    'Cancel an eligible canteen reservation.'
)
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description;

-- ADMIN always receives the permission.
INSERT INTO role_permissions (
    role_id,
    permission_id
)
SELECT
    r.id,
    p.id
FROM roles r
JOIN permissions p
  ON p.code = 'canteen.cancel_reservation'
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

-- Operational canteen roles inherit cancellation when they already
-- carry reservation or distribution authority.
INSERT INTO role_permissions (
    role_id,
    permission_id
)
SELECT DISTINCT
    rp.role_id,
    new_permission.id
FROM role_permissions rp
JOIN permissions existing_permission
  ON existing_permission.id = rp.permission_id
JOIN permissions new_permission
  ON new_permission.code = 'canteen.cancel_reservation'
WHERE existing_permission.code IN (
    'canteen.reserve',
    'canteen.distribute'
)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Order status history source alignment.
-- Java/runtime uses API for explicit HTTP transitions.
-- V008 predates this source and therefore needs alignment.
-- ---------------------------------------------------------------------------

ALTER TABLE order_status_history
    DROP CONSTRAINT ck_order_status_history_source;

ALTER TABLE order_status_history
    ADD CONSTRAINT ck_order_status_history_source
    CHECK (
        source IN (
            'MOBILE',
            'POS',
            'WEB',
            'SYSTEM',
            'ADMIN',
            'API'
        )
    );