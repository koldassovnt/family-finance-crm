# Family Finance CRM

Kotlin + Spring Boot + PostgreSQL backend for a household ledger: accounts,
categories and transactions, plus budgets, goals and bills.

## Running it

```bash
docker compose up -d                    # local Postgres on 5432
export JWT_SECRET='at-least-32-bytes-of-random-secret'
./gradlew bootRun                       # Flyway applies the schema on startup
```

Then create the single `OWNER` once, by hand — there is deliberately no
endpoint for it:

```bash
./gradlew printPasswordHash -Ppassword='your-password'
# paste the hash into db/bootstrap-owner.sql, then:
psql postgresql://family_finance:family_finance@localhost:5432/family_finance \
  -f db/bootstrap-owner.sql
```

`POST /api/v1/auth/login` returns the bearer token every other endpoint wants.
The browsable API contract lives at `/swagger-ui.html`.

`./gradlew build` runs ktlint and the unit tests; both gate the build.

## Running it in Docker

The `Dockerfile` builds the jar inside the image, so the host needs only
Docker. The `app` profile runs it next to Postgres:

```bash
export JWT_SECRET="$(openssl rand -hex 32)"   # keep it: rotating it logs everyone out
docker compose --profile app up -d --build
```

Flyway migrates on startup; bootstrap the `OWNER` as above, running `psql`
inside the database container:

```bash
docker exec -i family-finance-postgres \
  psql -U family_finance -d family_finance < db/bootstrap-owner.sql
```

`POSTGRES_PORT` and `APP_PORT` override the published host ports (5432 and
8080) when those are taken.

## Docs

- [`docs/running-the-service.md`](docs/running-the-service.md) — running it
  locally and deploying it to a real server (TLS, backups, upgrades).
- [`docs/progress/`](docs/progress/) — what is built, the development
  environment and seeded data, and the decisions behind recent choices.
  Start here when picking the project up again.

## Requirements

Split into one doc per phase so each stays focused. Read `00-` first — every
phase doc assumes it. The frontend has its own matching set, in its own repo:
`family-finance-crm-front/.claude/requirements/`.

| Doc                                                       | Status         |
|-----------------------------------------------------------|----------------|
| `.claude/requirements/00-architecture-and-foundations.md` | shared, always current |
| `.claude/requirements/phase-0-1-foundation-ledger.md`     | built |
| `.claude/requirements/phase-2-budgets-goals.md`           | built |
| `.claude/requirements/phase-4-bills-calendar.md`          | built |
| `.claude/requirements/phase-7-topics.md`                  | built |
| `.claude/requirements/phase-8-sharing.md`                 | spec'd, not built |

**Phases 0/1, 2, 4 and 7 are built.** Phase 7 groups a trip's or a
renovation's transactions into one view. Phase 8 (sharing individual accounts,
goals, budgets, bills and topics with other household members as read-only
viewers) is spec'd and is the next thing to build.

Phase 3 (Loans & Mortgages) was **dropped** — loans and mortgages are tracked
as ordinary expense categories, so there was no entity left to spec. The
original Phase 7 (Automation & Family Access) was dropped too, and its number
is reused by the topics doc above since nothing referenced it. Phases 5 and 6
keep their numbers.

`.claude/requirements/phase-5-investments.md` and `phase-6-net-worth.md` hold
specs for investments and net worth. They're written
up but **out of scope** — revisit once
Phases 0–4 are actually running and it's clear what's genuinely wanted. Treat
them as a starting point to re-review, not settled decisions.

Every in-scope phase is spec'd, and every open question has been decided —
there are no unresolved assumptions left in Phases 0–4. (Assumptions remain in
the not-yet-built docs, flagged inline: one in the out-of-scope Phase 5/6 pair,
one in Phase 7 — whether a transaction may belong to more than one topic,
built as one-per-transaction; and three in Phase 8, all about how much a share
exposes.)

**Decisions worth knowing before reading anything else:** JWT with a single
30-day token; soft delete everywhere (with two deliberate `@SQLRestriction`
exceptions, `Category` and `Topic`, so historical transactions keep resolving
them); `Asia/Almaty` fixed as the app timezone; all
endpoints under `/api/v1/`; balances corrected via an `ADJUSTMENT`
transaction rather than a direct edit; no pagination, date-range bounded
instead.

**Phases 0/1, 2, 4 and 7 are all built; Phase 8 is spec'd and next.** Phases 5
and 6 remain out of scope; revisit them only once this is genuinely in daily
use.
