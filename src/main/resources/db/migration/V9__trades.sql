-- Phase 5 — investments: a trade is a fourth kind of ledger row rather than a
-- table of its own, so buying an asset debits the account through the same
-- apply/reverse path as every other transaction. Holdings are not stored;
-- they are derived from these rows on read.

-- A crypto exchange or wallet: the second account type that can hold assets.
ALTER TABLE accounts DROP CONSTRAINT accounts_type_check;
ALTER TABLE accounts
    ADD CONSTRAINT accounts_type_check
        CHECK (type IN ('CASH', 'BANK', 'DEPOSIT', 'BROKER', 'CRYPTO'));

ALTER TABLE transactions
    ADD COLUMN trade_side VARCHAR(16),
    ADD COLUMN ticker     VARCHAR(32),
    -- Ten decimals rather than the four money uses: a crypto quantity is
    -- fractional, and so is the price of a coin worth a fraction of a cent.
    ADD COLUMN quantity   NUMERIC(28, 10),
    ADD COLUMN unit_price NUMERIC(28, 10);

ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;
ALTER TABLE transactions
    ADD CONSTRAINT transactions_type_check
        CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER', 'ADJUSTMENT', 'TRADE')),
    ADD CONSTRAINT transactions_trade_side_check
        CHECK (trade_side IS NULL OR trade_side IN ('BUY', 'SELL', 'OPENING')),
    -- The four trade columns are all set on a TRADE and all null otherwise.
    ADD CONSTRAINT transactions_trade_check
        CHECK (
            (type = 'TRADE'
                AND trade_side IS NOT NULL
                AND ticker IS NOT NULL
                AND quantity > 0
                AND unit_price > 0)
            OR (type <> 'TRADE'
                AND trade_side IS NULL
                AND ticker IS NULL
                AND quantity IS NULL
                AND unit_price IS NULL)
        );

-- Holdings are read per account and per ticker.
CREATE INDEX transactions_trade_idx
    ON transactions (account_id, ticker) WHERE type = 'TRADE' AND is_deleted = FALSE;
