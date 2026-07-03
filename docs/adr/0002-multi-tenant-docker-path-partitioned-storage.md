# ADR 0002: Multi-Tenant Deployment via Docker with Path-Partitioned Storage

## Status
Accepted

## Context
The Phase 4 work (`TenantDatabaseFactory`, `TenantConnectionCache`, `TenantSettingsFactory`, `TenantMigrationRunner`, Litestream backup runner) built a full database-per-student sharding layer, but two things were still missing:

1. **No deployment story.** The server had no Dockerfile, no production run instructions, and no documented way to run it as anything other than a single local process (`./gradlew :server:run`) writing to the operator's home directory (`~/.cef/tenants`).
2. **No live routing.** Every HTTP route in `Application.kt` resolved to a single hardcoded `ServerContainer.container` ("default" tenant) — the `X-Student-ID`-based tenant resolution that `ServerContainerFactory.containerFor(studentId)` supports was only exercised directly in tests, never by a real request.

We need a deployment shape that (a) actually wires per-student routing into the live server, and (b) makes the already-implemented per-student SQLite sharding operable in a containerized environment.

## Decision
Deploy the Ktor server and React web client as two Docker images (`server/Dockerfile`, `web/Dockerfile`, wired together in `docker-compose.yml`), with student data stored on a **single mounted volume, partitioned by path** — not one mount per tenant, and not one shared database.

1. **Tenant resolution:** `Application.kt` reads an `X-Student-ID` request header per request (defaulting to `"default"` when absent) and resolves it to a `DependencyContainer` via `ServerContainer.containerFor(studentId)`. The header is validated against `^[A-Za-z0-9_-]{1,128}$` before it ever reaches a file-path-building function — anything else (e.g. `../../../etc/passwd`) gets rejected with `400 Bad Request`. This closes what would otherwise be a path-traversal vector, since `TenantDatabaseFactory`/`TenantConnectionCache` build file paths directly from the studentId.
2. **Path-partitioned storage, one volume:** `TenantDatabaseFactory` already hashes each studentId (MD5) and shards into `baseDir/xx/yy/studentId.db` (first 4 hex chars as a two-level fan-out, avoiding one giant directory). We didn't change this — we just gave it a real home: a single Docker named volume (`tenant-data`) mounted at `/data/tenants` inside the `server` container, with `CEF_TENANT_BASE_DIR=/data/tenants` telling `ServerContainer` to use it instead of the bare-metal default (`~/.cef/tenants`).
3. **Two containers, one compose stack:** `server` (Ktor, fat jar via `shadowJar`) and `web` (the Vite build served by nginx, which also reverse-proxies `/api/*` to `server` — including the SSE stream at `/api/agent/stream`, with `proxy_buffering off` so events aren't batched).
4. **Secrets stay out of the image and out of the tenant-seeding path:** the operator's `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` are passed as container env vars (needed only to refresh already-linked Google tokens). `CEF_GEMINI_API_KEY` is deliberately *not* passed to the server container — each tenant sets their own Gemini key via `POST /api/settings` after the stack is up. `ServerContainerFactory` never auto-imports an operator-level key into a tenant in production (that pattern exists only in test code, see `GeminiLiveIntegrationTest`).

## Alternatives Considered
* **One shared database (Postgres) with a `tenant_id` column.** Rejected: throws away the already-built, already-tested SQLite-per-student sharding and connection-cache work, and adds an operational dependency (a DB server to run/back up) the app didn't need before.
* **One container per tenant.** Rejected: doesn't scale operationally past a handful of students — every signup would mean provisioning a new container, and Docker isn't a multi-tenant scheduler.
* **Ephemeral/no persistent volume (baked into the image or container-local disk).** Rejected: student data would vanish on every redeploy or container restart. The whole point of `TenantMigrationRunner` and the Litestream backup runner is a durable, mountable directory tree — an ephemeral filesystem defeats both.

## Consequences

### Positive
* The multi-tenant code from Phase 4 is now actually live, not just unit-tested in isolation.
* Backing up all tenants is "back up one directory tree" (or point Litestream at it) rather than N separate volumes or a bespoke per-tenant backup job.
* The hash-bucketed path structure (`xx/yy/studentId.db`) keeps any single directory from accumulating thousands of files as the tenant count grows.
* A crafted `X-Student-ID` header can no longer read/write outside the mount.

### Negative
* All tenants' data lives in one Docker volume — a volume-level failure affects every student, not just one. (Litestream replication mitigates this but doesn't eliminate it.)
* The `TenantConnectionCache` bounds *open file handles* (LRU, capacity 1000), not disk usage — a very large tenant count still needs monitoring of the volume's actual size.
* Per-tenant Google Calendar linking isn't solved by this ADR. Today, the web client's Settings page still says "Run the desktop app to authenticate via OAuth, then refresh this page" — the web/server deployment currently assumes a tenant's Google account was already linked elsewhere. A self-serve OAuth flow for web-only tenants is future work.
