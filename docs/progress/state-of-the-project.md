# State of the project

As of **2026-09-20**. Backend `main` at `29e9ece`; the frontend was at
`dd8320d` when this was written and moves on its own.

## Backend — every in-scope phase is built

| Phase | Scope | State |
|---|---|---|
| 0/1 | Users, accounts, banks, categories, transaction ledger, reconcile, monthly summary | Built |
| 2 | Budgets (month-versioned limits) and goals | Built |
| 3 | Loans & mortgages | **Dropped** — tracked as ordinary expense categories |
| 4 | Bills and the due-date calendar | Built |
| 5 | Investment portfolio | Spec'd, **out of scope** |
| 6 | Net worth and reporting | Spec'd, **out of scope** |
| 7 | Topics — a trip's or renovation's transactions as one view | Built |
| 8 | Sharing a single account/goal/budget/bill/topic with another member, read-only | Built, and built in the frontend too |

The original Phase 7 (*Automation & Family Access*) was dropped and its number
reused by topics. Phases 5 and 6 keep theirs.

**Migrations run to `V8`.** V1 initial schema, V2 budgets/goals, V3 transaction
exchange rate, V4 budget versions, V5 goal archived + password rotation,
V6 bills, V7 topics, V8 shares.

**Tests: 194, all passing**, unit-only with MockK. Integration tests are
deliberately deferred by the requirements until there is a frontend and a
Telegram bot to test against — which is now half true, so this is worth
revisiting rather than treating as settled.

### Endpoints added most recently

- The `/api/v1/shares` family (Phase 8): `GET` for who one resource is shared
  with (owner only), `POST` to grant, `DELETE` to revoke, plus `/incoming` and
  `/outgoing` across all five types. `GET /api/v1/users` now lists the
  household for the share picker, readable by any member.
- `scope=OWN|SHARED|ALL` on `/accounts`, `/goals`, `/budgets`, `/bills` and
  `/topics`, defaulting to `OWN` so no existing caller changed. Responses in
  those lists carry `access` and, when `VIEWER`, `owner: {id, displayName}`.
- `GET /api/v1/transactions` — the cross-account ledger list. `from`/`to`
  required, one-year cap, optional `accountId`, `categoryId`, `topicId`.
- `GET /api/v1/users/me` — so a client holding a stored token can re-establish
  identity after a reload.
- `PATCH /api/v1/transactions/{id}` now accepts `toAmount`.
- The whole `/api/v1/topics` family (Phase 7).

## Frontend — feature-complete for the current scope

Every phase in its own progress log reads Done: foundation, ledger, budgets
and goals, bills, topics, settings. Pages: login, dashboard, accounts, account
detail, transactions, budgets, goals, bills, topics, topic detail, categories,
users, password.

- Russian UI throughout. Topics are labelled **«Событие»**, not «Тема», which
  reads like a forum thread.
- Money renders at exactly two decimals everywhere (`45 000,00`), while
  `exchangeRate` keeps its own scale-6 precision — rounding a rate of
  `0.004821` to two decimals would show a wrong number rather than a rounded
  one.
- **Dark mode is out of scope** by the user's decision. Light theme only;
  shadcn's dark block is inert because nothing sets the `.dark` class.

## What is actually left

**Phase 8 (sharing) is built on both sides** — see
`../../.claude/requirements/phase-8-sharing.md`, and read it before touching
any access path: this is the feature where a bug is a disclosure rather than a
wrong number, and it is the first crack in the single-owner rule every
`getOwnedBy` depended on.

What that means in the code, so the next session does not have to rediscover it:

- **`getOwnedBy` and `getReadableBy` are deliberately separate on every
  service, and must stay that way.** The first is the only one any write path
  may call; the second also admits viewers. Reusing the read helper in a write
  path is precisely how this turns into a data breach, so neither is a default
  for the other.
- `Readable<T>` is a sealed `Own`/`Shared` pair, and the access badge and the
  owner on a response both come from it — a response cannot claim to be shared
  without saying whose it is.
- `ShareAccessService` (the read side) is what the five resource services
  depend on; `ShareService` (grant/revoke) depends on *them*, so the
  dependency runs one way.
- **A shared budget's usage is its owner's spending**, computed per owner.
  Getting that wrong would answer the wrong question silently rather than fail,
  which is why it has its own test.
- Its three flagged assumptions are now decided in the doc: goal sharing
  discloses the linked account's balance (binary, no percentage-only middle
  ground), `GET /api/v1/users` is open to any member, and deleting a member
  must leave no resolvable path from a live row to their `User`.

Otherwise nothing is half-built. These are open by choice:

1. **Phases 5 and 6** (investments, net worth) — spec'd, out of scope. Revisit
   only once this is in daily use. Phase 5 carries one open assumption: whether
   a trade should move the broker account's cash balance (spec says no).
2. **Phase 7's open assumption** — one topic per transaction, built that way.
   A join table would let one expense sit in two topics, at the cost of every
   cross-topic total double-counting it.
3. **Integration tests** — see above, and now more pointedly: Phase 8's access
   control was driven by hand against a real Postgres once but has no automated
   Testcontainers pass. That is the first thing to add if sharing is touched.
4. **Two frontend paths never exercised from the UI**, recorded in the
   frontend's `docs/progress/settings.md`: deleting a category blocked by a
   budget or by children (409 both times), and changing a password (doing so
   invalidates the seeded credentials everything else is tested with — the
   `member@example.com` account is the safe way to try it).
5. **Only Phase 8's frontend has actually been seen in a browser.** On
   2026-09-20 the frontend session drove its Phase 8 screens in Chrome as both
   users, at desktop and phone width, and fixed three rendering defects found
   that way. Everything earlier is still "API shapes confirmed, rendering
   traced" rather than watched, so anything visual outside Phase 8 remains
   unverified — and three defects surviving into a browser on the one screen
   set that got there is the argument for not trusting the rest.
6. **Deployment to a real server has not been done yet** — see
   `../running-the-service.md`, written for that purpose but not yet followed
   end to end on a real host.
