-- SUP2I FOOD - Phase 12 (Avis, Enquêtes & Vote de menus)
-- ============================================================================
-- Two sections :
--   1. Schema alignment for the engagement slice (reviews moderation + photos,
--      surveys targeting, menu-voting sessions) on top of the existing FINAL
--      V3 tables (V020, V036, V049, V050).
--   2. RBAC seed : review.read / review.write / survey.read / survey.write /
--      menuvote.read / menuvote.write.
--
-- Cahier des charges, matrice RBAC avis/enquêtes/vote :
--   - Modérer les avis et créer/clôturer des enquêtes ou des sessions de vote :
--     opérationnel (Resp. Snack) et Admin système -> review.write /
--     survey.write / menuvote.write.
--   - Consulter les avis, enquêtes/résultats et sessions de vote :
--     lecture pour la Direction et l'Administration (reporting), comme
--     subscription.read en V063 et promotion.read en V064 -> review.read /
--     survey.read / menuvote.read.
--   - STUDENT peut consulter les sessions et résultats de vote (menuvote.read) :
--     un rôle STUDENT est seedé ci-dessous.
--
-- Les endpoints étudiants (déposer un avis, répondre à une enquête, voter)
-- restent couverts par isAuthenticated() + contrôle de propriété
-- (pattern OrderController/CouponController). Aucune permission requise.

-- ============================================================================
-- 1. SCHEMA ALIGNMENT
-- ============================================================================

-- 1.1 Reviews : modération + photos optionnelles.
ALTER TABLE reviews
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE reviews
    ADD COLUMN moderated_by UUID REFERENCES users(id);

ALTER TABLE reviews
    ADD COLUMN moderated_at TIMESTAMPTZ;

ALTER TABLE reviews
    ADD COLUMN photos JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Hibernate maps rating as int; align the legacy SMALLINT column.
ALTER TABLE reviews
    ALTER COLUMN rating TYPE INTEGER;

ALTER TABLE reviews
    ADD CONSTRAINT ck_reviews_moderation_status CHECK (
        moderation_status IN ('PENDING', 'APPROVED', 'REJECTED')
    );

-- Un étudiant ne peut laisser qu'un seul avis par produit ou par commande livrée.
CREATE UNIQUE INDEX uq_reviews_student_product
    ON reviews(student_id, product_id)
    WHERE product_id IS NOT NULL;

CREATE UNIQUE INDEX uq_reviews_student_order
    ON reviews(student_id, order_id)
    WHERE order_id IS NOT NULL;

-- 1.2 Surveys : cible de diffusion.
ALTER TABLE surveys
    ADD COLUMN target VARCHAR(20) NOT NULL DEFAULT 'ALL';

ALTER TABLE surveys
    ADD CONSTRAINT ck_surveys_target CHECK (
        target IN ('ALL', 'ORDERED', 'SUBSCRIBED')
    );

ALTER TABLE surveys ADD COLUMN updated_by UUID REFERENCES users(id);

-- Aligne le nom de la contrainte sur celui déclaré par l'entité SurveySubmission.
ALTER TABLE survey_responses DROP CONSTRAINT uq_survey_response;
ALTER TABLE survey_responses
    ADD CONSTRAINT uq_survey_responses_survey_student UNIQUE (survey_id, student_id);

-- 1.3 Vote de menus : remplacement des tables legacy V036 (jamais utilisées
--     par du code) par le modèle Phase 12 (sessions/semaine cible/deadline).
DROP TABLE menu_votes;
DROP TABLE menu_vote_options;
DROP TABLE menu_vote_campaigns;
DROP TABLE menu_proposals;

CREATE TABLE menu_vote_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    title           VARCHAR(180) NOT NULL,
    description     TEXT,
    target_week     DATE NOT NULL,
    vote_deadline   TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_menu_vote_sessions_status CHECK (
        status IN ('OPEN', 'CLOSED')
    )
);

CREATE INDEX idx_menu_vote_sessions_lookup
    ON menu_vote_sessions(organization_id, status, vote_deadline);

CREATE TABLE menu_vote_options (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id    UUID NOT NULL REFERENCES menu_vote_sessions(id),
    product_id    UUID REFERENCES products(id),
    label         VARCHAR(180) NOT NULL,
    description   TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_menu_vote_options_order CHECK (display_order >= 0),
    CONSTRAINT uq_menu_vote_option_session UNIQUE(id, session_id)
);

CREATE INDEX idx_menu_vote_options_session
    ON menu_vote_options(session_id, display_order);

CREATE TABLE menu_votes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID NOT NULL,
    option_id   UUID NOT NULL,
    student_id  UUID NOT NULL REFERENCES students(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_menu_votes_option_session
        FOREIGN KEY(option_id, session_id)
        REFERENCES menu_vote_options(id, session_id),
    -- Un étudiant ne vote qu'une seule fois par session.
    CONSTRAINT uq_menu_vote_session_student UNIQUE(session_id, student_id)
);

CREATE INDEX idx_menu_votes_student
    ON menu_votes(student_id, created_at DESC);

-- ============================================================================
-- 2. RBAC SEED
-- ============================================================================

INSERT INTO roles (id, code, name, description, is_system)
VALUES (
    gen_random_uuid(),
    'STUDENT',
    'Étudiant',
    'Étudiant SUP2I : peut consulter les sessions et les résultats de vote de menus (menuvote.read).',
    TRUE
);

INSERT INTO permissions (id, code, description)
VALUES
    (
        gen_random_uuid(),
        'review.read',
        'View submitted reviews and pending moderation queue.'
    ),
    (
        gen_random_uuid(),
        'review.write',
        'Moderate reviews: approve or reject.'
    ),
    (
        gen_random_uuid(),
        'survey.read',
        'View surveys, their questions, and response results.'
    ),
    (
        gen_random_uuid(),
        'survey.write',
        'Create, edit, publish, and close surveys.'
    ),
    (
        gen_random_uuid(),
        'menuvote.read',
        'View menu-vote sessions, options, and results.'
    ),
    (
        gen_random_uuid(),
        'menuvote.write',
        'Create and close menu-vote sessions.'
    );

-- Lecture pour tous les rôles back-office (reporting).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('ADMINISTRATION', 'DIRECTION', 'SNACK_MANAGER', 'SYSTEM_ADMIN')
  AND p.code IN ('review.read', 'survey.read', 'menuvote.read');

-- Écriture pour l'opérationnel et l'admin système.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('SNACK_MANAGER', 'SYSTEM_ADMIN')
  AND p.code IN ('review.write', 'survey.write', 'menuvote.write');

-- Les étudiants consultent les sessions et résultats de vote.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'STUDENT'
  AND p.code = 'menuvote.read';

-- ============================================================================