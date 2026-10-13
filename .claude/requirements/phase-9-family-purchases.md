# Phase 9 — Family purchases

Status: **built** (2026-10-13). Builds on the `Transaction` ledger of Phase 0/1
and on the `User` list Phase 8 opened to every member.

## Where it came from

The owner's wife, 2026-10-13: «Надо добавить функционал чтоб записывать общие
семейные покупки. Это всегда общее для всех. И ещё есть личные покупки.» A
purchase is either a shared family one, common to everyone, or a personal one.

## Scope

An `EXPENSE` can be marked as a **family purchase**. Every family purchase,
whoever recorded it, appears in one **Family view** that every member of the
household can read. Everything not marked stays exactly as private as it was.

Decided by the owner on 2026-10-13:

- **Family means everyone sees it.** Not a private label: marking a purchase
  publishes it to the household.
- **Personal is the default.** A new expense is personal unless marked, so a
  forgotten tick hides a purchase instead of exposing one.
- **Others see the purchase, not the account.** Amount, currency, KZT amount,
  date, category name, note, and who paid. Never which account it was paid
  from, and never a balance.
- **Expenses only.** Income, transfers, adjustments and trades cannot be
  family purchases.

**Out of scope, deliberately:** splitting a purchase between people or working
out who owes whom; a household budget; family income; household-owned accounts
or categories (accounts stay single-owner, see `00-`); editing someone else's
family purchase.

## How this differs from Phase 8 sharing

Phase 8 shares **one resource with one person**, chosen each time. This is the
opposite shape: **one flag, the whole household**, no grantee and no `Share`
row. The two do not interact — a family purchase is visible in the Family view
whether or not its account is shared, and sharing an account still shows all
of its transactions, family or not.

It is a second deliberate crack in the single-owner rule, and the same warning
applies: **the Family view is a read path with many legitimate callers; every
write path still has exactly one.** Marking, unmarking, editing and deleting a
family purchase all go through the existing owner-only transaction endpoints.

## Data

`Transaction.isFamily: Boolean`, not null, default `false`.

- Only an `EXPENSE` may have it `true` — rejected in the service with a 400,
  and held by a check constraint as well.
- It is an ordinary editable field: the owner of a transaction can mark or
  unmark it later through `PATCH`. Unmarking removes it from the Family view
  at once.
- Soft-deleting the transaction removes it from the Family view like from
  everywhere else. A purchase on an account that was later soft-deleted stays
  — it still happened.

## What a family purchase exposes

Deliberately a **separate response type** from `TransactionResponse`, so the
fields that must not leak are not there to forget:

- shown: `id`, `amount`, `currency`, `amountKzt`, `occurredOn`,
  `categoryName`, `note`, `paidBy {id, displayName}`, `isMine`
- **not shown:** `accountId` or any account field, `exchangeRate`, `topic`,
  the category's id or parent

The **note is shown**, so a note written on a family purchase is written for
the household. The category is by **name only**: categories are per member
(`00-`), so two members' «Продукты» are different rows, and the Family view
groups them by name — the same name is the same line, whoever owns the row.

## Aggregates

The Family view's own totals **do** sum across members — that is its purpose,
and it is the only place in the system that does. In KZT, from `amountKzt`,
like every other total.

**Nothing else changes.** A member's own monthly summary, budget usage and
topic totals still count exactly their own transactions: their own family
purchases as before (it was their money), and nobody else's. Phase 8's
"aggregates never mix" rule stands everywhere outside this view.

## API

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/family/purchases?from=&to=` | every member's family purchases in the range, with totals |

- Any authenticated user. `from` and `to` required, at most one year, like the
  transaction list; newest first; no pagination.
- Response: `from`, `to`, `currency` (always `KZT`), `total`, `byMember`
  (`{member {id, displayName}, total}`, largest first), `byCategory`
  (`{categoryName, total}`, largest first; `null` name for uncategorised), and
  `purchases`.

On the existing transaction endpoints:

- `POST /api/v1/transactions` accepts `isFamily` (default `false`).
- `PATCH /api/v1/transactions/{id}` accepts `isFamily`.
- `TransactionResponse` carries `isFamily`.
- `isFamily: true` on anything but an `EXPENSE` is a **400**.

The transaction export has a `Family` column (`yes` or empty).

## Migration

`V11__family_purchases.sql`: `transactions.is_family BOOLEAN NOT NULL DEFAULT
FALSE`, a check that it is only set on an `EXPENSE`, and a partial index on
`occurred_on` for the live family rows — the Family view's only filter.

## Testing

A bug here is a disclosure, as in Phase 8:

- The Family view returns family purchases of every member and **no personal
  one**, whoever asks.
- Its response carries no account field.
- `isMine` is true exactly for the caller's own rows.
- Marking anything but an `EXPENSE` is rejected, on create and on edit.
- A member's own monthly summary is unchanged by other members' family
  purchases — it is computed by owner, and the flag does not enter it.

Unit tests cover these. The flow was also driven once by hand against a real
Postgres with two users (see the progress log); an automated integration pass
is deferred with the rest, per `00-`.

## Frontend

- A **«Семейная покупка»** checkbox on the expense form, unticked by default,
  with a line saying everyone in the family will see the amount, category,
  note and who paid.
- A **«Семейные покупки»** page from `GET /api/v1/family/purchases`: the list
  with who paid, the total, and the breakdowns by member and by category. Rows
  that are not `isMine` are read-only.
- A marker on family rows in the member's own transaction list.
