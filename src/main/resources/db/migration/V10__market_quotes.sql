-- Market data: the latest price of each asset held and the latest KZT rate of
-- each currency an account is in, fetched from API Ninjas. Purchase cost was
-- all Phase 5 could report; these are what turn it into a current value.

-- One row per symbol, overwritten on each refresh. History is not kept here:
-- a trend is Phase 6's job, and it snapshots totals rather than prices.
CREATE TABLE market_quotes (
    id         UUID            PRIMARY KEY,
    kind       VARCHAR(16)     NOT NULL,
    -- A ticker for STOCK and CRYPTO, a currency code for CURRENCY.
    symbol     VARCHAR(32)     NOT NULL,
    -- The price of one unit in `currency`. For CURRENCY: KZT per one unit.
    price      NUMERIC(28, 10) NOT NULL,
    currency   VARCHAR(3)      NOT NULL,
    -- Where a STOCK trades, as the price API names it; null for the other kinds.
    exchange   VARCHAR(32),
    fetched_at TIMESTAMPTZ     NOT NULL,
    is_deleted BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ     NOT NULL,
    updated_at TIMESTAMPTZ     NOT NULL,
    CONSTRAINT market_quotes_kind_check CHECK (kind IN ('STOCK', 'CRYPTO', 'CURRENCY')),
    CONSTRAINT market_quotes_price_check CHECK (price > 0)
);

CREATE UNIQUE INDEX market_quotes_kind_symbol_uk
    ON market_quotes (kind, symbol) WHERE is_deleted = FALSE;

-- Calls made per API per day, so the daily cap holds across restarts and
-- manual refreshes rather than only inside one run.
CREATE TABLE market_data_usage (
    id         UUID        PRIMARY KEY,
    kind       VARCHAR(16) NOT NULL,
    day        DATE        NOT NULL,
    calls      INTEGER     NOT NULL,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT market_data_usage_kind_check CHECK (kind IN ('STOCK', 'CRYPTO', 'CURRENCY')),
    CONSTRAINT market_data_usage_calls_check CHECK (calls >= 0)
);

CREATE UNIQUE INDEX market_data_usage_kind_day_uk
    ON market_data_usage (kind, day) WHERE is_deleted = FALSE;
