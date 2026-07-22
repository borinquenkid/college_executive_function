# Deploying the Server & Web Client

This covers [/server](./server) (Ktor backend) and [/web](./web) (React frontend) — the pieces a college
runs themselves to give their own students a hosted CEF instance. It does not cover the Android/iOS/Desktop
apps (see [README.md](README.md) for those).

See [SPEC.md](SPEC.md) for the API/protocol design and
[docs/adr/0002-multi-tenant-docker-path-partitioned-storage.md](docs/adr/0002-multi-tenant-docker-path-partitioned-storage.md)
for the multi-tenancy architecture this deployment shape is built around.

## Quick Start (IT Staff — No Programming Required)

Students and staff log in by launching CEF from a link inside your LMS (Canvas, Blackboard, Moodle,
etc.) — there's no separate signup or password (see
[docs/adr/0006-lti-1.3-only-auth.md](docs/adr/0006-lti-1.3-only-auth.md)). That means two things need
to be true before this is usable: the deployment needs to be reachable over HTTPS, and it needs to be
registered as an LTI 1.3 tool in your LMS. Both are one-time setup steps, done once per institution.

1. Install [Docker Desktop](https://www.docker.com/products/docker-desktop/) if you don't have it, and make sure it's running.
2. Download or `git clone` this repository.
3. Put a TLS-terminating reverse proxy in front of where this will run (see "Production Deployment" below) — you'll need its public HTTPS address for the next step.
4. Register CEF as an LTI 1.3 external tool in your LMS — see "Registering CEF as an LTI tool" below for exactly what to enter on both sides. Your LMS admin console will hand you back the values this deployment needs.
5. Open a terminal in the repository folder and run:
   ```shell
   ./scripts/setup-college-server.sh
   ```

The first run creates a `.env` file and stops, asking you to fill in the `CEF_APP_BASE_URL`/`CEF_LTI_*`
values from step 4 (Google Calendar sync fields are optional — leave them blank to skip that feature for
now). Re-run the script once `.env` is filled in: it checks Docker, builds and starts the server and web
client, and waits until the server responds before printing a "done" message with the web address to open.

Backups run automatically every 24 hours — nothing to configure. To update to a newer version later, run
`git pull` then re-run the same script; it rebuilds and restarts in place without touching student data.

The rest of this document explains what that script does under the hood, and covers maintenance
(backups/restore) and things that aren't set up yet (Litestream, per-tenant Google OAuth).

## Registering CEF as an LTI tool

Every LMS's exact screen names differ, but registering an LTI 1.3 "external tool" or "developer key"
asks for the same handful of things, in both directions:

**What you give your LMS admin console (CEF's side):**
| Field | Value |
|---|---|
| OIDC login initiation URL | `<CEF_APP_BASE_URL>/lti/login` |
| Target link / launch URL | `<CEF_APP_BASE_URL>/lti/launch` |
| Redirect URI | `<CEF_APP_BASE_URL>/lti/launch` |

**What your LMS gives back — put these in `.env` (see the template `setup-college-server.sh` creates):**
| `.env` variable | Where it comes from |
|---|---|
| `CEF_LTI_ISSUER` | The platform's issuer URL (e.g. `https://canvas.yourschool.edu`) |
| `CEF_LTI_CLIENT_ID` | The client ID assigned to this tool registration |
| `CEF_LTI_DEPLOYMENT_IDS` | The deployment ID(s) — comma-separated if there's more than one |
| `CEF_LTI_AUTH_LOGIN_URL` | The platform's OIDC authorization endpoint |
| `CEF_LTI_JWKS_URL` | The platform's public keys (JWKS) endpoint |

**Launch placement — use a new window/tab, not an iframe.** Modern browsers (Safari, and Chrome's
third-party-cookie phase-out) block the session cookie inside an iframe launch. Every major LMS
offers a "open in new window" placement option — use it, or the tool will appear broken specifically
in Safari while looking fine elsewhere.

An instructor or admin who launches the tool automatically gets the staff console (see "Staff
console" below) — no separate setup for that.

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

Open the URL Vite prints (typically `http://localhost:5173`). The server lazily creates a per-tenant
SQLite database on first request. Set the Gemini API key through the app's **Settings** page
(`POST /api/settings`) once it's running; it is never read from `.env` in production (see
`ServerContainerFactory`).

`:server:run` now requires `CEF_APP_BASE_URL` and the `CEF_LTI_*` variables (see "Registering CEF as
an LTI tool" above) — it fails fast at startup without them, same as production. Requests are routed
to a tenant by a signed session cookie, minted only by a verified LTI launch (see
[docs/adr/0006-lti-1.3-only-auth.md](docs/adr/0006-lti-1.3-only-auth.md)) — there is no
`curl`-friendly way to create a session by hand, since that would mean accepting an unsigned launch.
Three practical options for exercising this locally:
- **Use the built-in mock LTI platform — fastest way to get a live, browser-drivable demo.**
  `./gradlew :server:runDemoLtiPlatform` starts a standalone mock LMS on `:9099` (source:
  `server/src/main/kotlin/com/borinquenterrier/cef/tools/DemoLtiPlatform.kt`) that plays the
  platform side of a real LTI 1.3 launch — it mints its own RSA keypair, serves it as a JWKS, and
  signs+auto-POSTs a launch id_token back to `/lti/launch` exactly like a real LMS's
  `response_mode=form_post` would. Point a separately-running `:server:run` at it (never at a real
  deployment's `.env`):
  ```shell
  CEF_APP_BASE_URL=http://localhost:8080 \
  CEF_LTI_ISSUER=https://demo-lms.local \
  CEF_LTI_CLIENT_ID=demo-client \
  CEF_LTI_DEPLOYMENT_IDS=demo-deployment \
  CEF_LTI_AUTH_LOGIN_URL=http://localhost:9099/auth \
  CEF_LTI_JWKS_URL=http://localhost:9099/jwks \
  ./gradlew :server:run
  ```
  Then, with Vite also running (see below), open
  `http://localhost:8080/lti/login?iss=https://demo-lms.local&login_hint=demo-student` (add
  `&role=instructor` to land on the staff console instead). The session cookie it mints is a real
  `CEF_SESSION` scoped to the bare `localhost` domain, so it's sent on any `localhost` port —
  switch the browser tab to `http://localhost:5173` once login redirects to the server's bare `/`
  placeholder page, and the actual app loads authenticated.
- **Point at a real LMS sandbox.** Canvas, Blackboard, and Moodle all offer free developer/test
  instances where you can register a tool against `http://localhost:5173` (or an `ngrok`-style
  tunnel, since LTI requires HTTPS) and launch it for real.
- **Use the automated test suite instead of manual curl.** `LtiTestSupport`
  (`server/src/test/kotlin/com/borinquenterrier/cef/lti/LtiTestSupport.kt`) generates a local RSA
  keypair and signs real launch JWTs against it, so `./gradlew :server:test` exercises the entire
  `/lti/login` → `/lti/launch` flow end-to-end without any external LMS at all — this is the fast
  loop for iterating on server-side auth logic.

**`web/node_modules` gotcha:** if `npm run dev` fails with `Cannot find native binding` /
`rolldown-binding.darwin-*.node` errors, it's a corrupted optional-dependency install (a known npm
bug, not a repo issue) — `rm -rf web/node_modules && (cd web && npm ci)` fixes it without touching
the tracked `package-lock.json`.

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

**HTTPS is required, not optional, for a real deployment.** LTI 1.3 mandates HTTPS launch URLs — an
LMS will refuse to complete a launch against a plain-HTTP `CEF_APP_BASE_URL`. Put a TLS-terminating
reverse proxy (Caddy and Traefik both provision Let's Encrypt certificates automatically; a cloud
load balancer works too) in front of the `web` container — this compose file does not set up HTTPS
itself. Once you do, set `CEF_FORCE_SECURE_COOKIES=true` in your `.env` so session cookies get the
`Secure` flag — it's off by default only so the bare `docker compose up` in Local Development above
still works without one. The key used to sign session cookies is auto-generated on first boot and
persisted to `.session-secret` inside the tenant volume (a restart won't log everyone out); set
`CEF_SESSION_SECRET` yourself if you'd rather pin it explicitly.

## Staff console

Anyone who launches CEF via LTI with an Instructor or Administrator role lands directly on
`/staff/` instead of the student app — no separate setup, invite, or password (see
[docs/adr/0007](docs/adr/0007-staff-console-via-lti-roles.md)). It shows, per student who has ever
launched the tool: when they first launched and when they were last active — nothing about their
calendar, uploaded sources, or chat history. A "Reset session" button forces that student to
relaunch from your course to get back in — useful if a student's access looks stuck or you suspect
a shared/lost device. There is currently no roster of students who *haven't* launched the tool yet.

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

* **No full class roster.** The staff console (see "Staff console" above) only lists students who
  have already launched the tool at least once — there's no way to see students who are enrolled
  but haven't used it yet. Would need LTI's Names and Role Provisioning Service (NRPS), deferred —
  see [docs/adr/0007](docs/adr/0007-staff-console-via-lti-roles.md)'s "Alternatives Considered".
* **No LTI Deep Linking.** Adding CEF to a course today means an LMS admin/instructor manually
  pastes the launch URL as an external tool link; there's no "search and insert" content-picker
  experience. A small, additive enhancement if wanted later.
* **Recovery is re-launching via the LMS, not a standalone flow.** This is by design (see
  [docs/adr/0006](docs/adr/0006-lti-1.3-only-auth.md) point 6) — there's no direct, non-LMS-embedded
  way to access the app at all, so "lost my session" and "how do I even get in" are the same
  question with the same answer: go back to the course link.
