# To do

Ideas and deferred work that are wanted but not started, or started and not
finished. The phase docs in `.claude/requirements/` say what each feature
should be; this file only says what is waiting and on what.

## Market data — prices API Ninjas cannot give

Market data is built and was run with the real key on 2026-10-09 (design and
the full results table: `phase-5-investments.md`, "Market data"). Rates,
US-listed stocks and ETFs, and coins are priced. TON is priced under its new
name: record it as `GRAM/USD`, or rename existing trades with
`POST /api/v1/accounts/{id}/holdings/rename`.

**Not available from API Ninjas at all: everything in the KZT broker account**
(`KZTO`, `HSBK`, …) — KASE is not covered. `HSBK.IL` exists but is the London
GDR in USD, a different instrument at a different price. The owner decided on
2026-10-09 to skip KASE: those holdings show purchase cost only and are
counted in `unpriced` on the totals. If that ever becomes annoying, a
hand-entered price per ticker is the smallest fix.

Still open:
- **The API Ninjas attribution** on the Investments page, which the free plan
  requires; the frontend session has been asked. (The plan's "data caching
  not allowed" line was settled by the owner on 2026-10-09: fine for a private
  household tool.)
- **Pre-filling the KZT rate** on a new transaction from the stored rate
  (`GET /api/v1/market-data/rates`), which is typed by hand today.

## Phase 6 — net worth

**Waiting on:** the owner's go-ahead, and a decision on how unpriced holdings
(above) count toward net worth — at cost, or left out. Net worth
needs today's value of the investments and today's rate for every non-KZT
account; both now have a source. The spec in `phase-6-net-worth.md` predates
how Phase 5 was built and must be re-reviewed before any of it is implemented.

Its transaction export to XLSX does not depend on any of this and could be
built on its own if wanted sooner.

## Versioned compose images

Spec'd under Non-Functional Requirements in
`00-architecture-and-foundations.md`, not started: every compose service gets
an explicit image version tag, so the running version is visible and a bad
upgrade can be rolled back.
