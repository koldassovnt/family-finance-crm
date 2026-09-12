-- Family Finance CRM — Phase 0/1 foundation & ledger.
-- Money is NUMERIC(19,4); ids are UUIDs; every table is soft-deleted and audited.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT users_role_check CHECK (role IN ('OWNER', 'MEMBER'))
);

-- Partial, so a soft-deleted row never permanently blocks reusing an email.
CREATE UNIQUE INDEX users_email_uk ON users (lower(email)) WHERE is_deleted = FALSE;

CREATE TABLE banks (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX banks_name_uk ON banks (lower(name)) WHERE is_deleted = FALSE;

CREATE TABLE accounts (
    id         UUID           PRIMARY KEY,
    owner_id   UUID           NOT NULL REFERENCES users (id),
    bank_id    UUID           REFERENCES banks (id),
    name       VARCHAR(255)   NOT NULL,
    type       VARCHAR(16)    NOT NULL,
    balance    NUMERIC(19, 4) NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    is_deleted BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ    NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL,
    CONSTRAINT accounts_type_check CHECK (type IN ('CASH', 'BANK', 'DEPOSIT', 'BROKER'))
);

CREATE INDEX accounts_owner_idx ON accounts (owner_id) WHERE is_deleted = FALSE;
CREATE INDEX accounts_bank_idx ON accounts (bank_id);

CREATE TABLE categories (
    id         UUID         PRIMARY KEY,
    owner_id   UUID         NOT NULL REFERENCES users (id),
    parent_id  UUID         REFERENCES categories (id),
    name       VARCHAR(255) NOT NULL,
    kind       VARCHAR(16)  NOT NULL,
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT categories_kind_check CHECK (kind IN ('EXPENSE', 'INCOME'))
);

CREATE INDEX categories_owner_idx ON categories (owner_id) WHERE is_deleted = FALSE;
CREATE INDEX categories_parent_idx ON categories (parent_id);

CREATE TABLE transactions (
    id            UUID           PRIMARY KEY,
    type          VARCHAR(16)    NOT NULL,
    amount        NUMERIC(19, 4) NOT NULL,
    currency      VARCHAR(3)     NOT NULL,
    to_amount     NUMERIC(19, 4),
    occurred_on   DATE           NOT NULL,
    account_id    UUID           NOT NULL REFERENCES accounts (id),
    to_account_id UUID           REFERENCES accounts (id),
    category_id   UUID           REFERENCES categories (id),
    note          VARCHAR(1000),
    is_deleted    BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL,
    CONSTRAINT transactions_type_check
        CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER', 'ADJUSTMENT')),
    -- ADJUSTMENT is the only type allowed to be negative; zero is never valid.
    CONSTRAINT transactions_amount_check
        CHECK (type = 'ADJUSTMENT' OR amount > 0),
    CONSTRAINT transactions_amount_nonzero_check CHECK (amount <> 0),
    -- toAmount / toAccount only ever belong to a TRANSFER.
    CONSTRAINT transactions_transfer_check
        CHECK (type = 'TRANSFER' OR (to_account_id IS NULL AND to_amount IS NULL)),
    CONSTRAINT transactions_transfer_target_check
        CHECK (type <> 'TRANSFER' OR to_account_id IS NOT NULL),
    CONSTRAINT transactions_transfer_self_check
        CHECK (to_account_id IS NULL OR to_account_id <> account_id)
);

CREATE INDEX transactions_account_date_idx
    ON transactions (account_id, occurred_on) WHERE is_deleted = FALSE;
CREATE INDEX transactions_to_account_date_idx
    ON transactions (to_account_id, occurred_on) WHERE is_deleted = FALSE;
CREATE INDEX transactions_category_idx ON transactions (category_id);
CREATE INDEX transactions_occurred_on_idx
    ON transactions (occurred_on) WHERE is_deleted = FALSE;
