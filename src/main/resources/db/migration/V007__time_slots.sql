-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 07. CRÉNEAUX
-- ============================================================================

CREATE TABLE time_slots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id     UUID NOT NULL REFERENCES locations(id),
    slot_date       DATE NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    capacity        INTEGER NOT NULL,
    reserved_count  INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_time_slots_location_date_start UNIQUE(location_id, slot_date, start_time),
    CONSTRAINT ck_time_slots_time CHECK (end_time > start_time),
    CONSTRAINT ck_time_slots_capacity CHECK (capacity > 0),
    CONSTRAINT ck_time_slots_reserved CHECK (
        reserved_count >= 0 AND reserved_count <= capacity
    ),
    CONSTRAINT ck_time_slots_status CHECK (
        status IN ('OPEN','ALMOST_FULL','FULL','CLOSED')
    )
);

CREATE INDEX idx_time_slots_location_date ON time_slots(location_id, slot_date);

-- ============================================================================
