# Phase 6 — Net Worth & Reporting

Status: net worth is spec'd, not built; it depends on Phase 0/1 (accounts) and
Phase 5 (holdings). The **transaction export is built** (2026-10-13) — it
depends on neither; see "Transaction export" at the end.

**Re-review before building (noted 2026-10-08).** Phase 5 was built differently
from the draft this document assumed: there are no instruments, no price
snapshots and no exchange-rate table, so a holding has a **cost** (in its
account's currency and in KZT) but no current value. Everything below that
says "latest snapshot prices" has nothing to read yet. Either the investment
total is reported at cost and labelled as such, or this phase waits for the
price and rate sources Phase 5 defers. The same gap applies to a non-KZT cash
account, which has no current rate either.

**Decided by the owner, 2026-10-08: this phase waits** until they have chosen
open APIs for exchange rates and for crypto and stock prices. Those now exist
(API Ninjas, and kase.kz for KZT stocks — see the Phase 5 doc), but on
2026-10-15 the owner dropped it from the to-do list: build it only if
asked, and re-review this doc first. Never build it at cost.

## The key constraint: balances have no history

`Account.balance` is a single current figure — there's no record of what it
was last March. So a net-worth *trend* can't be derived retroactively; it has
to be **captured going forward** as snapshots. This is the one piece of
stored-rather-derived state in the whole system, and it's stored for exactly
that reason.

(Balances *could* be reconstructed by replaying the transaction ledger
backwards, but that breaks the moment an opening balance is set directly or a
transaction is soft-deleted — snapshots are far more robust.)

## `NetWorthSnapshot`

- `id: UUID`
- `owner: User` (FK, required)
- `asOf: LocalDate`
- `cashTotal: BigDecimal` — sum of `CASH`/`BANK`/`DEPOSIT` account balances
- `investmentTotal: BigDecimal` — sum of holdings at their latest snapshot prices
- `total: BigDecimal` — the two above, added
- `currency: String`
- `isDeleted: Boolean`, `createdAt`
- Unique on `(owner_id, as_of)` **where `is_deleted = false`** — one snapshot per person per day

Written by a **daily scheduled job** (Spring `@Scheduled`), plus a manual
`POST /api/v1/net-worth/snapshot` for backfilling or forcing one. **Assumption
(flag if wrong): daily.** Monthly would be plenty for a chart and 30× fewer
rows, but daily costs almost nothing at this scale and can be thinned later.

## Assets only — a deliberate limitation

Loans and mortgages are tracked as expense categories, so no "remaining owed"
figure exists anywhere to subtract. **This number is assets, not true net
worth.** Recommend labelling it "Total Assets" in the UI rather than "Net
Worth", so the figure isn't mistaken for something it isn't. If it ever needs
to reflect debt, that means reintroducing a stored balance for debts — a
decision consciously deferred, not an oversight.

**Multi-currency caveat:** if Phase 5 lands with no conversion (its
recommendation), `investmentTotal` can only sum instruments already in the
snapshot's currency. Either restrict the total to matching-currency holdings
and label it as such, or add the manual `ExchangeRate` table Phase 5
describes. Resolve this when Phase 5 is actually built, not before.

## API

| Method | Path                          | Purpose                                              |
|--------|--------------------------------|-------------------------------------------------------|
| GET    | `/api/v1/net-worth/current`       | today's figure, computed live (not from a snapshot)  |
| GET    | `/api/v1/net-worth/history`       | snapshot series (`from`/`to`) for the trend chart    |
| POST   | `/api/v1/net-worth/snapshot`      | force a snapshot now                                 |
| GET    | `/api/v1/reports/transactions.xlsx`| transaction export — built, see below               |

## Transaction export (built 2026-10-13)

`GET /api/v1/reports/transactions.xlsx?from=&to=` returns the caller's
transactions as an Excel file, as an attachment named
`transactions_<from>_<to>.xlsx`.

- **It is the transaction list as a file.** Same parameters as
  `GET /api/v1/transactions` — `from` and `to` required, at most one year,
  optional `accountId`, `categoryId`, `topicId` — the same rows in the same
  order (newest first), and the same 400/404. It is implemented on top of that
  list, so the two cannot drift. The original spec named only the account
  filter; the other two came free and let a filtered view be exported as seen.
- **Own transactions only.** A viewer of a shared account reads its history
  through `GET /api/v1/accounts/{id}/transactions`; the export does not reach
  shared resources, so it adds nothing to what Phase 8 discloses.
- **One sheet, one row per transaction:** Date, Type, Account, To account,
  Category, Topic, Amount, Currency, Rate to KZT, Amount KZT, To amount,
  To currency, Trade side, Ticker, Quantity, Unit price, Note. Accounts,
  categories and topics appear by name. A value a transaction does not have is
  an empty cell, never a zero.
- **Amounts are as the ledger stores them** — positive, with Type (and Trade
  side) saying which way the money moved; only an `ADJUSTMENT` is signed.
  Nothing is totalled in the file: Amount mixes currencies down the column,
  and Amount KZT is the one that can be summed.
- Dates and numbers are real date and number cells; text is text, so a note
  beginning with `=` is not a formula. The header row is frozen and filterable.

**XLSX, not CSV or PDF.** Excel output keeps number and date formatting
intact — a CSV of `BigDecimal` amounts loses the formatting and invites
locale trouble with decimal separators, which matters here since KZT amounts
run large. Generated with **Apache POI** (`org.apache.poi:poi-ooxml`), built
in memory and written to the response, never to disk — a year of one
household's ledger is small, and POI's streaming mode would spool to temp
files. Column widths are fixed because POI's auto-sizing needs fonts the
Alpine JRE image does not have.

An error is JSON even when the request accepts only a spreadsheet; the
exception handler presets the content type for that reason.

PDF stays out: it needs a template engine and layout work for something a
spreadsheet handles better. Add it later only if a genuinely printable report
is ever needed.
