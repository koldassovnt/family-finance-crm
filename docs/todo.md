# To do

Ideas and deferred work that are wanted but not started. The phase docs in
`.claude/requirements/` say what each feature should be; this file only says
what is waiting and on what.

## Market data from open APIs

**Waiting on:** the owner choosing the APIs. Nothing should be built, and no
price or rate table added, until they are picked — the tables should follow
what the chosen APIs return.

The idea (owner, 2026-10-08): find open APIs for three things and use them to
show what the investments are worth now, next to what they cost.

- **Exchange rates** — KZT per unit of each currency an account is held in.
- **Crypto prices** — the current price of each coin held.
- **Stock market prices** — the current price of each stock, ETF and bond held.

What it unlocks:

- **Current value and unrealised gain** on `GET /api/v1/investments` and
  `GET /api/v1/accounts/{id}/holdings`, which report purchase cost only today
  (see `phase-5-investments.md`, "Deferred, not rejected").
- **Phase 6, net worth** — see below.
- Possibly pre-filling the KZT rate on a new transaction, which is typed by
  hand today.

Things to settle when choosing:

- Holdings are keyed by a free-text ticker, with no instrument table and no
  record of whether a ticker is a stock or a coin. A price lookup needs to know
  which API to ask and under what symbol, so either the account type decides
  it (`CRYPTO` → the crypto API, `BROKER` → the stock API) or a ticker needs a
  small mapping.
- A crypto account's currency is three letters, so a stablecoin balance is
  recorded as `USD`.
- This would be the project's first outbound call. The architecture doc says
  "no external services", so it needs a line there, a decision about what the
  app shows when the API is down or rate-limited, and where an API key lives
  (`.env`, like the other secrets).
- Whether prices are fetched on a schedule and stored, or on demand and cached.

## Phase 6 — net worth

**Waiting on:** the market-data APIs above (decided by the owner 2026-10-08).
Net worth needs today's value of the investments and today's rate for every
non-KZT account; neither exists until a price and a rate source do. The spec
in `phase-6-net-worth.md` predates how Phase 5 was built and must be
re-reviewed before any of it is implemented.

Its transaction export to XLSX does not depend on any of this and could be
built on its own if wanted sooner.

## Versioned compose images

Spec'd under Non-Functional Requirements in
`00-architecture-and-foundations.md`, not started: every compose service gets
an explicit image version tag, so the running version is visible and a bad
upgrade can be rolled back.
