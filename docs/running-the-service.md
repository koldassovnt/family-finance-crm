# Running the service

How to run the backend locally, and how to put it on a real server. The
backend lives in this repo; the React frontend is a **separate repo**
(`family-finance-crm-front`) and is covered at the end.

Everything here assumes Docker. There is no other supported way to run it:
the image builds the jar itself, so the host needs no JDK and no Gradle.

---

## What you need

- **Docker** with Compose v2 (`docker compose version`). Nothing else —
  no Java, no Gradle, no Postgres client, though `psql` is handy.
- About 1 GB of RAM for the JVM and 300 MB for Postgres, comfortably.
- The first build downloads the Gradle distribution and the dependencies, so
  it takes a few minutes and needs network. Later builds reuse a cache mount.

---

## Local run

From the repo root:

```bash
# 1. Secrets and ports live in .env, which is gitignored. Create it once.
cat > .env <<'EOF'
JWT_SECRET=replace-me-with-32-bytes-or-more
POSTGRES_PORT=5432
APP_PORT=8080
EOF

# Generate a real secret rather than typing one:
#   openssl rand -hex 32
# Keep it stable. Changing it invalidates every issued token, so everyone
# logs in again.

# 2. Start Postgres and the app together.
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
On a machine already running another Postgres, `POSTGRES_PORT=55432` is the
usual fix.

### Create the first user

There is deliberately **no signup endpoint** — at the point the first user is
created there is nobody to authorize the call. Do it once, by hand:

```bash
# 1. Hash a password. Needs a JDK locally; if you have none, see below.
./gradlew printPasswordHash -Ppassword='your-password'

# 2. Put the hash, your email and your name into db/bootstrap-owner.sql,
#    then run it inside the database container:
docker exec -i family-finance-postgres \
  psql -U family_finance -d family_finance < db/bootstrap-owner.sql
```

No JDK on the host? Generate the BCrypt hash with `htpasswd` from a small
image instead — verified against this app, which accepts the `$2a$` form:

```bash
docker run --rm httpd:alpine htpasswd -bnBC 10 "" 'your-password' \
  | tr -d ':\n' | sed 's/^\$2y/\$2a/'
```

(`htpasswd` emits `$2y$`; Spring's encoder accepts it, but rewriting the
prefix to `$2a$` keeps every hash in the table looking the same.)

Running the Gradle task inside a JDK image also works, but with no Gradle
cache in the container it downloads the distribution and every dependency
first — several minutes for one hash, so prefer the command above.

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
docker compose --profile app up -d --build   # rebuild after code changes
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
POSTGRES_PORT=5432
APP_PORT=8080
EOF
```

**Change the database password from the default.** `compose.yaml` ships with
`family_finance` as the user, password and database name, which is fine on a
laptop and not fine on a server. Point both services at the new one by editing
`compose.yaml`:

```yaml
  postgres:
    environment:
      POSTGRES_PASSWORD: ${DB_PASSWORD:?set DB_PASSWORD}
  app:
    environment:
      DB_PASSWORD: ${DB_PASSWORD:?set DB_PASSWORD}
```

Do this **before the first start**: `POSTGRES_PASSWORD` only takes effect when
the data volume is created. Changing it later means an `ALTER USER` inside the
running container, not an edit here.

### 3. Do not publish Postgres

On a server, delete the `ports:` block from the `postgres` service. The app
reaches it over the compose network; nothing outside the host needs to. If you
want `psql` access, go through `docker exec` or an SSH tunnel.

```yaml
  postgres:
    # ports:            <- remove on a server
    #   - "5432:5432"
```

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
docker compose --profile app up -d --build
```

Both services are `restart: unless-stopped`, so they come back after a reboot
as long as Docker itself starts at boot (`systemctl enable docker`).

### 6. Back it up

The requirements call backups unnecessary on a personal machine. On a real
server holding your family's finances, they are not. The whole database is one
volume:

```bash
# Dump (small; this database stays in the megabytes for years)
docker exec family-finance-postgres \
  pg_dump -U family_finance family_finance | gzip > backup-$(date +%F).sql.gz

# Restore into an empty database
gunzip -c backup-2026-09-19.sql.gz | \
  docker exec -i family-finance-postgres psql -U family_finance -d family_finance
```

A nightly cron with a fortnight of retention is enough:

```cron
0 3 * * * cd /srv/family-finance-crm && docker exec family-finance-postgres pg_dump -U family_finance family_finance | gzip > backups/$(date +\%F).sql.gz && find backups -name '*.sql.gz' -mtime +14 -delete
```

Test a restore once. An untested backup is a guess.

### 7. Upgrading

```bash
git pull
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

**Login returns 401 with the right password.** Confirm the bootstrap row
actually landed: `docker exec family-finance-postgres psql -U family_finance
-d family_finance -c 'select email, role from users;'`

**Port already in use.** Change `POSTGRES_PORT` or `APP_PORT` in `.env`; they
affect only what is published on the host.

**Timezone.** The app is fixed to `Asia/Almaty` regardless of the server's
clock setting — "today" for a due date or a future-date check always means
today in Almaty. Set `app.timezone` in `application.yml` if that ever needs to
change; nothing else in the system reads a timezone.
