# To do

Only what is waiting, and on what. The phase docs in `.claude/requirements/`
say what each feature should be; finished work is removed from here.

## KASE prices from the owner's own service

API Ninjas does not cover KASE, so the KZT broker account's holdings show
purchase cost only. Decided 2026-10-13: the owner will write a separate
service that takes each ticker's end-of-day price from the KASE website at
18:00 Almaty time, and this backend will read prices from it.

**Waiting on:** that service existing, and its API (address, how a ticker is
asked for, what it returns).

## Phase 6 — net worth

**Waiting on:** the owner's go-ahead, and KASE prices (above) so every holding
has a value. The spec in `phase-6-net-worth.md` predates how Phase 5 was built
and must be re-reviewed before any of it is implemented.

## Family purchases — the frontend

The backend is built (`phase-9-family-purchases.md`): an expense can be marked
`isFamily`, and `GET /api/v1/family/purchases` shows every member's.

**Waiting on:** the frontend — a «Семейная покупка» checkbox on the expense
form and a «Семейные покупки» page. Until then nobody can mark or see one.
