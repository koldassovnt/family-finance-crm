# Decisions log

Choices made in the sessions of **2026-09-15 → 2026-09-19**, with the reason
attached. The phase docs carry the big architectural decisions; this file
records the smaller ones that a later session could undo by accident because
the reasoning is not visible in the code.

## API

**`scope` defaults to `OWN` on every list that accepts it.** Sharing added
`scope=OWN|SHARED|ALL` to accounts, goals, budgets, bills and topics rather
than folding shared rows into the default answer. `OWN` is the scope where
nothing can sum across owners by accident, so it is the safe default — and it
also means no caller written before Phase 8 changed behaviour.

**A foreign resource id is always 404, never 403.** Sharing something you do
not own, revoking someone else's grant, asking who else can see a resource you
only view: all of them answer 404, because a 403 would confirm the id exists.
The one 400 is sharing with yourself, which reveals nothing.

**`GET /api/v1/users` is open to any authenticated member, not just the OWNER.**
Sharing needs a picker, and in a household of a few people who already know
each other's names, hiding the list protects nothing. It is a real widening of
Phase 0/1, where members were invisible to each other, so it is recorded rather
than slipped in.

**A summary can be re-read against source; a bad default cannot.** Two mistakes
in the Phase 8 handoff were caught by the other session reading the DTOs. One
was prose — describing `ShareAccess` and `AccessLevel` as one enum — and any
careful re-reading of the source would have caught it. The other was in the
design: a nested account defaulting to `access: "OWNER"`. Re-reading the summary
more carefully could never have surfaced that, because the summary was right and
the code was wrong. Worth keeping apart when deciding how much to trust a
handoff: check prose against source, but a default that asserts the most
permissive value is only found by someone asking what a field means.

**An embedded resource carries no access badge.** `access` and `owner` say how
the caller reached the resource they asked for, so they belong on that and
nothing nested inside it. A goal's linked account is therefore a separate
`LinkedAccountResponse` with neither field. The first version reused
`AccountResponse`, whose defaults meant a viewer of a shared goal was told
`access: "OWNER"` about an account that 404s for them — the most permissive
value in the enum, asserted on no basis. Found by the frontend session reading
the DTOs. A nested object that *is* the subject of the response, like
`TopicDetailResponse.topic`, is badged on purpose and sets it explicitly.

**Sharing a goal discloses the linked account's balance, and there is no
partial version.** Progress is `balance ÷ targetAmount`, so a response showing
progress and a target is one multiplication from the balance. Hiding the number
while publishing both factors would tell the sharer they are protected when
they are not. The share dialog names the account instead.

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

**A trade is a transaction type, not its own table** (2026-10-08). The owner
described it as a fourth operation next to expense, income and transfer, and
that shape is what makes a purchase debit the account for free: `TRADE` goes
through the same apply/reverse path, so edit and delete correct the balance
with no second bookkeeping entry. The earlier draft kept trades apart from
cash precisely to avoid that second entry; making the trade *be* the entry
removed the reason. The cost is four nullable columns on `transactions`,
guarded by a `CHECK` that they are all set on a trade and all null otherwise.

**`OPENING` is a trade side, not a flag** (2026-10-08). An asset held before
tracking began counts toward the holding and moves no cash. A side makes the
three cases one exhaustive `when` in `applyToBalances`; a boolean next to
`BUY` would have allowed a cash-free `SELL`, which means nothing.

**There is no instrument table and no price** (2026-10-08). A ticker is free
text on the trade, and the kind of asset goes in the note — the owner's call,
to keep entry to four fields. Holdings therefore report cost, never value.
Do not add a price or rate table speculatively: the owner intends to pick
external APIs for both, and the shape should follow whatever those return.

**An edit or delete may not leave a ticker oversold** (2026-10-08). Found by
driving a real instance, not by a unit test: deleting a purchase from under a
sale returned the purchase's cash and kept the sale's, with no holding left to
show for either. `requireNothingOversold` turns that into a 409. It relies on
Hibernate flushing the change before its query runs, which a mocked
repository cannot show — re-check it against Postgres if it is ever touched.

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

**A viewer sees a transaction's `toAccountId` for an account never shared with
them.** Deliberate: sharing an account shares its history, and a transfer's
other side is part of that. The viewer cannot resolve that id to anything, and
the frontend must not label it as deleted — the account is healthy, it simply
is not theirs to see. «Другой счёт», not «Удалённый счёт».

**A viewer of a shared *goal* can see a balance for an account that 404s on
`GET /accounts/{id}`.** Also deliberate, and the two rules composing is not a
leak: the goal discloses its linked account's name, currency and balance,
while the account itself — and its whole transaction history — stays private.

**`GET /topics/{id}/candidates` is owner-only even though it is a GET.** It
suggests the caller's own unattached transactions for attaching, which makes it
a writing tool wearing a read verb.

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

**The jar is built on the host; the Dockerfile only packages it**
(2026-10-02). It used to build inside the image, but every cold build then
downloaded the Gradle distribution from GitHub, which the production host —
this Windows machine — could not reach. The host already has JDK 21 and a
warm Gradle cache. The cost: `./gradlew bootJar` must run before
`docker compose ... --build`, or the image repackages a stale jar. The boot
jar is named `app.jar` so the Dockerfile never has to choose between it and
the `-plain` jar.

**The app service sits behind a compose profile.** `docker compose up -d`
stays a Postgres-only command for local `bootRun`, while
`--profile app` runs the whole stack.

**`.env` is gitignored and holds `JWT_SECRET`, `DB_PASSWORD` and port
overrides.** Both secrets are required by `compose.yaml` (`${...:?}`), so a
server cannot come up on the old `family_finance` default password by
accident. Postgres publishes on `127.0.0.1:6432` by default (2026-10-02, when
this Windows machine became the production host): loopback so a local DB
viewer works while the LAN does not — Docker Desktop publishes bare ports on
every interface — and off 5432, which another project holds here.

**The `OWNER` is created at startup from `OWNER_*` env vars** (2026-10-02),
replacing `db/bootstrap-owner.sql` and the `printPasswordHash` task. The SQL
route needed a hash generated out of band and a hand-run insert; the env route
is the usual self-hosted pattern (Grafana, Keycloak) and still adds no
endpoint. Guarded by "no `OWNER` exists", so leftover variables are inert; the
app warns while `OWNER_PASSWORD` is still set. Validation reuses
`CreateUserRequest`'s constraints so the two paths cannot drift.

**Backups run in a compose sidecar, not Windows Task Scheduler** (2026-10-02).
The `backup` service uses the database's own image, so `pg_dump` matches the
server's major version, and it runs exactly when the database does. busybox
`crond` does not hand the container environment to its jobs, so the
entrypoint writes the `PG*`/`TZ` variables to `/etc/backup.env` for the job to
source. It dumps once on start when nothing is fresh, so the healthcheck is
green from the first boot rather than red until 03:00.

**The compose network name is pinned** to `family-finance-crm_default`, the
name it already had, because the frontend's compose joins it as an external
network; renaming the folder would otherwise have cut the frontend off.

**`.gitattributes` pins `gradlew` to LF.** With `core.autocrlf=true` on Windows
it was checked out with CRLF, and the Docker build's `./gradlew` failed with
`not found`.

**The whole `.idea/` directory is ignored.** It was partly tracked, including
`dataSources.xml`, which IntelliJ had staged and which records local database
connection details.

## Process

**No dark mode.** The user's decision; light theme only.

**Reseeding test data is not free.** Figures recorded as verified by the other
session move when the dataset changes, and nothing announces it. State what
moved, not just what was added — and prefer additive changes, like attaching an
existing transaction, which move no totals.

**Keep distinguishing "API shapes confirmed" from "watched it work".** That
distinction held for every phase up to 7, when neither session had opened a
browser. It no longer holds uniformly: on **2026-09-20** the frontend session
drove Phase 8's screens in Chrome as both users, at desktop and phone width,
and found three rendering defects that way — defects no amount of tracing had
surfaced. Treat Phase 8's frontend as seen and everything earlier as traced,
and keep saying which when recording anything visual. This backend session has
still never opened the frontend.
