# Phase 6 — Net Worth & Reporting

Status: spec'd, not built. Depends on Phase 0/1 (accounts) and Phase 5
(holdings).

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
| GET    | `/api/v1/reports/transactions.xlsx`| transaction export (date range, optional account)   |

**XLSX, not CSV or PDF.** Excel output keeps number and date formatting
intact — a CSV of `BigDecimal` amounts loses the formatting and invites
locale trouble with decimal separators, which matters here since KZT amounts
run large. Generate with **Apache POI** (`org.apache.poi:poi-ooxml`), streamed
straight to the response rather than written to disk first.

PDF stays out: it needs a template engine and layout work for something a
spreadsheet handles better. Add it later only if a genuinely printable report
is ever needed.
