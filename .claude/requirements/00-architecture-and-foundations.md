# 00 — Architecture & Foundations

Shared across every phase. Read this first; phase docs reference it rather
than repeating it.

## Tech Stack

- Kotlin
- Spring Boot 3.x (Web, Data JPA, Validation, Security, Actuator)
- PostgreSQL, schema managed via Flyway migrations (not Hibernate `ddl-auto`)
- springdoc-openapi — Swagger/OpenAPI docs generated from controller annotations
- Gradle (Kotlin DSL)

## Architecture

- Modular monolith — not microservices. The operational overhead of
  microservices isn't worth it at this scale.
- **Three explicit layers, each only calling the one directly below it:**
  - **Controller layer** (`web`) — REST endpoints only: request/response
    mapping and validation, no business logic.
  - **Service layer** (`service`) — all business logic (validation rules,
    balance updates, aggregation, etc.). Controllers call services; services
    never call other controllers.
  - **Repository layer** (`repository`) — Spring Data JPA; the only layer
    allowed to touch the database directly.
- **Every service is an interface + implementation**, e.g. `AccountService`
  (interface) and `AccountServiceImpl` (the `@Service`-annotated class).
  Controllers and other services depend on the interface, not the concrete
  class. *Note: the Phase 0/1 code shipped so far used plain concrete service
  classes — retrofit those to interface/Impl before or while building Phase
  2, so the pattern is consistent from here on (see the Phase 0/1 doc).*
- **URL versioning from the start: every endpoint lives under `/api/v1/`.**
  Cheap now, awkward to retrofit once a frontend and a bot are both calling
  the old paths.
- **Error responses have one shape**, returned by a single
  `@RestControllerAdvice` so no controller hand-rolls its own:
  ```json
  {
    "code": "VALIDATION_FAILED",
    "message": "Amount must be greater than zero",
    "fieldErrors": { "amount": "must be greater than zero" }
  }
  ```
  - `code` is a stable machine-readable enum-like string the frontend can
    branch on (`VALIDATION_FAILED`, `NOT_FOUND`, `FORBIDDEN`,
    `CURRENCY_MISMATCH`, `DUPLICATE_BUDGET`, …). Never localize it.
  - `message` is human-readable and safe to display.
  - `fieldErrors` is a field-name → message map, present only for validation
    failures (omit or leave empty otherwise) — this is what lets the UI put
    errors next to the right input rather than in one banner.
  - HTTP status still carries meaning: 400 validation, 401 unauthenticated,
    403 forbidden, 404 missing, 409 conflict.
- **CORS: all origins allowed** (`allowedOriginPatterns("*")`). Safe *because*
  auth is a bearer token rather than a cookie — a random site can't attach a
  token it has no access to. **`allowCredentials` must stay `false`** (it's
  required to be, alongside a wildcard). If auth ever moves to cookies, this
  has to become a real allowlist.
- **API documentation:** every controller endpoint is documented via
  Swagger/OpenAPI (`springdoc-openapi` — `@Operation`, `@ApiResponse`, etc.),
  so every consumer of the API has a browsable, always-current contract at
  `/swagger-ui.html`.

## Core Data Model

These entities recur across every phase, so their shape is settled here
rather than per-phase. All five belong to Phase 0/1 — `User`/`Account`/
`Category`/`Transaction` are already built, `Bank` is new work (see that
phase's implementation plan). Later phases add their own entities in their
own docs but should not redefine these.

**Every entity has `createdAt`, `updatedAt`, and `isDeleted: Boolean`
(default `false`) — soft delete only, no hard deletes anywhere.**
`updatedAt` is set on every write including the soft delete itself, so
"when did this row last change" is always answerable. Implement with JPA
auditing (`@CreatedDate`/`@LastModifiedDate` + `@EnableJpaAuditing`) rather
than setting them by hand in each service. This is a blanket rule, not just for the
entities below: every entity in every phase doc from here on needs it too.
Implications, so nothing gets missed when this is built:
- Every `DELETE` endpoint becomes an update (`isDeleted = true`), not a row removal.
- Every list/get query must exclude `isDeleted = true` by default — recommend Hibernate's `@SQLRestriction("is_deleted = false")` on each entity so this is automatic rather than repeated per-query.
- **⚠ Exception — do NOT put `@SQLRestriction` on `Category`.** Historical transactions keep pointing at a soft-deleted category (see the validation rules below), and `@SQLRestriction` applies to relationship loading too: `transaction.category` would silently resolve to `null` for any deleted category, corrupting old records in every response and summary. Filter deleted categories in the `/api/v1/categories` list query explicitly instead.
- Any "must be unique" constraint (e.g. `User.email`, `Budget`'s `(owner_id, category_id)`) needs a **partial** unique index (`WHERE is_deleted = false`), not a plain `UNIQUE` constraint — otherwise a soft-deleted row permanently blocks reusing that value.
- The existing "block delete if still referenced" rules (e.g. `Category` referenced by a `Budget`) now mean: block **soft**-deleting the parent while a non-deleted child still references it.

- **User** — `id, email, displayName, passwordHash, role (OWNER/MEMBER), isDeleted, createdAt, updatedAt`. **Exactly one `OWNER` exists**, created by the bootstrap step; the user-creation endpoint can only make `MEMBER`. **`VIEWER` was removed** — it was never enforced anywhere, and an unenforced read-only role is worse than none (it implies a restriction that doesn't exist).
  - **In practice this is a single-user system today.** The per-user ownership structure (`owner: User` on every entity, the user-creation endpoint, `MEMBER`) is kept deliberately anyway: it costs nothing while unused, and retrofitting per-user scoping onto an existing dataset is genuinely painful. `MEMBER` currently behaves identically to `OWNER` except for not being able to create users.
- **Bank** — `id, name, createdAt, updatedAt, isDeleted`. A lookup/reference entity — real-world banks ("Halyk Bank", "Kaspi Bank", etc.), not personal to one family member. **Confirmed shared/global, not per-user** — unlike `Category`, which was deliberately made per-user. Self-service find-or-create: any family member can add a new `Bank` by name when setting up an account if it isn't already in the list, rather than this being admin-provisioned. Constraint: unique on `name` **where `is_deleted = false`** (same partial-index pattern as everywhere else soft delete meets uniqueness).
- **Account** — any place money sits: `id, owner, bank, name, type (CASH/BANK/DEPOSIT/BROKER), balance, currency, isDeleted, createdAt, updatedAt`. **`Account.balance` always means money you *have*** — there is no account type where it means money owed. **`CARD`, `LOAN`, and `MORTGAGE` were removed** — a debit card is just a `BANK` account, credit-card debt isn't tracked, and loans/mortgages aren't modelled as entities at all: they're paid via ordinary `EXPENSE` transactions against a user-created "Loan"/"Mortgage" category. `bank: Bank` FK is **nullable** — a `CASH` account has no bank. **Confirmed single-owner** — joint/shared accounts were considered and explicitly rejected; every account belongs to exactly one `User`. Don't revisit this without a real need. `DEPOSIT` (a fixed-term deposit) behaves like `BANK` for now — a plain balance; add interest-rate/maturity fields later only if term-deposit specifics turn out to matter.
- **Category** — `id, name, owner, parent (self-referencing, for a simple hierarchy), kind (EXPENSE/INCOME), isDeleted, createdAt, updatedAt`. **Categories are per family member**, not a shared household taxonomy — each `Category` gets an `owner: User` FK, same pattern as `Account`. Two family members can each have their own "Groceries" category as separate rows; nothing is deduplicated across users. Both `Account` and `Category` are entirely self-service — each family member creates their own directly in the CRM; nothing is admin-provisioned or auto-seeded.
  - `Budget.category` and `Transaction.category` must belong to the *same owner* as the budget/transaction itself — enforce this ownership check alongside the existing account-ownership checks (`AccountService.getOwnedBy`-style pattern) in every service that touches `Category`.
  - A self-referencing `parent` must also belong to the same owner as its child — enforce in the service layer when creating/updating a category, not just at the DB level.
- **Transaction** — `id, type (INCOME/EXPENSE/TRANSFER/ADJUSTMENT), amount (BigDecimal/NUMERIC(19,4)), currency, toAmount (BigDecimal?, TRANSFER only), occurredOn, account, toAccount (TRANSFER only), category, note, isDeleted, createdAt, updatedAt`. **`toAmount` handles cross-currency transfers** — see the convention below. **Removed: `isRecurring`, `recurrenceInterval`** — not needed. Ownership is implied transitively through `account.owner`, which is safe given accounts are single-owner.

## Design Decisions & Conventions (already made — don't re-litigate these)

- **Ledger, not full double-entry.** One row per movement, typed
  INCOME/EXPENSE/TRANSFER, rather than paired debit/credit postings. Cheaper
  to build and reason about at this scale.
- **Transfers are one row** with both `account` (source) and `toAccount`
  (destination). The account-history query matches **either** side, so a
  transfer shows up for both accounts involved, not just the source — this
  was a bug caught and fixed in Phase 0/1; don't reintroduce an
  account-id-only filter.
- **Money** is always `BigDecimal` / `NUMERIC(19,4)`, never floating point.
- **Currency** is a plain 3-letter code column for now (default `KZT`), no
  conversion logic yet — fine until Phase 5 mixes currencies in one portfolio.
- **IDs** are UUIDs, generated by Hibernate (`GenerationType.UUID`).
- **Auth is JWT**, not HTTP Basic (the Phase 0/1 code still uses Basic and
  needs migrating). **Single long-lived token, 30-day expiry, no refresh
  token** — at 2–5 users on a home network, refresh-token rotation is
  ceremony without benefit; on expiry you simply log in again. Signing secret
  comes from an environment variable, never committed. There's no server-side
  token store, so **logout is client-side only** (drop the token) and a token
  can't be revoked before it expires — acceptable at this scale, but worth
  knowing rather than discovering later.
- **Timezone: `Asia/Almaty`, fixed** — set as a config property
  (`app.timezone`), not per-user and not UTC. Everything date-dependent
  resolves against it: the default `occurredOn` for a new transaction, the
  "current month" for budget usage, and whether a bill is overdue. Rationale:
  finances stay anchored to home even when travelling — a dinner bought
  abroad still belongs in the normal Almaty budget month. Since `occurredOn`
  is a user-supplied `LocalDate`, the timezone only sets defaults and month
  boundaries; a wrong date is always correctable by hand.
- **Balance corrections use an `ADJUSTMENT` transaction, never a direct
  balance edit.** The ledger must always explain the balance; a `PATCH` on
  `Account.balance` would break that and leave no record of the correction.
  - `POST /api/v1/accounts/{id}/reconcile` takes the **actual balance** (what
    the bank says) plus an optional note. The service computes the delta and
    writes an `ADJUSTMENT` transaction for it.
  - `ADJUSTMENT` is the only type where `amount` may be **negative** (the
    balance can drift either way). It carries no `category`.
  - **Excluded from the monthly summary and from budget usage** — a
    correction isn't spending. Every aggregation must filter it out
    explicitly; this is the easiest thing to forget.
- **Cross-currency transfers are allowed**, via a nullable `toAmount` on
  `Transaction`:
  - Same-currency transfer: `toAmount` is null; both sides move by `amount`.
  - Cross-currency: `toAmount` is required. Source loses `amount` (in
    `account.currency`), destination gains `toAmount` (in
    `toAccount.currency`). Example: $100 → 48,000 ₸ is `amount = 100`,
    `toAmount = 48000`.
  - Validate in the service layer: `toAmount` **must** be present when the
    two accounts' currencies differ, and **must** be absent when they match —
    don't silently accept a redundant or missing value.
  - No exchange rate is stored or derived; the implied rate is just
    `toAmount / amount` if ever needed for display.
  - `toAmount` is meaningless for `INCOME`/`EXPENSE` — reject it there.
- **Validation rules** (enforce in the service layer, not just bean validation):
  - `amount` must be **> 0** for `INCOME`/`EXPENSE`/`TRANSFER`. `ADJUSTMENT`
    is the sole exception and may be negative; zero is never valid for any type.
  - **Future `occurredOn` is rejected** — a transaction records something
    that happened, not something planned. "Today" means today in
    `Asia/Almaty`. (Upcoming obligations belong in Phase 4's `Bill`, which is
    exactly what future-dated entries would otherwise be reinventing.)
  - **Balances may go negative.** An expense larger than the tracked balance
    is accepted, not rejected — blocking it would mean one forgotten income
    entry locks out every subsequent expense, and would make backfilling
    history out of order impossible. A negative balance is a signal that
    something's missing; the UI should flag it, and `reconcile` fixes it.
  - String length caps: `name`/`displayName` 255, `note` 1000, `currency`
    exactly 3 characters.
- **Soft-deleted categories stay attached to their history.** Deleting a
  `Category` that transactions reference is **allowed** — those transactions
  keep pointing at it, and it keeps rendering with its name in history and
  summaries. It just disappears from `/api/v1/categories`, so it can't be
  chosen for anything new. (A `Budget` referencing it still blocks the
  delete — see `phase-2-budgets-goals.md`; that's a live config, not history.)
- **Naming:** Spring Boot's default physical naming strategy handles
  camelCase → snake_case automatically; don't add redundant `@Column(name=...)`
  unless the mapping needs to differ from that default.

## Non-Functional Requirements

- **Security:** this is the most sensitive personal data hosted here —
  encrypt at rest; don't expose the API beyond the home network/VPN without
  auth hardening.
- **Backups:** not needed for now — running as a Docker container on a
  personal PC, not a production host. Revisit if that deployment target changes.
- **Testing:** unit tests are enough for now. Broader integration/end-to-end
  testing is deferred until the React frontend and Telegram bot exist to
  test against.
