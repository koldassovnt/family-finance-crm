# Phase 8 — Sharing with family members

Status: **spec'd, not built.** Builds on every entity in Phases 0/1, 2, 4 and
7, and on the `User` entity in `00-architecture-and-foundations.md`.

## Scope

Any member can share **one specific thing they own** — an account, a goal, a
budget, a bill, a topic — with another member of the household, who can then
**view it and nothing more**. Sharing is per-resource and controlled from that
resource's own detail view, not from a global setting and not per-person.

Everyone has this. It is not an `OWNER` privilege: a `MEMBER` shares their own
accounts with the `OWNER` exactly as the `OWNER` shares theirs. Sharing is
one-directional per grant — A sharing an account with B does not let A see B's.

**This is the first crack in the single-owner rule** that Phases 0–7 were built
on, where `getOwnedBy` was the one and only access check. Read that as a
warning: every read path now has two legitimate callers, and every write path
still has exactly one.

**Out of scope, deliberately:** editing shared resources, re-sharing something
shared with you, sharing a whole category of things ("all my accounts"),
household-wide resources with no owner, notifications, and any invitation flow
— users are still created by the `OWNER` through `POST /api/v1/users`.

## `Share`

One table, with a type discriminator, rather than five near-identical join
tables.

- `id: UUID`
- `resourceType: enum` — `ACCOUNT`, `GOAL`, `BUDGET`, `BILL`, `TOPIC`
- `resourceId: UUID` — **no FK**, since it points at one of five tables
- `owner: User` (FK, required) — who granted it, denormalized from the
  resource so "everything I have shared" is one query
- `grantee: User` (FK, required) — who can now see it
- `access: enum` — `VIEWER` only for now. A single-valued enum, like
  `BudgetPeriod`, so adding `EDITOR` later is a new value rather than a new
  concept
- `isDeleted: Boolean`, `createdAt`, `updatedAt`
- Constraint: unique on `(resource_type, resource_id, grantee_id)`
  **where `is_deleted = false`** — sharing the same thing twice with the same
  person is a no-op, not a second grant
- Index on `(grantee_id, resource_type)` — every viewer-side read filters on it

**Why no FK on `resourceId`.** A polymorphic column cannot have one. The
alternative is five tables (`account_shares`, `goal_shares`, …) with real FKs,
five repositories and five near-identical services. The integrity that buys is
mostly theoretical here: nothing in this system is ever hard-deleted, so a
share cannot be orphaned by a vanishing row. The service layer resolves the
resource and checks ownership before writing, which is where the real
protection lives. Revisit if a sixth shareable type appears and the `when`
dispatch starts to sprawl.

**Revoking** soft-deletes the row. The partial unique index means the same
thing can be shared again afterwards.

## Who can do what

- **Only the resource's owner may share it or revoke a share.** A viewer
  cannot re-share; an `OWNER` cannot share a `MEMBER`'s account.
- **A viewer has read access to that one resource and nothing adjacent**,
  except where reading it is meaningless without something else — see the
  exposure notes below, which are the part worth arguing with before building.
- **A viewer never writes.** Every mutating endpoint keeps the existing
  `getOwnedBy` check unchanged. This is the invariant to protect: if a write
  path ever accepts a viewer, the feature has failed.
- **Soft-deleting a shared resource** does not revoke its shares; the resource
  simply stops appearing, as it does for its owner.

## What a share actually exposes

Each of these is a decision, not an accident. The UI should say so plainly at
the moment of sharing, because "share my account" sounds narrower than it is.

- **`ACCOUNT`** — name, type, bank, currency, **balance**, and **its whole
  transaction history**: every amount, date, note, and the **category and topic
  names** on those rows. An account without its transactions is a number with
  no explanation, so sharing one means sharing what it did. A transfer to an
  account that is *not* shared shows as a transfer whose destination the viewer
  cannot resolve — the same missing-name case as a deleted account.
- **`GOAL`** — name, target, status, progress, **and the linked account's name,
  currency and balance**, because progress *is* the balance against the target.
  Sharing a goal therefore discloses that account's balance without sharing the
  account. **Assumption (flag if wrong):** acceptable, because a goal is
  meaningless otherwise. If not, the alternative is a goal response that shows
  only a percentage to viewers.
- **`BUDGET`** — the category, the limit, and the computed usage for whatever
  month is requested, including past months. Not the individual transactions
  behind the total; that is the account's share to give.
- **`BILL`** — name, amount, currency, due date, paid flag, derived overdue.
  Nothing adjacent.
- **`TOPIC`** — its totals, its per-category breakdown, **and its attached
  transactions** — which belong to accounts the viewer may not otherwise see.
  A topic is a lens over transactions, so sharing the lens shares what it
  frames. This is the widest of the five; the UI should be loudest here.

## Reading shared things

**Default behaviour does not change.** Every existing list endpoint returns
exactly what it returns today, so nothing already built breaks.

- List endpoints (`/accounts`, `/goals`, `/budgets`, `/bills`, `/topics`) take
  an optional **`scope=OWN|SHARED|ALL`**, defaulting to **`OWN`**.
- Every response in those lists carries **`access: OWNER|VIEWER`** and, when
  `VIEWER`, an **`owner: {id, displayName}`**, so a mixed `ALL` list can be
  badged and sorted without a second call.
- Detail and nested read endpoints accept either the owner or a viewer:
  `GET /accounts/{id}`, `/accounts/{id}/transactions`, `/goals`, `/budgets`,
  `/bills`, `/topics/{id}`, `/topics/{id}/transactions`. Internally that means a
  `getReadableBy(id, user)` alongside the existing `getOwnedBy`, and **the two
  must stay visibly distinct at every call site** — a read helper silently
  reused in a write path is exactly how this feature turns into a data breach.
- `GET /topics/{id}/candidates` stays **owner-only**: it suggests the caller's
  own unattached transactions, which is a writing tool, not a view.

### Aggregates never mix

**A shared resource contributes nothing to the viewer's own figures.** The
monthly summary, budget usage, the dashboard totals and every future net-worth
number stay strictly own-only. A viewer looking at my account sees *my* balance
on *that account's* page; their own totals are unchanged. Anything else would
make two people's dashboards disagree about the same household, and each would
be right.

This is why `scope` defaults to `OWN` rather than `ALL`: the safe default is
the one where nothing sums across owners by accident.

## Family members list

Sharing needs somebody to share with, and there is currently **no way to list
users** — `POST /api/v1/users` is all that exists.

- `GET /api/v1/users` returns every non-deleted user: `id`, `displayName`,
  `email`, `role`. Available to **any authenticated user**, not just the
  `OWNER`. **Assumption (flag if wrong):** in a household of a few people who
  already know each other's names, hiding the member list protects nothing and
  makes sharing impossible. It is a widening of today's behaviour, where users
  are invisible to each other, so it is called out rather than slipped in.
- Deleted users stay out of the list but keep their shares; revoke on delete is
  a separate decision, and the simplest correct answer is that a soft-deleted
  user cannot authenticate, so their shares are inert.

## API

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/users` | the family members list, for the share picker |
| GET | `/api/v1/shares?resourceType=&resourceId=` | who this resource is shared with — **owner only** |
| POST | `/api/v1/shares` | grant: `{resourceType, resourceId, granteeUserId}` |
| DELETE | `/api/v1/shares/{id}` | revoke (soft delete) |
| GET | `/api/v1/shares/incoming` | everything shared **with me**, across all five types, for a "Shared with me" screen |
| GET | `/api/v1/shares/outgoing` | everything I have shared, so revoking does not require visiting five pages |

Errors: sharing something you do not own is **404** (never 403 — a foreign id
must not be distinguishable from a missing one); sharing with yourself is
**400**; sharing the same thing twice with the same person is **409**;
sharing an unknown `granteeUserId` is **404**.

## Migration

`V8__shares.sql`:
- `shares` table as above
- partial unique index on `(resource_type, resource_id, grantee_id) WHERE is_deleted = false`
- index on `(grantee_id, resource_type)` and on `(owner_id)`

No changes to existing tables: sharing is additive, and nothing about a
resource changes when it is shared.

## Testing

This is the first feature where a bug is a **disclosure**, not a wrong number,
so the tests matter more than the count:

- Every read path, with a viewer: allowed for exactly the shared resource.
- Every **write** path, with a viewer: rejected, for all five types.
- A viewer of one account cannot read a second account of the same owner.
- A revoked share reads as 404 immediately afterwards.
- A viewer's own summary, budgets and dashboard totals are **unchanged** by
  anything shared with them.
- `scope=OWN` returns exactly what the endpoint returned before Phase 8.

The requirements have so far called unit tests sufficient. **This phase is the
argument for integration tests** against a real Postgres: access control that
is only asserted with mocked repositories is asserted against a fiction.

## Frontend

Covered properly in the frontend requirements; the shape it needs:

- A **share control in each detail view** — account, goal, budget, bill, topic
  — listing who it is shared with, a picker of family members to add, and a
  revoke. This is the "controlled in the details of each thing" the feature was
  asked for.
- **A plain statement of what a share exposes**, per type, at the moment of
  sharing. For a topic or an account that includes the transactions.
- A **"Shared with me"** view, from `/shares/incoming`, plus badges wherever a
  shared item appears in a mixed list.
- **Read-only rendering that is obviously read-only**: no edit, delete, attach
  or reconcile affordances on something whose `access` is `VIEWER`. The server
  rejects those anyway; the UI should not offer them.
- A **"Shared by me"** list from `/shares/outgoing`, so revoking is one screen.
- Russian labels: «Участники семьи» for the member list, «Поделиться» for the
  action, «Доступно мне» for incoming.
