-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.20 Emploi du temps / « Préparer ma pause » / prévision calendrier
-- --------------------------------------------------------------------------

CREATE TABLE academic_groups (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campus_id   UUID NOT NULL REFERENCES campuses(id),
    code        VARCHAR(80) NOT NULL,
    name        VARCHAR(150) NOT NULL,
    program     VARCHAR(150),
    level       VARCHAR(80),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_academic_groups_campus_code UNIQUE(campus_id, code)
);

CREATE TABLE academic_schedule_slots (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    academic_group_id UUID NOT NULL REFERENCES academic_groups(id),
    valid_from        DATE NOT NULL,
    valid_to          DATE NOT NULL,
    day_of_week       SMALLINT NOT NULL,
    starts_at         TIME NOT NULL,
    ends_at           TIME NOT NULL,
    slot_type         VARCHAR(30) NOT NULL,
    title             VARCHAR(180),
    CONSTRAINT ck_academic_schedule_slots_dates CHECK (valid_to >= valid_from),
    CONSTRAINT ck_academic_schedule_slots_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_academic_schedule_slots_time CHECK (ends_at > starts_at),
    CONSTRAINT ck_academic_schedule_slots_type CHECK (
        slot_type IN ('COURSE','BREAK','EXAM','OTHER')
    )
);

CREATE INDEX idx_academic_schedule_group_day
    ON academic_schedule_slots(academic_group_id, day_of_week, valid_from, valid_to);

CREATE TABLE student_group_memberships (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id        UUID NOT NULL REFERENCES students(id),
    academic_group_id UUID NOT NULL REFERENCES academic_groups(id),
    valid_from        DATE NOT NULL,
    valid_to          DATE,
    CONSTRAINT uq_student_group_membership UNIQUE(
        student_id, academic_group_id, valid_from
    ),
    CONSTRAINT ck_student_group_membership_dates CHECK (
        valid_to IS NULL OR valid_to >= valid_from
    )
);

-- --------------------------------------------------------------------------
