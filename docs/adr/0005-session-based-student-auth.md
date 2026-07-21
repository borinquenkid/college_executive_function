# ADR 0005: Frictionless Session-Based Student Auth

## Status
Accepted

Supersedes the tenant-resolution mechanism described in
[ADR 0002](0002-multi-tenant-docker-path-partitioned-storage.md) — the storage design there
(path-partitioned per-student SQLite, one Docker volume) is unchanged; only *how a request is
attributed to a student* changes.

## Context
Before this change, `Application.kt` resolved every request's tenant from a client-supplied
`X-Student-ID` header, defaulting to `"default"` when absent. This was never actual
authentication — any client could claim to be any student by setting the header. Worse, the React
web client (`web/src/App.tsx`) never sent the header at all, so in practice every browser session
landed in the same shared `"default"` tenant: two students at the same university would read and
write the same data. This was found during a pre-launch audit ahead of promoting the self-hosted
deployment to universities (see `DEPLOYMENT.md`).

Full LMS-embedded auth (LTI 1.3 — OIDC launch + JWT verification, tool registration per
Canvas/Blackboard/Moodle) was considered and explicitly deferred: it's a materially bigger build,
each university's IT/LMS team has to register the tool through their own admin/approval process
before it's usable, and it still doesn't cover direct/standalone access (bookmarks, non-course
use) — some session mechanism is needed regardless of whether LTI is added later.

## Decision
Identity is now a signed, `HttpOnly` session cookie, established with zero user friction — no
signup form, no password:

1. **`POST /api/auth/start`** (rate-limited, 5/min per IP): if the caller already has a valid
   session cookie, no-ops and returns it; otherwise generates a random, unguessable `studentId`
   (`"u-" + 24 random bytes, base64url`), stores it in the session, and returns it. The web client
   calls this once on mount before any other request. This is a deliberate "security by obscurity"
   trade-off: there is no separate password, knowing the token is what grants access. The
   explicit trade-off accepted here is ease of adoption over defense-in-depth identity — reasonable
   for a low-stakes personal productivity tool, not appropriate if this ever stores materially more
   sensitive data.
2. **`Application.kt`'s `resolveStudentId()`** no longer reads `X-Student-ID` at all — it reads the
   verified session and rejects with `401` if none exists. Every route already funneled through a
   single `resolveContainer(call)` choke point, so this was a one-function change, not a
   route-by-route rewrite. `X-Student-ID` is now completely inert (verified by
   `StudentIdRoutingTest` in `ServerContainerFactoryTest.kt` — spoofing it has no effect once a
   session exists, and it grants nothing without one).
3. **Cookies, not headers, close the SSE gap for free.** `useAgentStream.ts` opens a native
   browser `EventSource`, which cannot set custom headers — a header-based auth scheme would have
   needed a separate mechanism for that endpoint. Cookies attach automatically to both `fetch()`
   and `EventSource` on the same origin, so no special-casing was needed.
4. **DoS guardrail:** removing the trusted-header model means `POST /api/auth/start` is the one
   endpoint that can create a tenant (a new SQLite file) with no credentials at all. It's
   rate-limited per-IP via Ktor's `RateLimit` plugin, keyed on the real client IP via the
   `XForwardedHeaders` plugin (nginx already forwards `X-Real-IP`/`X-Forwarded-For` in
   `web/nginx.conf`, so this works out of the box behind the documented docker-compose reverse
   proxy). Upload size was also capped in `MultipartParser.kt` in the same pass, closing the same
   class of unauthenticated-resource-exhaustion risk.
5. **Cookie `Secure` flag defaults off.** DEPLOYMENT.md's documented default deployment
   (`docker compose up`, port 80, no TLS) is plain HTTP — forcing `Secure=true` would silently
   break login for that default path. Set `CEF_FORCE_SECURE_COOKIES=true` once TLS is in front.
6. **Session secret** is read from `CEF_SESSION_SECRET` if set, otherwise generated once and
   persisted to `<tenantBaseDir>/.session-secret` (`SessionSecret.kt`) so a container restart
   doesn't invalidate every student's session.
7. **Recovery is explicitly out of scope for this pass.** Losing the cookie (clearing browser data,
   switching devices) means a fresh, empty tenant — there is no username/password to log back in
   with. A "copy/email yourself your link" affordance was considered and deferred as a fast-follow
   once real students hit the problem, to keep this change small.

## Alternatives Considered
* **Username + password signup.** Rejected for v1: adds signup friction (the opposite of what was
  wanted — "make it easier to adopt, not harder") and a password-reset burden this self-hosted,
  ops-light project has no infrastructure for (no SMTP, no email delivery anywhere in the stack).
* **LTI 1.3 (LMS-embedded launch).** Deferred, not rejected — see Context. Worth building once a
  specific university's LMS team asks for embedded launch; can be added as an additional login
  path alongside the session cookie without reworking this.
* **Invite-code gated signup.** Rejected for v1: adds an admin step (generating/distributing
  codes) that conflicts with `DEPLOYMENT.md`'s "no programming required" IT-staff quick start.

## Consequences

### Positive
* Two students on the same deployment can no longer read or write each other's data — the bug
  this ADR exists to fix.
* Zero-friction adoption: visiting the app is the entire "login" flow.
* `:server:test` is now wired into CI (`.github/workflows/pr-check.yml`), and `web/` gets a build
  check for the first time — neither ran in CI before this change.

### Negative
* No password recovery story yet — losing cookies means starting over. Tracked as a fast-follow.
* "Security by obscurity" is a real, accepted trade-off, not just a figure of speech: anyone who
  obtains a student's session cookie value has full access to that tenant, with no second factor.
  Acceptable for the current low-stakes data model; revisit if that changes.
* `X-Student-ID` header handling code paths in tests/docs referencing the old model needed
  updating (`StudentIdRoutingTest`, `SPEC.md`, `DEPLOYMENT.md`) — done as part of this change.
