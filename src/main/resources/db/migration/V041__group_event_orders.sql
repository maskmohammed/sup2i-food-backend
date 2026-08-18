-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.19 Commandes groupées + catering/commandes événementielles
-- --------------------------------------------------------------------------

CREATE TABLE group_orders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id         UUID NOT NULL UNIQUE REFERENCES orders(id),
    owner_student_id UUID NOT NULL REFERENCES students(id),
    join_code        VARCHAR(40) NOT NULL UNIQUE,
    status           VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    closes_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_group_orders_status CHECK (
        status IN ('OPEN','LOCKED','SUBMITTED','CANCELLED','COMPLETED')
    )
);

CREATE TABLE group_order_members (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_order_id UUID NOT NULL REFERENCES group_orders(id),
    student_id     UUID NOT NULL REFERENCES students(id),
    status         VARCHAR(30) NOT NULL DEFAULT 'JOINED',
    joined_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at        TIMESTAMPTZ,
    CONSTRAINT uq_group_order_member UNIQUE(group_order_id, student_id),
    CONSTRAINT ck_group_order_members_status CHECK (
        status IN ('INVITED','JOINED','LEFT','REMOVED')
    )
);

CREATE TABLE campus_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campus_id           UUID NOT NULL REFERENCES campuses(id),
    name                VARCHAR(180) NOT NULL,
    event_type          VARCHAR(40) NOT NULL,
    starts_at           TIMESTAMPTZ NOT NULL,
    ends_at             TIMESTAMPTZ NOT NULL,
    expected_attendance INTEGER,
    description         TEXT,
    created_by          UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_campus_events_dates CHECK (ends_at > starts_at),
    CONSTRAINT ck_campus_events_attendance CHECK (
        expected_attendance IS NULL OR expected_attendance >= 0
    )
);

CREATE INDEX idx_campus_events_dates
    ON campus_events(campus_id, starts_at, ends_at);

CREATE TABLE event_order_details (
    order_id        UUID PRIMARY KEY REFERENCES orders(id),
    campus_event_id UUID NOT NULL REFERENCES campus_events(id),
    requested_for   TIMESTAMPTZ NOT NULL,
    approval_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    approved_by     UUID REFERENCES users(id),
    approved_at     TIMESTAMPTZ,
    notes           TEXT,
    CONSTRAINT ck_event_order_details_approval_status CHECK (
        approval_status IN ('PENDING','APPROVED','REJECTED','CANCELLED')
    )
);

-- --------------------------------------------------------------------------
