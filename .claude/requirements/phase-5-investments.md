# Phase 5 — Investment Portfolio

Status: spec'd, not built. See `00-architecture-and-foundations.md` for the
`Account` entity this builds on (`type = BROKER`).

## Scope

Track what's held in a broker account — stocks, ETFs, bonds — with a current
valuation. Deliberately **manual-first**: no broker API integration, no
automatic price feeds, no broker statement import.

## `Instrument`

A shared reference entity (like `Bank`), not per-user — two family members
holding the same ETF reference the same row.

- `id: UUID`
- `ticker: String` — e.g. `VOO`
- `isin: String?` — nullable
- `name: String`
- `kind: enum` — `STOCK`, `ETF`, `BOND`
- `currency: String` — the currency the instrument trades in
- `isDeleted: Boolean`, `createdAt`
- Unique on `ticker` **where `is_deleted = false`**, same partial-index pattern as `Bank`

## `Trade`

Trades are the source of truth; holdings are **derived from them**, not
stored. Same reasoning as budget usage and goal progress in Phase 2 — a
stored quantity can drift out of sync with its history, a derived one can't.

- `id: UUID`
- `account: Account` (FK, required — must be `type = BROKER`, enforced in the service layer)
- `instrument: Instrument` (FK, required)
- `side: enum` — `BUY`, `SELL`
- `quantity: BigDecimal` — decimal, not integer: fractional shares are normal
- `pricePerUnit: BigDecimal`
- `fee: BigDecimal` (default `0`)
- `tradedOn: LocalDate`
- `isDeleted: Boolean`, `createdAt`

**Holding** is a computed view per `(account, instrument)`: quantity is
`sum(BUY) − sum(SELL)`; cost basis is **weighted average** (not FIFO —
simpler, and adequate unless you need per-lot tax reporting). Current value
is `quantity × latest price`. Unrealized gain/loss is value minus cost basis.

**Does a trade move the account balance?** **Assumption (flag if wrong): no.**
A `BROKER` account's `balance` field tracks uninvested cash only, and trades
are recorded independently of it — so buying shares doesn't automatically
debit cash. Wiring the two together means every trade needs a matching cash
transaction, which is more bookkeeping than this is worth. Portfolio value is
reported separately from account balance.

## `PriceSnapshot`

- `id: UUID`
- `instrument: Instrument` (FK)
- `price: BigDecimal`
- `asOf: LocalDate`
- `isDeleted: Boolean`, `createdAt`

Manually entered via `PUT /api/v1/instruments/{id}/price`. Valuation uses the
most recent snapshot per instrument. **Deferred, not rejected:** pulling
prices from a market-data API on a schedule — the entity shape above works
unchanged whether a row is typed in by hand or written by a job later.

## Currency — the decision deferred from `00-`

Instruments trade in their own currency (a US ETF in `USD`), while everything
else defaults to `KZT`. **Recommendation: report portfolio value in each
instrument's native currency, and don't convert at all for now.** Conversion
needs an exchange-rate source and a decision about which rate applies when —
real work for a portfolio a family checks occasionally. If a single combined
figure becomes necessary (most likely for Phase 6's net worth), add a
manually-maintained `ExchangeRate` table (`from`, `to`, `rate`, `asOf`)
following exactly the same manual-first pattern as `PriceSnapshot`.

## API

| Method | Path                             | Purpose                                          |
|--------|-----------------------------------|---------------------------------------------------|
| GET    | `/api/v1/instruments`                | list/search instruments                          |
| POST   | `/api/v1/instruments`                | find-or-create by ticker                         |
| PUT    | `/api/v1/instruments/{id}/price`     | record a price snapshot                          |
| GET    | `/api/v1/accounts/{id}/holdings`     | derived holdings for a broker account, with value and gain/loss |
| GET    | `/api/v1/trades`                     | list trades (`accountId`, date range filters)    |
| POST   | `/api/v1/trades`                     | record a trade                                   |
| PATCH  | `/api/v1/trades/{id}`                | correct quantity/price/date                      |
| DELETE | `/api/v1/trades/{id}`                | soft delete (holdings recompute automatically)   |
