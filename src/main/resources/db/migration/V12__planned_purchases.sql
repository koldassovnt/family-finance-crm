-- Phase 9 — planned purchases: a list of things to buy, personal or common to
-- the family, each of which can be marked bought. Not connected to the ledger.

-- V11 marked an expense as a family purchase. That design was withdrawn the
-- day it shipped, before anything could set the flag, so it goes unused.
DROP INDEX transactions_family_occurred_on_idx;
ALTER TABLE transactions
    DROP CONSTRAINT transactions_family_check,
    DROP COLUMN is_family;

CREATE TABLE planned_purchases (
    id         UUID         PRIMARY KEY,
    -- Who added it. A personal item is theirs alone; a family one is everyone's.
    owner_id   UUID         NOT NULL REFERENCES users (id),
    name       VARCHAR(255) NOT NULL,
    is_family  BOOLEAN      NOT NULL,
    is_bought  BOOLEAN      NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

-- The list is "mine, plus every family item".
CREATE INDEX planned_purchases_owner_idx ON planned_purchases (owner_id) WHERE is_deleted = FALSE;
CREATE INDEX planned_purchases_family_idx ON planned_purchases (is_family) WHERE is_deleted = FALSE;
