# Phase 2 — Budgets & Goals

Status: spec'd, not built. See `00-architecture-and-foundations.md` for the
`User`/`Account`/`Category` entities this builds on.

**Scope decision:** budgets and goals belong to a single `owner: User`,
consistent with how `Account` and `Category` already work. Shared
household-wide budgets aren't supported and aren't planned.

## `Budget`
- `id: UUID`
- `owner: User` (FK, required)
- `category: Category` (FK, required)
- `limitAmount: BigDecimal`
- `period: enum` — `MONTHLY` only for now; don't build `YEARLY` until there's an actual need
- `alertThresholdPercent: Int?` — nullable, e.g. `80`. **Display cue only** — there is no alerting job, no notification, no delivery mechanism. The API returns it alongside computed usage so the UI can style the progress bar (amber past the threshold, red past 100%). Nothing server-side reacts to it.
- `isDeleted: Boolean` (default `false`) — see `00-`'s soft-delete rule
- `createdAt`
- Constraint: unique on `(owner_id, category_id)` **where `is_deleted = false`** — one active budget per category per person; "usage" is always computed against the *current* calendar month, not a stored per-month row
- Usage is computed on read (not stored): sum of that owner's `EXPENSE` transactions in that category for the current month (`Asia/Almaty`; `ADJUSTMENT` transactions are excluded — see `00-`), reused from the existing monthly-summary aggregation logic but filtered to one category
- Soft-deleting a `Category` that a non-deleted `Budget` references must be blocked — a **service-layer check**, not an FK restrict (an FK can't see `is_deleted`, since the row still physically exists)

## `Goal`
- `id: UUID`
- `owner: User` (FK, required)
- `name: String`
- `type: enum` — `SAVINGS`, `EMERGENCY_FUND`. **`DEBT_PAYOFF` was removed** — it depended on a `Debt` entity that no longer exists (loans/mortgages are tracked as ordinary expense categories now, so there's no stored "remaining owed" figure to measure progress against).
- `targetAmount: BigDecimal`
- `targetDate: LocalDate?` — nullable
- `linkedAccount: Account` (FK, required) — must be owned by the same user (same ownership check pattern as `AccountService.getOwnedBy`); a user can point more than one goal at the same account (e.g. two separate savings milestones on one account)
- `status: enum` — `ACTIVE`, `ABANDONED` (user-set only; there's no separate `ACHIEVED` *state* — see progress below)
- `isDeleted: Boolean` (default `false`) — see `00-`'s soft-delete rule
- `createdAt`
- Soft-deleting an `Account` that a non-deleted `Goal` references must be blocked — service-layer check, same as the category/budget rule above

## Progress — tied to the linked account's balance, computed on read, not stored
- `progress = linkedAccount.balance / targetAmount` (clamp 0–100%)
- An `achieved: Boolean` field is derived (`progress >= 100%`) and returned in the API response; it is **not** a persisted state transition — the user can still see and abandon an "achieved" goal if they want

## API
| Method | Path                | Purpose                                                |
|--------|----------------------|---------------------------------------------------------|
| GET    | `/api/v1/budgets`       | list the caller's budgets with computed usage this month |
| POST   | `/api/v1/budgets`       | create (rejects duplicate owner+category)               |
| PATCH  | `/api/v1/budgets/{id}`  | update limit / alert threshold                          |
| DELETE | `/api/v1/budgets/{id}`  | soft delete                                             |
| GET    | `/api/v1/goals`         | list the caller's goals with computed progress          |
| POST   | `/api/v1/goals`         | create                                                  |
| PATCH  | `/api/v1/goals/{id}`    | update target amount/date, or set `status = ABANDONED`  |
| DELETE | `/api/v1/goals/{id}`    | soft delete                                             |

No separate "contribute to goal" endpoint — contributions happen through
ordinary `Transaction`s into/out of the linked account, consistent with
tracking progress off the account balance rather than a separate ledger.
