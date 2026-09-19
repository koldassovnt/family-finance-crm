# Decisions log

Choices made in the sessions of **2026-09-15 → 2026-09-19**, with the reason
attached. The phase docs carry the big architectural decisions; this file
records the smaller ones that a later session could undo by accident because
the reasoning is not visible in the code.

## API

**`GET /api/v1/transactions` exists rather than fanning out per account.**
The frontend needed one ledger view across every account. Fanning out
client-side meant N requests and de-duplicating transfers, which come back on
both accounts. The endpoint keeps the same one-year bounded window as the
per-account history.

**`GET /api/v1/users/me` exists because there is no refresh flow.** A client
with a stored token and no user in memory after a reload had nothing to call.
Decoding the JWT was the alternative: it carries `sub`, `email`, `role`, but
no display name, and a client-side decode is unverified — fine for hiding a
nav item, never a security boundary.

**`toAmount` is patchable, and required alongside a changed `amount` on a
cross-currency transfer.** The two sides of such a transfer are credited
independently. Editing only `amount` moved the source and left the destination
holding the old figure, so the pair silently implied an exchange rate nobody
chose. Making the half-corrected state inexpressible was better than
documenting it.

**A topic accepts `INCOME` and `EXPENSE` only.** A `TRANSFER` would count both
the cash withdrawal and the meal it paid for. `INCOME` is allowed because a
refund or money repaid by a travel companion genuinely belongs to a trip's net
cost.

**`GET /topics/{id}/transactions` requires no date range**, unlike the ledger
list. Topic membership is itself the bound. The one-year cap elsewhere exists
because an unbounded ledger query is unbounded; a trip is not.

## Serialization and formatting

**Money serializes as a JSON number with trailing scale digits**
(`1234.5600`, `exchangeRate: 1.000000`), and **write responses differ from
reads** — a POST echoes `45000` where a later GET returns `45000.0000`, because
one is the parsed `BigDecimal` and the other comes from `numeric(19,4)`.
Deliberately **not normalized**: the frontend formats to two decimals anyway,
which makes it invisible. Do not assert on raw response text.

**`percentUsed` is scale 2 while money is scale 4.** A percentage rounded for
display is not a money quantity; matching them would give a percentage four
decimal places for no reason.

**`fieldErrors` is omitted entirely when empty**, not sent as `{}` —
`ErrorResponse` is `@JsonInclude(NON_EMPTY)` at class level, so treat any
empty-valued property as absent.

## Things that look like bugs and are not

**A transaction keeps resolving a soft-deleted category's or topic's name.**
Both entities deliberately lack `@SQLRestriction`, because it would apply to
relationship loading and silently null them out on historical rows. So the API
can return a category or topic that is absent from its own list endpoint.
Render it.

**A soft-deleted account's transactions stay put, and the money they moved
stays moved.** Only an active goal blocks deleting an account; transactions
never do. So the ledger can carry an `accountId` absent from `GET /accounts`,
and the other side of a transfer keeps what it received.

**`overdue` is computed server-side against `Asia/Almaty`.** Recomputing it in
a browser disagrees for most of the day outside that timezone, and near
midnight inside it. The wording "derived, not stored" means the server does not
persist a status field — it is not an instruction to the client.

**`unpaid=true` means "still owed", not "late"** — it includes future due
dates. An attention list filters on `overdue` itself.

**A `CLOSED` topic still accepts attachments.** A late invoice is normal;
closing only drops it from the transaction form's picker.

## Infrastructure

**The Dockerfile builds the jar inside the image.** The host needs only
Docker — no JDK, no Gradle. Lint and tests are not run in the image;
`./gradlew build` is the gate for those.

**The app service sits behind a compose profile.** `docker compose up -d`
stays a Postgres-only command for local `bootRun`, while
`--profile app` runs the whole stack.

**`.env` is gitignored and holds `JWT_SECRET` plus port overrides.** Host 5432
is taken on this machine by another project, hence `POSTGRES_PORT=55432`.

**The whole `.idea/` directory is ignored.** It was partly tracked, including
`dataSources.xml`, which IntelliJ had staged and which records local database
connection details.

## Process

**No dark mode.** The user's decision; light theme only.

**Reseeding test data is not free.** Figures recorded as verified by the other
session move when the dataset changes, and nothing announces it. State what
moved, not just what was added — and prefer additive changes, like attaching an
existing transaction, which move no totals.

**Neither Claude session has seen the frontend in a browser.** Both logs are
careful to distinguish "API shapes confirmed and rendering traced" from
"watched it work". Keep that distinction.
