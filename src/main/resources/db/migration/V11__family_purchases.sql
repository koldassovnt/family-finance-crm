-- Phase 9 — family purchases: an expense can be marked as common to the whole
-- household, which publishes it to a view every member reads. Personal is the
-- default, so every existing row stays exactly as private as it was.

ALTER TABLE transactions
    ADD COLUMN is_family BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE transactions
    ADD CONSTRAINT transactions_family_check
        CHECK (is_family = FALSE OR type = 'EXPENSE');

-- The family view filters on nothing but the date, across every owner.
CREATE INDEX transactions_family_occurred_on_idx
    ON transactions (occurred_on)
    WHERE is_family = TRUE AND is_deleted = FALSE;
