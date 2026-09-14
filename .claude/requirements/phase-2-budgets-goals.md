# Phase 2 — Budgets & Goals

Status: **built.** See `00-architecture-and-foundations.md` for the
`User`/`Account`/`Category` entities this builds on.

**Scope decision:** budgets and goals belong to a single `owner: User`,
consistent with how `Account` and `Category` already work. Shared
household-wide budgets aren't supported and aren't planned.

## `Budget` — identity, versioned limits
A budget is **stable identity**; its limit lives in dated versions, so a past
month always reports the limit that actually applied then rather than today's.

- `id: UUID`
- `owner: User` (FK, required)
- `category: Category` (FK, required) — **fixed after creation**, since usage for a different category is simply a different budget. **Must be `kind = EXPENSE`**; usage only ever counts `EXPENSE` transactions, so an income budget could never read anything but zero
- `period: enum` — `MONTHLY` only for now; don't build `YEARLY` until there's an actual need
- `isDeleted: Boolean` (default `false`) — **no `@SQLRestriction`**, see `00-`
- `createdAt`, `updatedAt`
- Constraint: unique on `(owner_id, category_id)` **where `is_deleted = false`** — one live budget per category per person

## `BudgetVersion`
- `id: UUID`
- `budget: Budget` (FK, required)
- `limitAmount: BigDecimal`
- `alertThresholdPercent: Int?` — nullable, e.g. `80`. **Display cue only** — there is no alerting job, no notification, no delivery mechanism. The API returns it alongside computed usage so the UI can style the progress bar (amber past the threshold, red past 100%). Nothing server-side reacts to it.
- `effectiveFromMonth: LocalDate`, `effectiveToMonth: LocalDate?` — both the **first day** of a month; `effectiveToMonth` is **inclusive** and `null` means still in force
- Constraint: at most one open version per budget (partial unique index on `effective_to_month IS NULL`)
- **Creating** a budget opens a version from the current month. **Changing** a limit closes the open version at the end of *last* month and opens a new one from this month — unless the open version already started this month, in which case it is corrected in place rather than leaving two versions for one month. **Deleting** closes it at the end of last month (or drops the version entirely if it started this month and so never applied), and marks the budget deleted.
  - ⚠ Hibernate orders inserts before updates within a flush, so closing the old version must be flushed *before* inserting the new one or the partial unique index trips.

## Usage
- Computed on read, never stored: sum of that owner's `EXPENSE` transactions for the month in question (`Asia/Almaty`; `TRANSFER` and `ADJUSTMENT` excluded — see `00-`), summing **`amountKzt`** so mixed currencies are never added together
- **Rolls up sub-categories to arbitrary depth** — a budget on *Food* includes spending filed under *Food → Fruit*. A parent and a child may both have budgets, and the child's spending counts toward both: the parent is a cap over the group
- `GET /api/v1/budgets?month=2026-09` reports any month, defaulting to the current one. A budget that did not exist that month is simply absent
- `percentUsed` is **not** capped at 100, and `remaining` goes negative — overspend is information to show, not hide
- Soft-deleting a `Category` whose budget still has an **open** version must be blocked — a **service-layer check**, not an FK restrict (an FK can't see `is_deleted` or an effective range). A closed version is history and does not block.

## `Goal`
- `id: UUID`
- `owner: User` (FK, required)
- `name: String`
- `type: enum` — `SAVINGS`, `EMERGENCY_FUND`. **`DEBT_PAYOFF` was removed** — it depended on a `Debt` entity that no longer exists (loans/mortgages are tracked as ordinary expense categories now, so there's no stored "remaining owed" figure to measure progress against).
- `targetAmount: BigDecimal`
- `targetDate: LocalDate?` — nullable
- `linkedAccount: Account` (FK, required) — **fixed after creation**: progress is measured against it, so swapping it would silently rewrite what every past reading meant. Must be owned by the same user (same ownership check pattern as `AccountService.getOwnedBy`); a user can point more than one goal at the same account (e.g. two separate savings milestones on one account)
- `status: enum` — `ACTIVE`, `ABANDONED`, `ARCHIVED` (user-set only; there's no separate `ACHIEVED` *state* — see progress below). `ARCHIVED` is "done with this, keep the record"
- `targetAmount` is denominated in the **linked account's currency** — converting a balance would need a current rate, which `00-` deliberately doesn't store
- `isDeleted: Boolean` (default `false`) — see `00-`'s soft-delete rule
- `createdAt`
- Soft-deleting an `Account` that an **`ACTIVE`** `Goal` references must be blocked — service-layer check, same as the category/budget rule above. Abandoned and archived goals are kept for the record and deliberately do not block: giving up on a goal shouldn't force you to delete it before closing the account

## Progress — tied to the linked account's balance, computed on read, not stored
- `progress = linkedAccount.balance / targetAmount` (clamp 0–100%)
- An `achieved: Boolean` field is derived (`progress >= 100%`) and returned in the API response; it is **not** a persisted state transition — the user can still see and abandon an "achieved" goal if they want

## API
| Method | Path                | Purpose                                                |
|--------|----------------------|---------------------------------------------------------|
| GET    | `/api/v1/budgets`       | list the caller's budgets with usage; `?month=2026-09`, defaults to current |
| POST   | `/api/v1/budgets`       | create (rejects duplicate owner+category)               |
| PATCH  | `/api/v1/budgets/{id}`  | update limit / alert threshold; takes effect from this month onward |
| DELETE | `/api/v1/budgets/{id}`  | stops it from this month onward; past months keep reporting it |
| GET    | `/api/v1/goals`         | list the caller's goals with computed progress          |
| POST   | `/api/v1/goals`         | create                                                  |
| PATCH  | `/api/v1/goals/{id}`    | update name/target amount/date, or set `status`          |
| DELETE | `/api/v1/goals/{id}`    | soft delete                                             |

No separate "contribute to goal" endpoint — contributions happen through
ordinary `Transaction`s into/out of the linked account, consistent with
tracking progress off the account balance rather than a separate ledger.
