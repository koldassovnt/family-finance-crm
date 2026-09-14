# Phase 4 — Bills & Due-Date Calendar

Status: **built.** See `00-architecture-and-foundations.md` for the
`User` entity and the soft-delete convention this builds on.

## Scope

Deliberately minimal: a place to record **date, name, amount** for anything
due — utility bills, subscriptions, loan and mortgage payments alike — and
see them on a month view. No recurrence engine, no bill-vs-transaction
reconciliation, no reminder rules. Loan and mortgage payments have no special
handling; they're just bills you enter like any other, and the payment itself
is an ordinary categorized `EXPENSE` transaction (see `00-architecture-and-foundations.md`
for how categories work).

## `Bill`

- `id: UUID`
- `owner: User` (FK, required) — per family member, same as `Account`/`Category`
- `name: String` — e.g. "Electricity", "Netflix", "Mortgage payment"
- `amount: BigDecimal`
- `currency: String` — 3-letter code, same convention as `Account`
- `dueDate: LocalDate`
- `isPaid: Boolean` (default `false`)
- `batchId: UUID?` — nullable; shared by every row created in one batch call, null for individually-created bills
- `isDeleted: Boolean` (default `false`) — see `00-`'s soft-delete rule
- `createdAt`, `updatedAt`

That's the whole entity. Unlike `Transaction`, a `Bill` carries no
`exchangeRate`/`amountKzt`: nothing sums bills, so there is no total that could
mix currencies. If a "total due this month" is ever wanted, it needs the same
treatment `00-` gives transactions. Overdue isn't a stored status — it's derived
(`!isPaid && dueDate < today`), so it can't go stale.

**Recurring bills — batch create, not a recurrence engine.** There's no
`recurrenceInterval` field and no scheduled job generating rows. Instead,
`POST /api/v1/bills/batch` takes a pattern and expands it into ordinary `Bill`
rows in one request:

- `name`, `amount`, `currency` — same for every generated row
- `dayOfMonth: Int` (1–31) — e.g. `15` for a loan payment due the 15th
- `startMonth: YearMonth`, `endMonth: YearMonth` — inclusive range

So "Loan payment, 250,000 ₸, the 15th of every month from 2026-09 through
2026-12" creates four rows in one call. Once created they're just normal
bills — edit one month's amount, delete a single row, mark them paid
individually; nothing links them together or regenerates them.

Two details worth pinning down:
- **Day-of-month overflow:** `dayOfMonth = 31` in a 30-day month (or February)
  should **clamp to the last day of that month**, not skip the month or roll
  into the next one.
- **Cap the range** — reject anything beyond, say, 120 rows in a single call,
  so a typo in `endMonth` can't generate thousands of bills.

**Generated rows share a `batchId`**, so a whole series can be managed
together: `DELETE /api/v1/bills/batch/{batchId}` soft-deletes every row in
the batch. The rows stay individually editable and deletable — the batch id
is a convenience handle, not a grouping that constrains them. Editing one
row's amount does **not** propagate to its siblings, and a row keeps its
`batchId` even after being edited.

**Marking paid** just sets `isPaid = true`. It does **not** create a
`Transaction` — you record the actual spend as a normal transaction
separately, exactly as you would without this feature. Auto-creating one
would mean this phase needs to know about accounts and categories, which is
the complexity being avoided here.

## API

| Method | Path                | Purpose                                                     |
|--------|----------------------|--------------------------------------------------------------|
| GET    | `/api/v1/bills`         | list the caller's bills, earliest due date first; `month=2026-09` filters by due date and `unpaid=true` by outstanding status, independently or together, omit both for all |
| POST   | `/api/v1/bills`         | create one bill                                             |
| POST   | `/api/v1/bills/batch`   | create many from a pattern (see above); returns the created rows |
| PATCH  | `/api/v1/bills/{id}`    | update name/amount/currency/date, or set `isPaid`. Changing `currency` requires restating `amount`, since nothing converts it |
| DELETE | `/api/v1/bills/{id}`    | soft delete one bill                                        |
| DELETE | `/api/v1/bills/batch/{batchId}` | soft delete every bill in a batch                   |

The calendar view is a frontend concern — the backend just serves the month's
bills and lets the UI lay them out.

**A month view alone hides overdue bills from earlier months** — `?month=2026-09`
returns only bills *due* in September, so an unpaid August bill is absent. That
is what `?unpaid=true` is for: it returns everything still owed regardless of
when it fell due, and `?unpaid=false` returns the settled ones.

The two filters are **independent and AND together** rather than having any
combined special meaning. So a calendar screen makes two calls: `?month=2026-09`
for the grid, and `?unpaid=true` for an arrears panel beside it. Keeping them
orthogonal is what stops `month` from quietly meaning "this month, plus some
older things too".
