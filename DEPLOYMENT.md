# Deploying the Server & Web Client

This covers [/server](./server) (Ktor backend) and [/web](./web) (React frontend) — the pieces a college
runs themselves to give their own students a hosted CEF instance. It does not cover the Android/iOS/Desktop
apps (see [README.md](README.md) for those).

See [SPEC.md](SPEC.md) for the API/protocol design and
[docs/adr/0002-multi-tenant-docker-path-partitioned-storage.md](docs/adr/0002-multi-tenant-docker-path-partitioned-storage.md)
for the multi-tenancy architecture this deployment shape is built around.

## Quick Start (IT Staff — No Programming Required)

1. Install [Docker Desktop](https://www.docker.com/products/docker-desktop/) if you don't have it, and make sure it's running.
2. Download or `git clone` this repository.
3. Open a terminal in the repository folder and run:
   ```shell
   ./scripts/setup-college-server.sh
   ```

That's it. The script checks that Docker is installed, creates a `.env` file if you don't have one (Google
Calendar sync fields are optional — leave them blank to skip that feature for now), builds and starts the
server and web client, and waits until the server responds before printing a "done" message with the web
address to open.

Backups run automatically every 24 hours — nothing to configure. To update to a newer version later, run
`git pull` then re-run the same script; it rebuilds and restarts in place without touching student data.

The rest of this document explains what that script does under the hood, and covers maintenance
(backups/restore) and things that aren't set up yet (Litestream, per-tenant Google OAuth).

## Local Development

Run both, in separate terminals:

```shell
./gradlew :server:run          # Ktor backend on :8080
```

```shell
cd web
npm install                    # first run only
npm run dev                    # Vite dev server on :5173, proxies /api to :8080
```

Open the URL Vite prints (typically `http://localhost:5173`). No env vars are required to boot — the server
lazily creates a per-tenant SQLite database on first request. Set the Gemini API key through the app's
**Settings** page (`POST /api/settings`) once it's running; it is never read from `.env` in production
(see `ServerContainerFactory`).

Requests are routed to a tenant by a signed session cookie, not a client-supplied header — visiting the
app calls `POST /api/auth/start` once to establish one (no signup, no password; see
[docs/adr/0005-session-based-student-auth.md](docs/adr/0005-session-based-student-auth.md)). A plain
`curl` without first calling `/api/auth/start` (and carrying its cookie) gets `401 Unauthorized`:
```shell
curl -c cookies.txt -X POST http://localhost:8080/api/auth/start
curl -b cookies.txt http://localhost:8080/api/sources
```

## Production Deployment (Docker)

```shell
docker compose up -d --build server web
```

This builds and runs two containers (see `server/Dockerfile`, `web/Dockerfile`, `docker-compose.yml`):
- **`server`**: the Ktor backend (a `shadowJar` fat jar), with tenant SQLite databases written to a named
  volume (`tenant-data`) mounted at `/data/tenants`.
- **`web`**: the built React app served by nginx, which reverse-proxies `/api/*` (including the
  `/api/agent/stream` SSE endpoint) to `server`.

By default the web client is reachable at `http://localhost` (port 80). `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`
are read from the root `.env` if present (needed only to refresh already-linked Google tokens — see the Google
Cloud Console setup in README.md). As in local dev, **no Gemini key is baked into the image or auto-imported
into any tenant** — each tenant sets their own via the Settings page after the stack is up.

For a real production rollout, put a TLS-terminating reverse proxy (Caddy, Traefik, or your cloud load
balancer) in front of the `web` container — this compose file does not set up HTTPS itself. Once you do,
set `CEF_FORCE_SECURE_COOKIES=true` in your `.env` so session cookies get the `Secure` flag — it's off by
default because the plain-HTTP quick start above wouldn't work otherwise. The key used to sign session
cookies is auto-generated on first boot and persisted to `.session-secret` inside the tenant volume (a
restart won't log everyone out); set `CEF_SESSION_SECRET` yourself if you'd rather pin it explicitly.

## Maintenance

### Backups (VACUUM INTO snapshots) — automatic, no setup required

`docker-compose.yml` runs the server with `CEF_BACKUP_DIR=/data/backups` and
`CEF_BACKUP_INTERVAL_HOURS=24` set. `ScheduledBackupJob` (started from `main()`) runs immediately at
startup and then on that interval for as long as the container is alive — no host cron, no manual step.
Each run walks the tenant volume and runs SQLite's `VACUUM INTO` on every tenant database, producing a
consistent, compacted snapshot copy in a *separate* named volume (`tenant-backups`, distinct from
`tenant-data`) — safe to run against live databases (`VACUUM INTO` doesn't lock out readers/writers the
way a full `VACUUM` does).

Check it's running:
```shell
docker compose logs server | grep ScheduledBackupJob
```
You should see a line like `[ScheduledBackupJob] Backed up N tenant database(s) to /data/backups (0 failure(s))`
once at startup and again every `CEF_BACKUP_INTERVAL_HOURS`. Change the frequency by setting
`CEF_BACKUP_INTERVAL_HOURS` in your `.env`; disable it entirely by removing `CEF_BACKUP_DIR` from
`docker-compose.yml`.

**Off-host copies:** the `tenant-backups` volume protects against corruption or an accidental delete of
`tenant-data`, but it's still on the same Docker host — for protection against a full host/disk failure,
periodically copy the `tenant-backups` volume's contents to wherever your institution already does backups
(network share, cloud storage, tape, whatever you use today). That copy step isn't automated here; it's a
plain directory of `.db` files, so any file-based backup tool works.

**To restore a tenant:** stop the server, copy the relevant `<studentId>.db` file from `tenant-backups` back
into its hash-partitioned path under `/data/tenants` (`<md5-hash-first-2-chars>/<next-2-chars>/<studentId>.db`
— or just overwrite the same relative path in the volume), and restart.

**Running a backup manually** (e.g. right before a risky change, rather than waiting for the schedule):
```shell
docker exec <server-container> java -cp server.jar com.borinquenterrier.cef.BackupCliKt /data/tenants /data/backups
```
or, from a bare-metal/Gradle checkout: `./gradlew :server:vacuumBackup -Ptenant=/path/to/tenants -Pbackup=/path/to/backups`.
This is the same `BackupCli.kt` entrypoint the scheduled job calls internally; it prints a summary and exits
non-zero on any failure, so it's also usable from your own tooling/health checks if you'd rather not rely on
the built-in scheduler.

### Continuous replication (Litestream) — config generation only

`LitestreamConfigGenerator` produces a Litestream YAML config (S3, Azure Blob, or MinIO-compatible replicas)
for every `.db` file you pass it, for continuous point-in-time WAL replication rather than periodic snapshots.
This generator is implemented and tested, but **the Litestream binary itself is not part of the Docker image or
compose file** — to actually use it, add a `litestream` sidecar container (or install the binary in the
`server` image) pointed at a config generated by this class, mounted read-only into the same `tenant-data`
volume. Treat the automatic VACUUM INTO backups above as the baseline; Litestream is the upgrade path if you
need tighter recovery-point objectives than "up to `CEF_BACKUP_INTERVAL_HOURS` of data since the last snapshot."

### Schema migrations across tenants

Normal schema upgrades need no manual action: `DriverFactory` migrates a tenant's database automatically the
next time it's opened (i.e., the next request for that student), the same lazy-migration behavior the desktop
app has always used. `TenantMigrationRunner` exists for the case where you'd rather migrate every tenant
proactively — e.g. during a maintenance window, ahead of a version bump, instead of paying the migration cost
on each tenant's first post-upgrade request — but it isn't wired to a CLI entrypoint yet. If you need that,
it's a small addition following the same pattern as `BackupCli.kt`.

## Known Gaps

* **Per-tenant Google Calendar linking isn't solved.** The web client's Settings page still says "Run the
  desktop app to authenticate via OAuth, then refresh this page" — this deployment currently assumes a
  tenant's Google account was already linked elsewhere. A self-serve OAuth flow for web-only tenants is
  future work.
* **No session recovery.** Clearing cookies or switching devices loses access to a tenant permanently —
  there's no username/password to log back in with (see ADR 0005). A "copy/email yourself your access
  link" affordance is a planned fast-follow, not yet built.
* **No LMS-embedded login (LTI).** Auth today is a standalone session cookie, not a Canvas/Blackboard/Moodle
  launch — see ADR 0005's "Alternatives Considered" for why this was deferred rather than built first.
