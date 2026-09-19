-- Phase 7 — topics: a named grouping over transactions that already exist
-- (a trip, a renovation), so one undertaking can be viewed and totalled on its
-- own. No new money concepts: a topic is a lens over the ledger, and every
-- total it reports is derived from the transactions attached to it.

CREATE TABLE topics (
    id             UUID           PRIMARY KEY,
    owner_id       UUID           NOT NULL REFERENCES users (id),
    name           VARCHAR(255)   NOT NULL,
    description    VARCHAR(1000),
    -- Metadata describing when the undertaking happened, not a constraint on
    -- what may be attached: a deposit paid months earlier still belongs to it.
    start_date     DATE,
    end_date       DATE,
    -- What you expected to spend, in KZT. Display only — nothing alerts.
    planned_amount NUMERIC(19, 4),
    status         VARCHAR(16)    NOT NULL,
    is_deleted     BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT topics_planned_amount_check
        CHECK (planned_amount IS NULL OR planned_amount > 0),
    CONSTRAINT topics_dates_check
        CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date)
);

-- Two live topics with the same name are a typo, not a plan.
CREATE UNIQUE INDEX topics_owner_name_uk
    ON topics (owner_id, lower(name)) WHERE is_deleted = FALSE;

-- Membership is a nullable FK: one topic per transaction, so no total can ever
-- count the same money twice.
ALTER TABLE transactions ADD COLUMN topic_id UUID REFERENCES topics (id);
CREATE INDEX transactions_topic_idx ON transactions (topic_id) WHERE is_deleted = FALSE;
