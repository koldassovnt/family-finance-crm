# State of the project

As of **2026-09-19**. Backend `main` at `ca35706`; the frontend was at
`c97dd64` when this was written and moves on its own.

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
| 8 | Sharing a single account/goal/budget/bill/topic with another member, read-only | **Spec'd, next** |

The original Phase 7 (*Automation & Family Access*) was dropped and its number
reused by topics. Phases 5 and 6 keep theirs.

**Migrations run to `V7`.** V1 initial schema, V2 budgets/goals, V3 transaction
exchange rate, V4 budget versions, V5 goal archived + password rotation,
V6 bills, V7 topics.

**Tests: 144, all passing**, unit-only with MockK. Integration tests are
deliberately deferred by the requirements until there is a frontend and a
Telegram bot to test against — which is now half true, so this is worth
revisiting rather than treating as settled.

### Endpoints added most recently

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

**Phase 8 (sharing) is spec'd and is the next thing to build** — see
`../../.claude/requirements/phase-8-sharing.md`. It is the first feature where
a bug is a disclosure rather than a wrong number: it breaks the single-owner
rule that every `getOwnedBy` check depends on, so read that doc before touching
any access path. It carries three flagged assumptions, all about how much a
share exposes.

Otherwise nothing is half-built. These are open by choice:

1. **Phases 5 and 6** (investments, net worth) — spec'd, out of scope. Revisit
   only once this is in daily use. Phase 5 carries one open assumption: whether
   a trade should move the broker account's cash balance (spec says no).
2. **Phase 7's open assumption** — one topic per transaction, built that way.
   A join table would let one expense sit in two topics, at the cost of every
   cross-topic total double-counting it.
3. **Integration tests** — see above.
4. **Two frontend paths never exercised from the UI**, recorded in the
   frontend's `docs/progress/settings.md`: deleting a category blocked by a
   budget or by children (409 both times), and changing a password (doing so
   invalidates the seeded credentials everything else is tested with — the
   `member@example.com` account is the safe way to try it).
5. **The frontend has never been seen in a browser by either Claude session.**
   Its logs are careful to say "API shapes confirmed, rendering traced", not
   "watched it work". Anything visual is unverified.
6. **Deployment to a real server has not been done yet** — see
   `../running-the-service.md`, written for that purpose but not yet followed
   end to end on a real host.
