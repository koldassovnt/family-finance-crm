-- Phase 4 — bills. Date, name, amount for anything due, and nothing else:
-- no recurrence engine, no reconciliation against transactions, no reminders.
-- Recurring bills are expanded into ordinary rows by POST /api/v1/bills/batch.

CREATE TABLE bills (
    id         UUID           PRIMARY KEY,
    owner_id   UUID           NOT NULL REFERENCES users (id),
    name       VARCHAR(255)   NOT NULL,
    amount     NUMERIC(19, 4) NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    due_date   DATE           NOT NULL,
    is_paid    BOOLEAN        NOT NULL DEFAULT FALSE,
    -- Null for an individually created bill; shared across one batch call.
    batch_id   UUID,
    is_deleted BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ    NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL,
    CONSTRAINT bills_amount_check CHECK (amount > 0)
);

CREATE INDEX bills_owner_due_idx
    ON bills (owner_id, due_date) WHERE is_deleted = FALSE;
CREATE INDEX bills_batch_idx ON bills (batch_id) WHERE is_deleted = FALSE;
