# Phase 0 + 1 — Foundation & Ledger

Status: **built.** This doc is the target the code was built to, in one pass —
the earlier "shipped code has drifted" framing no longer applies, since no
Phase 0/1 code actually existed when the build started. See
`00-architecture-and-foundations.md` for the
`User`/`Bank`/`Account`/`Category`/`Transaction` entities this API is built on.

## User Provisioning & Auth

- **Bootstrapping the first `OWNER`:** no endpoint for this — there's no one
  to authorize it yet. Insert the first `User` (role `OWNER`) directly via
  SQL/migration at deploy time, same as any other one-time setup on a
  personal deployment.
- **Every subsequent user is created by an `OWNER`**, not self-service
  signup. `POST /api/v1/users` (`OWNER`-only) creates a `MEMBER`.
  **Confirmed: this endpoint cannot create another `OWNER`** — reject
  `role = OWNER` with a 400. There is exactly one `OWNER`, created by the
  bootstrap step, so admin accounts can't quietly proliferate.
  The `OWNER` sets the new user's initial email/displayName/password
  directly. `POST /api/v1/users/me/password` (any role, own account only)
  changes it afterwards, and moves `passwordChangedAt` forward — which is also
  the system's only token revocation, so it logs every other session out.
- `POST /api/v1/auth/login` — email + password → JWT. Token expiry/refresh
  strategy isn't pinned down yet (see `00-`); this endpoint exists, the
  details of what it returns firm up when it's actually built.

## API Surface (current target — supersedes what's actually running)

| Method | Path                                | Purpose                                                              |
|--------|--------------------------------------|------------------------------------------------------------------------|
| POST   | `/api/v1/auth/login`                    | log in, receive a JWT                                                |
| POST   | `/api/v1/users`                         | `OWNER`-only: create a `MEMBER` account                              |
| POST   | `/api/v1/users/me/password`             | change your own password; invalidates every token issued before now  |
| GET    | `/api/v1/accounts`                      | list your accounts                                                  |
| POST   | `/api/v1/accounts`                      | create an account                                                   |
| GET    | `/api/v1/accounts/{id}`                 | get one account                                                     |
| PATCH  | `/api/v1/accounts/{id}`                 | update name/bank — **not** balance                                  |
| POST   | `/api/v1/accounts/{id}/reconcile`       | correct a drifted balance; creates an `ADJUSTMENT` transaction. `exchangeRate` required for a non-KZT account, same rule as `POST /transactions` |
| DELETE | `/api/v1/accounts/{id}`                 | soft delete; blocked if an active `Goal` still points to it         |
| GET    | `/api/v1/categories`                    | list your categories                                                |
| POST   | `/api/v1/categories`                    | create a category                                                   |
| PATCH  | `/api/v1/categories/{id}`               | update name/parent                                                  |
| DELETE | `/api/v1/categories/{id}`               | soft delete; blocked only if an active `Budget` references it — historical transactions do **not** block it |
| GET    | `/api/v1/banks`                         | list banks                                                          |
| POST   | `/api/v1/banks`                         | find-or-create by name                                              |
| POST   | `/api/v1/transactions`                  | record a transaction; `exchangeRate` required for a non-KZT account |
| GET    | `/api/v1/accounts/{id}/transactions`    | account history; `from`/`to` **required**, range capped at 1 year — no paging (see below) |
| GET    | `/api/v1/transactions/summary`          | monthly summary (`month=2026-09`)                                   |
| PATCH  | `/api/v1/transactions/{id}`             | edit amount/date/category/note only — **not** type/account/toAccount (delete + recreate for those); re-applies the balance delta if amount changes |
| DELETE | `/api/v1/transactions/{id}`             | soft delete; **reverses its balance effect** — a hidden transaction can't leave a balance that assumes it still happened |

**Validation rules decided while building, not in the original spec:**
- **`POST /api/v1/transactions` rejects `type = ADJUSTMENT`** with a 400 pointing
  at `/reconcile`. Otherwise the "balance corrections always go through an
  `ADJUSTMENT`, never a direct edit" rule has an open back door.
- **A transaction's category must match its type's kind** — an `EXPENSE`
  transaction needs an `EXPENSE` category, `INCOME` needs `INCOME`. An
  `INCOME` category on an expense is a data error, not a preference.
- **`TRANSFER` and `ADJUSTMENT` carry no category at all**, and one is rejected
  if supplied.
- **Soft-deleting a `Category` with live sub-categories is blocked** (409), so
  the tree can't be left with dangling parents. Re-parent or delete the
  children first. (Historical transactions still never block it — see `00-`.)

**No pagination anywhere.** List endpoints are bounded by date range
instead: `from`/`to` are required on transaction history and the range is
capped (suggest 1 year) so a single response can't grow unbounded. Accounts,
categories, and banks are small enough to return whole. If transaction volume
ever makes even a year's range unwieldy, add `page`/`size` then — the
date-range contract doesn't block it.

## Implementation Plan — historical

⚠ **Kept for the reasoning, not as instructions.** This was written when the
code was believed to already exist and need retrofitting; in fact nothing had
been built, so it was built once straight to the target above. Two steps below
are now actively wrong:

- **Step 1's blanket `@SQLRestriction`** — `Category`, `Account` and `Budget`
  must *not* have it. Following step 1 literally reintroduces the bug where a
  soft-deleted parent resolves to `null` on every row that references it. See
  `00-` for the rule that replaced it.
- **The "retrofit to interface + Impl" notes** in steps 6 and 7 never applied —
  every service was written as interface + `Impl` from the start.

The original sequencing follows.

1. **Soft-delete + auditing infrastructure, first.** Add `isDeleted: Boolean`
   (default `false`) and `updatedAt` to `User`, `Account`, `Category`,
   `Transaction`; enable JPA auditing (`@EnableJpaAuditing`). Add
   `@SQLRestriction("is_deleted = false")` to each entity so exclusion is
   automatic, not repeated per-query. Convert `User.email`'s unique
   constraint to a partial index (`WHERE is_deleted = false`). Every later
   step assumes this exists.

2. **`Bank` entity.** New entity + repository + service (interface +
   `BankServiceImpl`) + controller: `GET /api/v1/banks`, `POST /api/v1/banks`
   (find-or-create by name — case-insensitive match recommended). Unique
   partial index on `name`. No dependency on the other new work, so this can
   also be done in parallel with step 3 if that's easier to split up.

3. **`Category` ownership + full CRUD.** Add `owner: User` FK to `Category`;
   drop the static Flyway seed entirely. Build `CategoryController`/
   `CategoryService` (interface + Impl) with `GET/POST/PATCH/DELETE
   /api/v1/categories`, scoped to the caller's own categories. `DELETE` is soft
   delete, blocked if a non-deleted `Budget` still references the category —
   **`Budget` doesn't exist yet (Phase 2)**, so leave a
   `// TODO: block if referenced by an active Budget` rather than
   implementing a check against a table that isn't there yet. Transactions
   referencing the category do **not** block it. **Do not add
   `@SQLRestriction` to `Category`** — see the warning in `00-`; filter
   deleted rows in the list query instead.

4. **`AccountType.DEPOSIT` + `Account.bank`.** Add the enum value. Add
   nullable `bank: Bank` FK to `Account`. Update `CreateAccountRequest`/
   `AccountResponse` DTOs accordingly.

5. **Drop `Transaction.isRecurring`/`recurrenceInterval`.** Remove the
   fields, the `RecurrenceInterval` enum, and the corresponding migration
   columns (a follow-up migration dropping them, since the original one may
   already have run locally).

6. **`Account` `PATCH`/`DELETE`.** Add both to `AccountController`/
   `AccountService`. `PATCH` updates name/bank only. `DELETE` is soft
   delete — same `Goal`-doesn't-exist-yet caveat as step 3, leave a `TODO`
   rather than a real check. **Retrofit `AccountService` to interface +
   `AccountServiceImpl` while already in this file** — no separate pass needed.

7. **`Transaction` `PATCH`/`DELETE`, plus cross-currency transfer support.**
   Add the nullable `toAmount` column and its validation rules (see `00-`);
   `TransactionService.create` credits the destination with `toAmount ?:
   amount`. Also add `TransactionType.ADJUSTMENT` plus
   `POST /api/v1/accounts/{id}/reconcile`, and **exclude `ADJUSTMENT` from the
   monthly-summary aggregation**. `PATCH` allows amount/date/category/
   note only; if amount changes, reverse the old amount's balance effect and
   apply the new one (type/account can't change via `PATCH`, so this only
   ever touches the same account(s) the transaction already has). `DELETE`
   is soft delete and **must** reverse the transaction's balance effect — a
   hidden transaction can't leave a balance that assumes it still happened.
   This is the trickiest step; the math mirrors `TransactionService.create`,
   inverted. **Retrofit `TransactionService` to interface + `ServiceImpl`
   here too.**

8. **JWT auth + user provisioning.** Replace the HTTP Basic `SecurityConfig`
   with JWT: `POST /api/v1/auth/login` (email+password → 30-day token) plus a
   filter validating the bearer token on every other request. Secret from an
   env var; no refresh token, no server-side token store. Add `POST /api/v1/users`
   (`OWNER`-only via `hasRole("OWNER")`) creating a `MEMBER`. The
   first `OWNER` still isn't created through any endpoint — insert it
   manually via SQL once, same as always.

9. **Swagger/OpenAPI, last.** Add `springdoc-openapi`, annotate every
   controller from every step above. Doing this last avoids re-annotating
   endpoints whose shape was still moving through steps 1–8.

