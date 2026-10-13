# Phase 9 — Planned purchases

Status: **built** (2026-10-13). Stands alone: it uses `User` and nothing else.

## Where it came from

The owner's wife, 2026-10-13: «Надо добавить функционал чтоб записывать общие
семейные покупки. Это всегда общее для всех. И ещё есть личные покупки.»

The owner, the same day: a list of planned purchases, personal and family,
where an item is marked `isBought` true or false and can be deleted. **That is
all of it.**

## Scope

A list of things to buy. Each item has a **name**, is either **personal** or
**family**, and is either **bought** or not.

- A **personal** item is seen and changed only by whoever added it.
- A **family** item is common to everyone: every member sees it, and any
  member can mark it bought, rename it or delete it. A shared shopping list
  only its author can tick off would not be shared.
- Personal is the default when adding.

**Out of scope, deliberately:** amounts, prices, quantities, due dates,
categories, and any link to the ledger. **Marking an item bought does not
create a `Transaction`** — the spend is recorded separately, as it would be
without this feature (the same choice as `Bill.isPaid` in Phase 4).

## `PlannedPurchase`

- `id: UUID`
- `owner: User` (FK, required) — who added it. For a family item this is a
  label ("added by"), not a permission.
- `name: String` (max 255, not blank, trimmed)
- `isFamily: Boolean` — set when the item is added and **not editable**:
  moving an item between the two lists is delete and add again. It keeps a
  member from taking a family item private, or publishing by accident.
- `isBought: Boolean`, default `false`
- `isDeleted: Boolean`, `createdAt`, `updatedAt` — soft delete, like everything

No uniqueness: the same thing can be on the list twice.

## Access

One rule for every call: **a family item is anyone's; a personal item is its
owner's.** Somebody else's personal item is a **404** on every endpoint, never
a 403, so its existence is not revealed.

This is not Phase 8 sharing and does not touch it: there is no `Share` row and
no grantee. Nothing here exposes money — an item is a name and two flags.

## API

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/planned-purchases` | the caller's personal items and every family item |
| POST | `/api/v1/planned-purchases` | add: `{name, isFamily}` (`isFamily` defaults to `false`) |
| PATCH | `/api/v1/planned-purchases/{id}` | `{name?, isBought?}` — mark bought or not, rename |
| DELETE | `/api/v1/planned-purchases/{id}` | soft delete |

Response: `id`, `name`, `isFamily`, `isBought`, `addedBy {id, displayName}`,
`createdAt`. The list is ordered still-to-buy first, then newest first; it is
not paginated or date-bounded, since deleting is how it stays short. Bought
items stay on the list until deleted.

## Migration

`V12__planned_purchases.sql` creates `planned_purchases`.

It also **drops `transactions.is_family`**, which `V11` added. V11 belonged to
a first reading of this request — a flag on an expense, with a household view
of everyone's family expenses — that the owner rejected as too complicated the
day it shipped (backend 1.2.0). No client could set the flag, so the column
was dropped unused. V11 stays in the history because Flyway had applied it.
Do not rebuild that design from the migration's name.

## Testing

Unit tests cover the access rule in both directions (a member changing and
deleting a family item of someone else's; the same on a personal item being a
404), the defaults, renaming, and un-marking. The flow was driven once by hand
with two users against a throwaway Postgres, and V12 was trialled on a restored
copy of the live database before it was deployed.

## Frontend

- A «Покупки» page with two lists, «Мои» and «Семейные», from the one `GET`
  split on `isFamily`.
- A field to add an item, with a choice of which list.
- A checkbox per item for `isBought`, and a delete.
- Family items show who added them (`addedBy`).
