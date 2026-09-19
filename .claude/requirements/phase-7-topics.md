# Phase 7 — Topics (trips, events, projects)

Status: **built.** See `00-architecture-and-foundations.md` for the
`User`/`Account`/`Category`/`Transaction` entities this builds on, and
`phase-0-1-foundation-ledger.md` for the ledger itself.

> **Number reuse:** the original Phase 7 (*Automation & Family Access*) was
> dropped and is unrelated to this. Nothing referenced it, so the number is
> reused rather than left as a hole. Phases 5 and 6 keep their numbers.

## Scope

A **topic** groups transactions that belong to one real-world undertaking —
*Trip to Malaysia*, *Kitchen renovation*, *Wedding* — so they can be viewed
and totalled together. It adds **no new money concepts**: every expense is an
ordinary `Transaction` against an ordinary `Account`, recorded exactly as it is
today. A topic is a *lens* over the ledger, not a second ledger.

**Why this isn't a `Category`.** Categories answer *what the money was for*
(food, transport) and are a permanent taxonomy with budgets attached. A topic
answers *what occasion the money belonged to*, is finite in time, and cuts
**across** categories — a trip has food, transport and accommodation in it.
Forcing trips into the category tree would corrupt every budget and every
monthly breakdown with one-off branches. The two are orthogonal: a transaction
may have both, and usually should.

**Explicitly out of scope:** per-topic budgets with alert thresholds
(`plannedAmount` below is a single number, not a `BudgetVersion` history),
shared/multi-user topics, nested topics, and any automatic assignment rule.

## `Topic`

- `id: UUID`
- `owner: User` (FK, required) — per-owner, exactly like `Account` and `Category`
- `name: String` — e.g. *Малайзия 2026*
- `description: String?` — nullable, up to 1000 chars
- `startDate: LocalDate?`, `endDate: LocalDate?` — **metadata, not a
  constraint.** They describe when the undertaking happened and drive the
  "suggest transactions in this window" helper below; a transaction dated
  outside them may still be attached (a deposit paid months earlier, a refund
  arriving after). If both are set, `endDate` must not precede `startDate`
- `plannedAmount: BigDecimal?` — nullable; what you *expected* to spend, in
  KZT. **Display only**, like `alertThresholdPercent` on a budget: nothing
  alerts, nothing blocks. Present so the view can show spent against intent
- `status: enum` — `ACTIVE`, `CLOSED`. User-set. `CLOSED` keeps the record but
  drops it from the transaction form's picker, since a finished trip should
  stop cluttering daily entry. **Two states, not three** — a topic has no
  equivalent of a goal's "abandoned": an undertaking that didn't happen has no
  transactions and can simply be deleted
- `isDeleted: Boolean` (default `false`) — **no `@SQLRestriction`**, for the
  same reason as `Category`: historical transactions keep pointing at a deleted
  topic, and the restriction would apply to relationship loading and silently
  null them out. Filter explicitly in the list query
- `createdAt`, `updatedAt`
- Constraint: unique on `(owner_id, lower(name))` **where `is_deleted = false`**
  — same partial-index pattern as `Bank`. Two live trips called *Малайзия* are
  a typo, not a plan

## Membership — a nullable FK on `Transaction`

`Transaction` gains `topic: Topic?` (`topic_id`, nullable, indexed).

- **One topic per transaction.** *Assumption (flag if wrong):* a single expense
  belongs to at most one undertaking. A join table would allow two, at the cost
  of making every total ambiguous — the same 50 000 ₸ would count fully toward
  two topics, and a household total across topics would double-count it. If
  overlapping topics ever become real, that's a deliberate migration, not
  something to leave a door open for now.
- **`EXPENSE` and `INCOME` only.** `TRANSFER` and `ADJUSTMENT` are **rejected**
  with a field error, consistent with how both are excluded from budgets and
  the monthly summary (`00-`). A transfer moves money between your own accounts
  — attaching one to a trip would count the cash withdrawal *and* the meal it
  paid for. `INCOME` is allowed because refunds, cancelled bookings and shared
  costs paid back by a travel companion genuinely belong to the trip's net cost.
- The topic must be **owned by the caller**, same `getOwnedBy` check as
  categories and accounts; an unknown or foreign id is a 404, never a silent
  no-op.
- A **`CLOSED`** topic still accepts attachments (a late invoice is normal) —
  closing affects the picker, not the API.
- Assignment happens two ways: `topicId` on `POST /api/v1/transactions` and on
  `PATCH`, and a bulk attach endpoint. **Bulk matters**: a trip is usually
  tagged *after* getting home, so the natural flow is "select 23 rows from last
  week and attach them", not editing 23 transactions one at a time.
- On `PATCH`, `topicId` is an `Optional<UUID>` so an explicit `null` detaches —
  the same absent-vs-null convention as `categoryId` (`00-`).

## Totals — derived, never stored

Same rule as budget usage and goal progress: a stored total drifts, a derived
one can't.

- `spent` — sum of the topic's `EXPENSE` transactions in **`amountKzt`**, so
  mixed currencies are never added together. A Malaysian trip paid partly in
  MYR and partly in KZT is exactly the case this has to get right
- `received` — the same over its `INCOME` transactions (refunds, repayments)
- `net` — `spent - received`, the honest cost of the undertaking
- `remaining` — `plannedAmount - net` when `plannedAmount` is set, `null`
  otherwise; goes **negative** on overspend, like a budget's `remaining`
- `transactionCount`, and `firstTransactionOn`/`lastTransactionOn` — the real
  span, which is often more informative than the declared `startDate`/`endDate`
- `byCategory` — the same `CategorySummary` shape the monthly summary already
  returns, so the frontend reuses its existing chart rather than growing a
  second one
- **Reporting currency is KZT**, like every other total in the system. A
  per-currency breakdown (*spent 4 200 MYR and 180 000 ₸*) is genuinely useful
  for a trip and is the one extension worth considering later; it needs no new
  data, only a `GROUP BY currency`

## Listing a topic's transactions

`GET /api/v1/topics/{id}/transactions` returns **every** transaction attached
to the topic, newest first, with **no date range required** and no pagination.

This deliberately differs from `GET /api/v1/transactions`, whose one-year cap
exists because an unbounded ledger query is unbounded. Topic membership is
itself the bound — a trip has tens of transactions, not tens of thousands — so
forcing a date range would be a worse API for no benefit. `topicId` is **also**
added as an optional filter on `GET /api/v1/transactions`, where the usual
`from`/`to` rules still apply, for the "trip spending last March" case.

## Finding candidates

`GET /api/v1/topics/{id}/candidates` returns the caller's **unattached**
`EXPENSE`/`INCOME` transactions falling inside the topic's
`startDate`..`endDate`, newest first — the picker feeding bulk attach.

- Requires both dates to be set; otherwise **400**, since "everything you ever
  recorded" is not a candidate list
- Excludes transactions already attached to **any** topic, so the list shrinks
  as you work through it
- It is a **suggestion, never an action**: nothing is attached automatically.
  Dates are a weak signal — a flight booked in March belongs to a June trip,
  and lunch on the day you flew home might not be trip spending at all

## Deleting

- Soft-deleting a topic **never touches its transactions** — they stay in the
  ledger with their `topic_id` intact, exactly as a soft-deleted category does.
  A topic is a view; deleting a view must not delete money
- No service-layer block on deleting a topic that still has transactions,
  unlike the category/budget and account/goal rules. Those protect a *live
  configuration* that computes something; a topic computes nothing but its own
  view, so deleting one is safe and reversible by recreating it
- Deleting an `Account` or `Category` is unaffected by topics

## API

| Method | Path                                   | Purpose                                                        |
|--------|----------------------------------------|----------------------------------------------------------------|
| GET    | `/api/v1/topics`                       | list the caller's topics with totals; `?status=ACTIVE` filters, omit for all |
| POST   | `/api/v1/topics`                       | create                                                          |
| GET    | `/api/v1/topics/{id}`                  | one topic with its full totals and `byCategory` breakdown       |
| PATCH  | `/api/v1/topics/{id}`                  | name, description, dates, `plannedAmount`, `status`             |
| DELETE | `/api/v1/topics/{id}`                  | soft delete; transactions keep their link                       |
| GET    | `/api/v1/topics/{id}/transactions`     | every attached transaction, newest first, no range required     |
| GET    | `/api/v1/topics/{id}/candidates`       | unattached transactions inside the topic's date window          |
| POST   | `/api/v1/topics/{id}/transactions`     | bulk attach — `{"transactionIds": [...]}`; rejects `TRANSFER`/`ADJUSTMENT` |
| DELETE | `/api/v1/topics/{id}/transactions/{transactionId}` | detach one (equivalent to `PATCH` with `topicId: null`) |

Bulk attach is **all-or-nothing**: if any id is unknown, not the caller's, or a
`TRANSFER`/`ADJUSTMENT`, the whole call is rejected with the offending ids
named. A partial success would leave the user guessing which of 23 rows landed.

## Migration

`V7__topics.sql`:
- `topics` table, snake_case, with the partial unique index on
  `(owner_id, lower(name)) WHERE is_deleted = false`
- `transactions.topic_id` — nullable FK, `ON DELETE RESTRICT` (soft delete
  means rows are never hard-deleted anyway)
- Index on `transactions(topic_id)` — every topic view filters on it, and
  `00-` requires FKs to be indexed in the same migration that adds them

## Frontend

Covered properly in the frontend requirements; the shape it needs:

- `/topics` — cards or a table: name, date range, spent, and planned vs spent
  where `plannedAmount` is set
- `/topics/:id` — the header totals, the `byCategory` chart (reusing the
  dashboard's), and the transaction list
- A **topic picker on the transaction form**, listing `ACTIVE` topics only,
  and shown for `EXPENSE`/`INCOME` but hidden for `TRANSFER` — mirroring how
  the category picker already behaves
- **Bulk attach** on the topic detail page, driven by `/candidates`, with
  multi-select — the primary way a trip actually gets tagged
- A `topicId` filter on `/transactions`
- Russian UI label: «Тема» is the literal translation but reads oddly for a
  trip; «Событие» (event) covers trips, renovations and weddings more
  naturally. The entity stays `Topic` in code either way
