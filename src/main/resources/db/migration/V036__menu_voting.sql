-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.14 Vote / co-construction des menus + propositions étudiants
-- --------------------------------------------------------------------------

CREATE TABLE menu_proposals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    student_id      UUID REFERENCES students(id),
    title           VARCHAR(180) NOT NULL,
    description     TEXT,
    status          VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    reviewed_by     UUID REFERENCES users(id),
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_menu_proposals_status CHECK (
        status IN ('SUBMITTED','UNDER_REVIEW','ACCEPTED','REJECTED','ARCHIVED')
    )
);

CREATE TABLE menu_vote_campaigns (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    title           VARCHAR(180) NOT NULL,
    description     TEXT,
    status          VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL,
    max_choices     INTEGER NOT NULL DEFAULT 1,
    created_by      UUID NOT NULL REFERENCES users(id),
    CONSTRAINT ck_menu_vote_campaigns_status CHECK (
        status IN ('DRAFT','ACTIVE','CLOSED','CANCELLED')
    ),
    CONSTRAINT ck_menu_vote_campaigns_dates CHECK (ends_at > starts_at),
    CONSTRAINT ck_menu_vote_campaigns_choices CHECK (max_choices > 0)
);

CREATE TABLE menu_vote_options (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id      UUID NOT NULL REFERENCES menu_vote_campaigns(id),
    product_id       UUID REFERENCES products(id),
    menu_proposal_id UUID REFERENCES menu_proposals(id),
    label            VARCHAR(180) NOT NULL,
    description      TEXT,
    display_order    INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_menu_vote_options_subject CHECK (
        num_nonnulls(product_id, menu_proposal_id) <= 1
    ),
    CONSTRAINT ck_menu_vote_options_order CHECK (display_order >= 0)
);

CREATE TABLE menu_votes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES menu_vote_campaigns(id),
    option_id   UUID NOT NULL REFERENCES menu_vote_options(id),
    student_id  UUID NOT NULL REFERENCES students(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_menu_vote_student_option UNIQUE(campaign_id, option_id, student_id)
);

CREATE INDEX idx_menu_votes_campaign_student
    ON menu_votes(campaign_id, student_id);

-- --------------------------------------------------------------------------
