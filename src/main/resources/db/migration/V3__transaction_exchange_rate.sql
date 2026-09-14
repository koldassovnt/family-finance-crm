-- KZT is the single accounting currency. A transaction still records the
-- amount that actually moved, in its account's currency, plus the rate used to
-- express it in KZT. Every aggregation sums amount_kzt, never amount, so
-- totals can no longer be a mix of currencies added together.

ALTER TABLE transactions
    ADD COLUMN exchange_rate NUMERIC(19, 6),
    ADD COLUMN amount_kzt    NUMERIC(19, 4);

-- Backfill is only correct because nothing is deployed yet: the sole non-KZT
-- rows in existence came from local verification runs.
UPDATE transactions SET exchange_rate = 1, amount_kzt = amount;

ALTER TABLE transactions
    ALTER COLUMN exchange_rate SET NOT NULL,
    ALTER COLUMN amount_kzt SET NOT NULL,
    ADD CONSTRAINT transactions_rate_positive_check CHECK (exchange_rate > 0),
    ADD CONSTRAINT transactions_base_currency_rate_check
        CHECK (currency <> 'KZT' OR exchange_rate = 1);
