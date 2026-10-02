# Family Finance CRM

Kotlin + Spring Boot + PostgreSQL backend for a household ledger: accounts,
categories and transactions, plus budgets, goals and bills.

## Running it

```bash
# .env (gitignored) must set JWT_SECRET and DB_PASSWORD first —
# see docs/running-the-service.md
docker compose up -d                    # Postgres on 127.0.0.1:6432
export DB_PASSWORD='same-value-as-in-.env'
export JWT_SECRET='at-least-32-bytes-of-random-secret'
./gradlew bootRun                       # Flyway applies the schema on startup
```

The single `OWNER` is created on the first start that finds none, from
`OWNER_EMAIL`, `OWNER_DISPLAY_NAME` and `OWNER_PASSWORD` — there is
deliberately no endpoint for it. Once it exists the three are ignored: change
the password through the API and delete `OWNER_PASSWORD` from `.env`.

`POST /api/v1/auth/login` returns the bearer token every other endpoint wants.
The browsable API contract lives at `/swagger-ui.html`.

`./gradlew build` runs ktlint and the unit tests; both gate the build.

## Running it in Docker

The jar is built on the host and the `Dockerfile` only packages it, so the
host needs a JDK 21 as well as Docker. The `app` profile runs it next to
Postgres:

```bash
cat > .env <<EOF
JWT_SECRET=$(openssl rand -hex 32)
DB_PASSWORD=$(openssl rand -hex 16)
EOF
./gradlew bootJar                       # always first: the image copies build/libs/app.jar
docker compose --profile app up -d --build
```

Keep `JWT_SECRET` stable (rotating it logs everyone out), and set
`DB_PASSWORD` before the first start: Postgres reads it only when the volume
is created. Flyway migrates on startup; bootstrap the `OWNER` as above.

`POSTGRES_PORT` and `APP_PORT` override the published host ports
(`127.0.0.1:6432` and `8080`).

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
| `.claude/requirements/phase-8-sharing.md`                 | built |

**Phases 0/1, 2, 4, 7 and 8 are built.** Phase 7 groups a trip's or a
renovation's transactions into one view. Phase 8 shares one account, goal,
budget, bill or topic at a time with another household member, read-only: the
five list endpoints take `scope=OWN|SHARED|ALL` (defaulting to `OWN`, so
nothing already built changed), every write path stays owner-only, and no
shared resource ever contributes to the viewer's own totals.

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
