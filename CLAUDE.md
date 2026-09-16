# CLAUDE.md

Project conventions for Claude Code to follow when working in this repository.

## Project Context

A single-deployment backend for one family's finances: accounts, categories,
a typed transaction ledger, budgets and goals (Phase 2), and bills (Phase 4).
Everything in current scope is built. Phases 5 (investments) and 6 (net worth)
are written up but out of scope — don't start them unless asked. Modular
monolith, no external services — it runs as a Docker container on a personal
machine behind the home network.

The requirements are the source of truth and every open question in them is
already decided: read `.claude/requirements/00-architecture-and-foundations.md`
first, then the relevant phase doc, before changing behaviour.

- Language: Kotlin 2.3, Java 21 toolchain
- Framework: Spring Boot 3.5 (Web, Data JPA, Validation, Security, Actuator)
- Database: PostgreSQL, schema owned by Flyway (`ddl-auto: validate`)
- Build: Gradle (Kotlin DSL), ktlint gating `./gradlew build`
- Base package: `com.familyfinance.crm`; every endpoint under `/api/v1/`

## Kotlin Code Style

- Follow the official Kotlin coding conventions; enforce with **ktlint** or **detekt** in the build (fail the build on violations, don't just warn).
- Prefer `val` over `var`; treat mutability as something you opt into, not the default.
- Never use `!!`. Use `?.`, `?:`, safe casts, or an explicit `require`/`check` with a message instead.
- Use data classes for DTOs and value objects, not for JPA entities (see Persistence section — `equals`/`hashCode`/`copy` semantics break with lazy proxies and mutable IDs).
- Use `sealed class` / `sealed interface` for closed hierarchies — domain results, error types, state machines — and rely on exhaustive `when` (no `else` branch) so new cases fail to compile until handled.
- Keep functions small; use expression-body syntax (`fun x() = ...`) when it's a single expression and stays readable.
- Extension functions are for genuine utility/readability, not for smuggling business logic out of the class that owns it.
- Use named arguments once a call has 3+ parameters, especially when several share a type (avoids transposition bugs).
- No wildcard imports.
- Default to immutable collection interfaces (`List`, `Set`, `Map`); reach for `Mutable*` only in a narrowly scoped, local context.
- Prefer top-level functions/constants over a `companion object` grab-bag, unless the thing genuinely belongs to the class (factory methods, constants tied to the type).

## Spring Boot Best Practices

- **Constructor injection only.** No `@Autowired` on fields or setters. With Kotlin this means a primary constructor and `private val` properties.
- Apply the `kotlin-spring` and `kotlin-jpa` Gradle plugins — they auto-open classes Spring needs to proxy and auto-generate no-arg constructors for JPA, so you don't have to hand-`open` everything.
- Layered architecture, one-directional: **Controller → Service → Repository**. No query logic in controllers, no HTTP concerns in services.
- DTOs at the API boundary — never return JPA entities directly from a controller (lazy-loading leaks, unintentional exposure of fields, tight coupling to schema).
- Centralize error handling in a single `@RestControllerAdvice`; controllers shouldn't have their own try/catch for anything expected.
- Typed configuration via `@ConfigurationProperties` data classes (`val` properties, constructor-bound), not scattered `@Value("${...}")` injections.
- Validate at the boundary: `jakarta.validation` annotations on request DTOs, not manual `if` checks in the service for basic shape/format validation.
- `@Transactional` belongs on service methods, scoped as narrowly as possible. Never on controllers; avoid on repository methods (Spring Data already wraps CRUD ops).
- For expected business failures (not-found, conflict, validation), prefer a typed/sealed result over throwing where the call site needs to branch on outcome; reserve exceptions for actually-exceptional or infra-level failures.

## Persistence / PostgreSQL

- Schema changes go through **Flyway** migrations, checked into `db/migration`. Never rely on `ddl-auto: update`/`create` outside a disposable local sandbox.
- snake_case in the database, camelCase in Kotlin — let Hibernate's naming strategy handle the translation; don't fight it with manual `@Column` names everywhere.
- JPA entities are `open class` with `var` for mapped fields and a stable, non-generated `equals`/`hashCode` (typically ID-based, handling the transient/unsaved case). Do not use data classes for entities.
- Be explicit about fetch strategy. Default to `LAZY` on associations, and use `@EntityGraph` or an explicit fetch-join JPQL query where you know you'll need the association, rather than triggering N+1 lookups.
- `JSONB` is fine for genuinely semi-structured or sparse data — don't reach for it as a shortcut around modeling a relation properly.
- Index foreign keys and any column you filter/sort on regularly; put the index in the same migration as the column, not as an afterthought.
- Configure HikariCP explicitly for production (pool size, timeout) — don't ship on framework defaults sized for nothing in particular.

## Testing

- Unit tests: JUnit 5 + **MockK** (not Mockito — better fit for Kotlin's final classes, coroutines, and lambda syntax).
- **Unit tests only, for now.** The requirements defer integration/end-to-end tests until the React frontend and Telegram bot exist, so don't add them unprompted. When they do come: `@SpringBootTest` + **Testcontainers** against a real Postgres container, not H2 (H2's SQL dialect and constraint behavior diverge from Postgres often enough to hide bugs).
- Descriptive backtick test names: `` fun `returns 404 when user not found`() ``.
- One behavior per test; arrange–act–assert, no branching/looping inside a test.

## Project Structure

```
src/main/kotlin/.../
  config/
  web/                # controllers
  security/           # JWT filter and service
  service/            # interface + Impl pairs
  repository/
  domain/
  dto/
  exception/
src/main/resources/
  db/migration/       # Flyway scripts, Vxx__description.sql
  application.yml
src/test/kotlin/...
```

## Tooling

- Gradle Kotlin DSL (`build.gradle.kts`), not Groovy.
- ktlint wired into the Gradle build (`ktlintCheck` runs as part of `check`);
  `./gradlew ktlintFormat` fixes most violations.
- CI gate: build + lint + tests must all pass before merge; no exceptions for "just this once."
- Deployment: the `Dockerfile` builds the jar inside the image;
  `docker compose --profile app up -d --build` runs it next to Postgres.
  Plain `docker compose up -d` starts Postgres alone for local development.
