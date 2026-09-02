-- SUP2I FOOD - B27.1
-- Runtime-governed order lifecycle defaults.
-- Definitions only.

INSERT INTO setting_definitions(
    setting_key,
    value_type,
    scope_type,
    default_value,
    description,
    is_secret,
    is_runtime_editable,
    validation_rules
)
VALUES
(
    'ORDER_PAYMENT_TTL_MINUTES',
    'INTEGER',
    'ANY',
    '15'::jsonb,
    'Payment window in minutes for orders without a slot-specific deadline.',
    FALSE,
    TRUE,
    '{"minimum":1}'::jsonb
),
(
    'ORDER_MAX_ACTIVE_ORDERS',
    'INTEGER',
    'ANY',
    '2'::jsonb,
    'Maximum concurrent active student orders.',
    FALSE,
    TRUE,
    '{"minimum":1}'::jsonb
),
(
    'ORDER_NO_SHOW_TOLERANCE_MINUTES',
    'INTEGER',
    'ANY',
    '30'::jsonb,
    'Minutes after READY before an uncollected order becomes NO_SHOW.',
    FALSE,
    TRUE,
    '{"minimum":1}'::jsonb
)
ON CONFLICT (setting_key)
DO NOTHING;