-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.17 Horaires réels des points de service
-- --------------------------------------------------------------------------

CREATE TABLE location_business_hours (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES locations(id),
    day_of_week SMALLINT NOT NULL,
    opens_at    TIME,
    closes_at   TIME,
    is_closed   BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from  DATE,
    valid_to    DATE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_location_business_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_location_business_hours_time CHECK (
        is_closed = TRUE
        OR (opens_at IS NOT NULL AND closes_at IS NOT NULL AND closes_at > opens_at)
    ),
    CONSTRAINT ck_location_business_hours_dates CHECK (
        valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from
    )
);

CREATE INDEX idx_location_business_hours_lookup
    ON location_business_hours(location_id, day_of_week, valid_from, valid_to);

-- --------------------------------------------------------------------------
