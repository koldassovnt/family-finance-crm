-- A budget becomes stable identity (owner + category); its limit moves into
-- dated versions, so querying a past month reports the limit that actually
-- applied then rather than today's.

CREATE TABLE budget_versions (
    id                      UUID           PRIMARY KEY,
    budget_id               UUID           NOT NULL REFERENCES budgets (id),
    limit_amount            NUMERIC(19, 4) NOT NULL,
    alert_threshold_percent INTEGER,
    -- Both hold the first day of the month; effective_to_month is inclusive,
    -- and NULL means the version is still in force.
    effective_from_month    DATE           NOT NULL,
    effective_to_month      DATE,
    is_deleted              BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ    NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,
    CONSTRAINT budget_versions_limit_check CHECK (limit_amount > 0),
    CONSTRAINT budget_versions_threshold_check
        CHECK (alert_threshold_percent IS NULL
               OR (alert_threshold_percent BETWEEN 1 AND 100)),
    CONSTRAINT budget_versions_range_check
        CHECK (effective_to_month IS NULL OR effective_to_month >= effective_from_month),
    CONSTRAINT budget_versions_month_start_check
        CHECK (date_trunc('month', effective_from_month) = effective_from_month
               AND (effective_to_month IS NULL
                    OR date_trunc('month', effective_to_month) = effective_to_month))
);

CREATE INDEX budget_versions_budget_idx
    ON budget_versions (budget_id, effective_from_month) WHERE is_deleted = FALSE;
-- At most one version may be in force for a budget at a time.
CREATE UNIQUE INDEX budget_versions_open_uk
    ON budget_versions (budget_id) WHERE effective_to_month IS NULL AND is_deleted = FALSE;

-- Move each existing budget's limit into a version starting the month it was
-- created. Best-effort for any already-deleted budget: it is closed at the
-- month before its last write, clamped so the range can never be inverted.
INSERT INTO budget_versions (
    id, budget_id, limit_amount, alert_threshold_percent,
    effective_from_month, effective_to_month, is_deleted, created_at, updated_at)
SELECT
    gen_random_uuid(),
    b.id,
    b.limit_amount,
    b.alert_threshold_percent,
    date_trunc('month', b.created_at AT TIME ZONE 'Asia/Almaty')::date,
    CASE WHEN b.is_deleted THEN GREATEST(
        (date_trunc('month', b.updated_at AT TIME ZONE 'Asia/Almaty') - INTERVAL '1 month')::date,
        date_trunc('month', b.created_at AT TIME ZONE 'Asia/Almaty')::date
    ) END,
    FALSE,
    b.created_at,
    b.updated_at
FROM budgets b;

ALTER TABLE budgets
    DROP COLUMN limit_amount,
    DROP COLUMN alert_threshold_percent;
