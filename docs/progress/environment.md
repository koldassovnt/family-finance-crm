# Environment and test data

The local development setup as it stands on this machine, and what is in the
database. Seeded data last changed **2026-09-20** (Phase 8 shares, member
accounts, one overdue bill). For how to run the thing from scratch, see
`../running-the-service.md`.

**These are development credentials on a personal machine.** Nothing here is a
production secret, and none of it should survive a move to a real server.

## The running stack

`docker compose --profile app up -d` from the repo root brings up:

| Container | Port | Notes |
|---|---|---|
| `family-finance-app` | 8080 | Health at `/actuator/health`, docs at `/swagger-ui.html` |
| `family-finance-postgres` | 55432 → 5432 | **Not 5432 on the host** — another project holds it |

`.env` (gitignored) carries `JWT_SECRET`, `POSTGRES_PORT=55432` and
`APP_PORT=8080`. The secret must stay stable: rotating it invalidates every
issued token.

Database credentials are the compose defaults —
`family_finance` / `family_finance` / `family_finance`:

```bash
psql postgresql://family_finance:family_finance@localhost:55432/family_finance
docker exec -it family-finance-postgres psql -U family_finance -d family_finance
```

The frontend dev server runs on 5173 with
`VITE_API_BASE_URL=http://localhost:8080`.

Note for anything schema-related: soft delete is universal, so most tables need
`WHERE is_deleted = false` to match what the API returns.

## Logins

| Email | Password | Role |
|---|---|---|
| `owner@example.com` | `family123` | `OWNER` |
| `member@example.com` | `member12345` | `MEMBER` |

The `MEMBER` exists to exercise the owner-only guard: `POST /api/v1/users`
returns 403 `FORBIDDEN` for it. **It used to own nothing**, and older notes
describe logging in as that user as a tour of every empty state. That changed
on 2026-09-20: it now owns three accounts, so Phase 8's mixed `scope=ALL` list
has both an owned and a shared row. It still owns no transactions, categories,
budgets, goals, bills or topics, so every other screen is still an empty
state — and its monthly summary is still zero, because an opening balance is
not a transaction.

## Seeded data

Built up to cover every screen, including the states that are easy to miss.
Figures move whenever anyone uses the app, so treat these as "what it looked
like on 2026-09-19", not as fixtures.

**Accounts** — «Наличные» (CASH), «Каспи Голд» (BANK, with a bank), «Долларовый»
(BANK, **USD**, exercises the `exchangeRate` path), «Депозит» (DEPOSIT),
«Отбасы» (added through the UI), plus one **soft-deleted** account,
«Старая карта Jusan», which still has two transactions pointing at it — the
fallback-label case, since deleted accounts are absent from `GET /accounts`.

**Categories** — three levels deep and branching at the bottom
(Продукты → Кофе → Зёрна and Оборудование), which is what distinguishes real
recursion from a two-level special case. Plus «Жильё», «Возвраты», «Транспорт»,
«Коммуналка», «Развлечения», «Зарплата», «Подарки». One category was
soft-deleted during testing and its transaction still renders its name — the
deliberate `@SQLRestriction` exception.

**Transactions** — across August and September 2026, including a same-currency
transfer, a cross-currency transfer with `toAmount`, USD expenses at several
rates, and one uncategorised expense so `category: null` renders.

**Budgets** — all three bar states at once: «Транспорт» ~35% (normal),
«Коммуналка» ~86% past its 80% threshold (amber), «Продукты» ~112% over the
limit (red, and its usage includes the child category «Кофе», so the rollup is
visible). August returns `[]` — the budgets take effect from September, which
is the "no budget that month" empty state rather than "no budgets configured".

**Goals** — one at ~77% `ACTIVE`, one **achieved at 100% but still `ACTIVE`**
(there is no `ACHIEVED` status), one `ARCHIVED`, one `ABANDONED`.

**Bills** — «Электричество», 18 400 KZT due 2026-09-12 and unpaid, is the
**overdue** row (the case a month calendar cannot show, which is why the
`unpaid=true` filter exists). Plus paid ones and a four-month batch sharing a
`batchId` with day 31 clamped to 09-30 and 11-30. An earlier August bill was
the overdue example until somebody marked it paid; «Электричество» was added on
2026-09-20 to restore the case, which is exactly the kind of drift the
reseeding note below warns about.

**Topics** — four, covering the interesting cases:

| Topic | State | Why it exists |
|---|---|---|
| «Турция 2026» | 12 rows, 8 USD at four different rates + 4 KZT | Two currencies on one screen; a refund so `received` and `net` differ; `firstTransactionOn` 2026-08-02 **precedes** `startDate` 2026-08-15 (flights booked early), proving the declared window does not bound the spending |
| «Малайзия 2026» | 5 rows incl. a USD one | Ordinary case; one row was added through the UI by hand |
| «Подготовка к школе» | 1 row, planned 100 000 | **Negative `remaining`** — the over-plan styling case |
| «Ремонт кухни» | `CLOSED`, nothing attached | Empty state and the status filter in one |

**Member accounts** — «Карта Kaspi» (BANK, Kaspi Bank, 184 300), «Наличные»
(CASH, 32 000) and «Накопления» (DEPOSIT, Halyk, 450 000), all owned by
`member@example.com` and created through the API as that user. They exist so a
viewer's `scope=ALL` is a genuinely mixed list rather than shared rows only.

**Shares** (Phase 8) — seven grants, all from `owner@example.com` to
`member@example.com`, each chosen to exercise one rendering case:

| Shared | Case it covers |
|---|---|
| ACCOUNT «Депозит» | Its one history row is a TRANSFER whose counterparty «Каспи Голд» is **not** shared — the unresolvable-counterparty case, which must read «Другой счёт» and never «Удалённый счёт» |
| GOAL «Подушка безопасности» | 100% achieved, and it discloses linked account «Каспи Голд» with its balance **while `GET /accounts/{that id}` 404s** for the viewer — the deliberate disclosure |
| BUDGET «Продукты» | 112% — over limit |
| BUDGET «Коммуналка» | 85.5% against an 80 threshold — amber. Two budgets on purpose, so a shared list is seen not to collapse to one row per type |
| BILL «Электричество» | Unpaid and past due — an **overdue** shared bill |
| BILL «Кредит на авто» | Unpaid, future due date |
| TOPIC «Турция 2026» | 12 transactions with both `expenseByCategory` and `incomeByCategory` populated; its rows sit on accounts never shared — the widest of the five |

Nothing the member owns is shared with anyone: sharing is one-directional per
grant, and there is no grant in that direction to exercise. In a mixed
`scope=ALL` list the owned rows come first, then the shared ones, each group
sorted by name — so the list is not globally sorted, which a client wanting one
flat alphabetical list has to handle itself.

## Reseeding

There is no seed script in the repo; the data above was built through the API.
If you need to rebuild it, do it through the API rather than SQL — the service
layer is where the invariants live, and hand-written rows will violate them
quietly.

**Before changing seeded data, check whether the other session has recorded
figures against it.** The frontend's `docs/progress/*.md` files cite exact
numbers as verified. Changing an amount, adding a transaction or adding a
category moves those, and nobody notices until a screen checked days ago looks
wrong. Attaching an existing transaction to a topic is the safe kind of change:
it moves no totals, only a reference.
