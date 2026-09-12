-- Phase 2 — budgets and goals. Both belong to a single owner, like accounts
-- and categories; neither stores computed usage or progress.

CREATE TABLE budgets (
    id                       UUID           PRIMARY KEY,
    owner_id                 UUID           NOT NULL REFERENCES users (id),
    category_id              UUID           NOT NULL REFERENCES categories (id),
    limit_amount             NUMERIC(19, 4) NOT NULL,
    period                   VARCHAR(16)    NOT NULL,
    alert_threshold_percent  INTEGER,
    is_deleted               BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ    NOT NULL,
    updated_at               TIMESTAMPTZ    NOT NULL,
    CONSTRAINT budgets_period_check CHECK (period IN ('MONTHLY')),
    CONSTRAINT budgets_limit_check CHECK (limit_amount > 0),
    CONSTRAINT budgets_threshold_check
        CHECK (alert_threshold_percent IS NULL
               OR (alert_threshold_percent BETWEEN 1 AND 100))
);

-- Partial, so a soft-deleted budget never blocks re-budgeting that category.
CREATE UNIQUE INDEX budgets_owner_category_uk
    ON budgets (owner_id, category_id) WHERE is_deleted = FALSE;
CREATE INDEX budgets_category_idx ON budgets (category_id) WHERE is_deleted = FALSE;

CREATE TABLE goals (
    id                UUID           PRIMARY KEY,
    owner_id          UUID           NOT NULL REFERENCES users (id),
    name              VARCHAR(255)   NOT NULL,
    type              VARCHAR(32)    NOT NULL,
    target_amount     NUMERIC(19, 4) NOT NULL,
    target_date       DATE,
    linked_account_id UUID           NOT NULL REFERENCES accounts (id),
    status            VARCHAR(16)    NOT NULL,
    is_deleted        BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,
    CONSTRAINT goals_type_check CHECK (type IN ('SAVINGS', 'EMERGENCY_FUND')),
    CONSTRAINT goals_status_check CHECK (status IN ('ACTIVE', 'ABANDONED')),
    CONSTRAINT goals_target_check CHECK (target_amount > 0)
);

CREATE INDEX goals_owner_idx ON goals (owner_id) WHERE is_deleted = FALSE;
CREATE INDEX goals_linked_account_idx ON goals (linked_account_id) WHERE is_deleted = FALSE;
