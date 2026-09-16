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

## Requirements

Split into one doc per phase so each stays focused. Read `00-` first — every
phase doc assumes it. Companion doc outside this folder: `frontend-requirements.md`.

| Doc                                                       | Status         |
|-----------------------------------------------------------|----------------|
| `.claude/requirements/00-architecture-and-foundations.md` | shared, always current |
| `.claude/requirements/phase-0-1-foundation-ledger.md`     | built |
| `.claude/requirements/phase-2-budgets-goals.md`           | built |
| `.claude/requirements/phase-4-bills-calendar.md`          | built |

**Current scope ends at Phase 4.** That's the whole build for now:
foundation, ledger, budgets, goals, and bills.

Phase 3 (Loans & Mortgages) and Phase 7 (Automation & Family Access) were both **dropped** — loans and mortgages are tracked
as ordinary expense categories, so there was no entity left to spec. Later
phase numbers are unchanged to avoid churning cross-references.

`.claude/requirements/phase-5-investments.md` and `phase-6-net-worth.md` hold
specs for investments and net worth. They're written
up but **out of scope** — revisit once
Phases 0–4 are actually running and it's clear what's genuinely wanted. Treat
them as a starting point to re-review, not settled decisions.

Every in-scope phase is spec'd, and every open question has been decided —
there are no unresolved assumptions left in Phases 0–4. (One remains in the
out-of-scope docs, flagged inline, for whenever that phase comes back.)

**Decisions worth knowing before reading anything else:** JWT with a single
30-day token; soft delete everywhere (with one deliberate `@SQLRestriction`
exception on `Category`); `Asia/Almaty` fixed as the app timezone; all
endpoints under `/api/v1/`; balances corrected via an `ADJUSTMENT`
transaction rather than a direct edit; no pagination, date-range bounded
instead.

**Phases 0/1, 2 and 4 are all built — the whole current scope is done.**
Phases 5 and 6 remain out of scope; revisit them only once this is genuinely
in daily use.
