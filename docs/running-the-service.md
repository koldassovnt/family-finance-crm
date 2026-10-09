# Running the service

How to run the backend locally, and how to put it on a real server. The
backend lives in this repo; the React frontend is a **separate repo**
(`family-finance-crm-front`) and is covered at the end.

Everything here assumes Docker. The jar is built **on the host** and the image
only packages it: `./gradlew bootJar` first, then compose. Building inside the
image meant downloading the Gradle distribution from GitHub on every cold
build, which the production host cannot reliably reach.

---

## What you need

- **Docker** with Compose v2 (`docker compose version`).
- **JDK 21** on the host, for `./gradlew bootJar`. The wrapper fetches Gradle
  itself the first time; after that it is cached under `~/.gradle`. No Postgres
  client is needed, though `psql` is handy.
- About 1 GB of RAM for the JVM and 300 MB for Postgres, comfortably.

**Always rebuild the jar before `--build`.** The image copies whatever
`build/libs/app.jar` holds, so `docker compose ... up -d --build` alone
repackages the old jar and runs stale code without complaint. On Windows,
`gradlew.bat bootJar` from PowerShell or `./gradlew bootJar` from Git Bash.

---

## Local run

From the repo root:

```bash
# 1. Secrets and ports live in .env, which is gitignored. Create it once.
#    Both secrets are required; compose refuses to start without them.
cat > .env <<EOF
JWT_SECRET=$(openssl rand -hex 32)
DB_PASSWORD=$(openssl rand -hex 16)
EOF

# Keep JWT_SECRET stable. Changing it invalidates every issued token, so
# everyone logs in again. DB_PASSWORD only takes effect when the data volume
# is first created — see "Write a real .env" below.

# 2. Build the jar, then start Postgres and the app together.
./gradlew bootJar
docker compose --profile app up -d --build
```

The app is then on <http://localhost:8080>, with the browsable API at
<http://localhost:8080/swagger-ui.html> and a health check at
`/actuator/health`. Flyway applies the schema on startup, so an empty database
becomes a current one with no extra step.

`docker compose up -d` **without** `--profile app` starts only Postgres, which
is what you want when running the app from an IDE or `./gradlew bootRun`.

### If a port is already taken

`POSTGRES_PORT` and `APP_PORT` in `.env` change only what is published on the
host; the containers always talk to each other on 5432 and 8080 internally.
Postgres defaults to `127.0.0.1:6432` — loopback only, and off 5432 so it does
not collide with another local Postgres. Point a DB viewer (DBeaver, IntelliJ)
at `localhost:6432`, user `family_finance`, with the `DB_PASSWORD` from `.env`.

### Create the first user

There is deliberately **no signup endpoint** — at the point the first user is
created there is nobody to authorize the call. Instead the app creates the
`OWNER` itself, on a start that finds none, from three variables in `.env`:

```bash
OWNER_EMAIL=you@example.com
OWNER_DISPLAY_NAME='Your Name'
OWNER_PASSWORD='8-to-128-characters'   # single quotes keep a $ literal
```

Then (re)start the app: `docker compose --profile app up -d`. The log says
`Created the OWNER ...`. The rules are the API's own — a valid email, a
password of 8–128 characters — and a bad or partial value **stops the app
from starting** with a message naming the problem, rather than leaving it
running with nobody able to log in. With none of the three set and no `OWNER`
yet, it starts and logs a warning instead.

Once the `OWNER` exists the variables are ignored for good, so they cannot
reset a password or create a second `OWNER`. Change the password through
`POST /api/v1/users/me/password`, then delete `OWNER_PASSWORD` from `.env`;
the app logs a reminder on every start until you do.

**Optional: prices and exchange rates.** With an API Ninjas key in `.env` the
app fetches the price of every asset held and the KZT rate of every account
currency once a day at 08:00 Almaty time, at most 30 requests per API per day:

```bash
API_NINJAS_KEY=your-key
```

Restart the app after setting it. Without it the app runs as before and
holdings show purchase cost only. To fetch immediately rather than wait for
08:00: `POST /api/v1/market-data/refresh`; its answer says how many symbols
were updated, failed, or left for tomorrow by the cap. The container needs
outbound HTTPS to `api.api-ninjas.com`.

Then log in and keep the token:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"your-password"}'
```

Every other user is created afterwards through `POST /api/v1/users`, which is
owner-only and can only create a `MEMBER`.

### Day-to-day commands

```bash
docker compose --profile app ps              # what is running
docker compose --profile app logs -f app     # follow the app log
./gradlew bootJar && docker compose --profile app up -d --build   # after code changes
docker compose --profile app restart app     # restart without rebuilding
docker compose --profile app down            # stop; the data volume survives
```

`docker compose down -v` deletes the data volume and everything in it. There
is no undo.

---

## On a real server

The same compose file runs it. What changes is the secret, the exposure and
the backups.

### 1. Get the code and build

```bash
git clone git@github.com:koldassovnt/family-finance-crm.git
cd family-finance-crm
```

### 2. Write a real `.env`

```bash
umask 077                                   # .env is readable only by you
cat > .env <<EOF
JWT_SECRET=$(openssl rand -hex 32)
DB_PASSWORD=$(openssl rand -hex 16)
APP_PORT=8080
EOF
```

On Windows, run this from Git Bash, which ships `openssl`; `umask` has no
effect there, so `.env` is protected by your user profile's permissions.

Both services read `DB_PASSWORD` from `.env`, and compose refuses to start
without it. Set it **before the first start**: `POSTGRES_PASSWORD` only takes
effect when the data volume is created. Changing it later means an
`ALTER USER` inside the running container, not an edit here.

### 3. Keep Postgres off the network

Postgres is published on `127.0.0.1:6432` only: a DB viewer on the server
itself can connect, nothing else on the network can. The app reaches it over
the compose network. From another machine, go through an SSH tunnel rather
than widening `POSTGRES_PORT` — Docker Desktop on Windows publishes a bare
port on every interface, and its firewall rule usually lets the LAN in.

### 4. Put TLS in front of it

The app speaks plain HTTP and has no TLS of its own. **Do not expose port 8080
to the internet.** Options, in order of preference:

- **Home network / VPN only** — what the requirements assume. Bind the app to
  the LAN and reach it over WireGuard or Tailscale from outside.
- **A reverse proxy** (Caddy, nginx, Traefik) terminating HTTPS on 443 and
  forwarding to `127.0.0.1:8080`. Caddy is two lines and gets you a
  certificate automatically:

  ```
  finance.example.com {
      reverse_proxy 127.0.0.1:8080
  }
  ```

  Then set `APP_PORT=127.0.0.1:8080` in `.env` so the container publishes only
  on loopback and the proxy is the sole way in.

Note on CORS: the API allows **all origins**, which is safe *because* auth is a
bearer token rather than a cookie — a random site cannot attach a token it has
no access to. That stops being true if auth ever moves to cookies, at which
point CORS must become a real allowlist.

### 5. Start it, and keep it started

```bash
./gradlew build                     # lint + tests, and writes build/libs/app.jar
docker compose --profile app up -d --build
```

Both services are `restart: unless-stopped`, so they come back after a reboot
as long as Docker itself starts at boot (`systemctl enable docker`).

### 6. Back it up

Backups are required — the spec is "Backups" under Non-Functional
Requirements in `.claude/requirements/00-architecture-and-foundations.md`.
The `backup` compose service (`db/backup.sh`) does it: a `pg_dump` custom-format
file every night at 03:00 Almaty time into `BACKUP_DIR`, keeping the last 14
nightly dumps plus the first of each month for 12 months. Set the folder in
`.env`, on a different physical disk from Docker's data:

```bash
BACKUP_DIR=D:/family-finance-backups
```

On start it takes a dump straight away if none is under 26 hours old. Files
are named `family_finance-YYYY-MM-DD_HHMM.dump`, about 40 KB each today.

```bash
# On demand — always before an upgrade
docker compose --profile app exec backup sh /scripts/backup.sh run

# Is it working? "unhealthy" means the newest dump is over 26 hours old
docker compose --profile app ps backup
docker compose --profile app logs backup
```

**Test a restore** into a throwaway database, never over the live one — once
after setup, and after every Postgres major-version upgrade. An untested
backup is a guess:

```bash
docker compose --profile app exec backup sh -c '
  createdb restore_test &&
  pg_restore --dbname=restore_test --no-owner --exit-on-error /backups/family_finance-2026-10-02_2313.dump &&
  psql -d restore_test -c "select email, role from users" ;
  dropdb restore_test'
```

**A real restore** replaces the live database, so stop the app first and
take one more dump of the current state, in case the backup is the wrong one:

```bash
docker compose --profile app stop app
docker compose --profile app exec backup sh /scripts/backup.sh run
docker compose --profile app exec backup \
  pg_restore --dbname=family_finance --clean --if-exists --no-owner --exit-on-error \
  /backups/family_finance-YYYY-MM-DD_HHMM.dump
docker compose --profile app start app
```

Flyway validates the restored schema on start; a dump from a newer version of
the code than you are running fails loudly there rather than half-working.

### 7. Upgrading

```bash
git pull
docker compose --profile app exec backup sh /scripts/backup.sh run
./gradlew build
docker compose --profile app up -d --build
```

Flyway applies any new migrations on startup and the app refuses to start if
the schema does not match what the code expects (`ddl-auto: validate`), so a
mismatch is a loud failure rather than silent corruption. Take a dump before
upgrading anyway.

---

## The frontend

Separate repo: `family-finance-crm-front` (React + TypeScript + Vite). It is a
static bundle — no server of its own.

```bash
cd family-finance-crm-front
npm install

# Point it at the API. Same-origin behind a proxy, or an absolute URL.
echo 'VITE_API_BASE_URL=https://finance.example.com' > .env

npm run dev      # development, http://localhost:5173
npm run build    # production bundle into dist/
```

Serve `dist/` with whatever already terminates TLS. With Caddy, one site block
does both halves:

```
finance.example.com {
    handle /api/* {
        reverse_proxy 127.0.0.1:8080
    }
    handle /swagger-ui* {
        reverse_proxy 127.0.0.1:8080
    }
    handle {
        root * /srv/family-finance-front/dist
        try_files {path} /index.html      # client-side routing
        file_server
    }
}
```

With that layout the frontend and API share an origin, so set
`VITE_API_BASE_URL=https://finance.example.com` (or leave it empty and let the
client default to the same host) and rebuild.

---

## Troubleshooting

**The app container exits immediately.** Almost always the JWT secret:
it must be at least 32 bytes, and the app refuses to start without one. Check
with `docker compose --profile app logs app | tail -20`.

**`FlywayValidateException` on startup.** The database schema does not match
the migrations — usually a hand-edited table, or a database created by a newer
version of the code than you are now running. Restore from a dump rather than
deleting migration rows.

**401 on every request right after a restart.** Check whether `JWT_SECRET`
changed. A new secret invalidates every token ever issued; logging in again is
the fix. Also expected after any password change, by design.

**Login returns 401 with the right password.** Confirm the `OWNER` was
actually created — look for `Created the OWNER` or `No OWNER exists` in the
app log, or `docker exec family-finance-postgres psql -U family_finance
-d family_finance -c 'select email, role from users;'`. If the password in
`.env` contains a `$` and is not single-quoted, compose expanded it as a
variable and the password stored is not the one you typed.

**The app exits with `Bootstrap owner ... is invalid`.** One of the `OWNER_*`
variables is missing or breaks the API's rules; the message says which.

**Port already in use.** Change `POSTGRES_PORT` or `APP_PORT` in `.env`; they
affect only what is published on the host.

**Timezone.** The app is fixed to `Asia/Almaty` regardless of the server's
clock setting — "today" for a due date or a future-date check always means
today in Almaty. Set `app.timezone` in `application.yml` if that ever needs to
change; nothing else in the system reads a timezone.
