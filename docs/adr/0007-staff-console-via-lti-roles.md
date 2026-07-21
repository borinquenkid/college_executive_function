# ADR 0007: Minimal Staff Console, Authenticated via LTI Roles

## Status
Accepted

Builds directly on [ADR 0006](0006-lti-1.3-only-auth.md) — this only exists because LTI launches
already carry a roles claim; without ADR 0006's LTI-only auth model, this would need its own
credential system.

## Context
Disability-services offices need some way to see that the tool is actually being used and to
unblock a student who's locked out — but explicitly do **not** need (and per the tool's own privacy
posture, should not have) visibility into any individual student's calendar, uploaded sources, or
chat history. Before this, there was zero concept of a non-student user anywhere in the codebase —
every route funneled through the same single-tenant `resolveContainer(call)` choke point.

## Decision
1. **No separate staff credential store.** An LTI launch's roles claim (`isStaffRole()`, a
   substring match on "Instructor"/"Administrator" IMS role URIs — see `lti/LtiRoles.kt`) decides
   staff access directly. `LtiLaunchHandler` mints a `StaffSession` cookie alongside the ordinary
   `UserSession` whenever a launch's roles qualify — instructors keep their own app instance too,
   they're not routed away from it.
2. **`DirectoryDatabase`** (server-wide, `<tenantBaseDir>/_directory.db`, outside the per-tenant
   hash-partitioned tree) tracks `student_id`, `created_at`, `is_staff`, and `session_epoch` for
   every studentId that has ever launched. This is the first thing in the codebase that needs to
   *enumerate* students rather than look one up by an already-known id — hence introducing it now
   rather than earlier.
3. **`GET /api/staff/students`** returns only `studentId`, `createdAtMillis`, `lastActiveMillis` —
   deliberately excludes anything else. `lastActiveMillis` is computed lazily by stat'ing the
   tenant's `.db`/`.db-wal`/`.db-shm` files at request time (tenant DBs run in WAL mode, so a plain
   `.db` mtime alone reads stale for an active tenant) rather than written on every API request,
   which would add a write to the hot path of *every* request just to power an admin view.
4. **`POST /api/staff/students/{id}/reset-session`** is the "force-reset" mechanism, and it needed
   solving a real problem: session cookies are stateless signed tokens, so there was previously no
   way to revoke one server-side. `UserSession` now carries a `session_epoch`, embedded at mint
   time from `DirectoryDatabase.recordLaunch()`'s return value; `resolveStudentId()` rejects any
   session whose epoch doesn't match the tenant's *current* epoch. Reset bumps the epoch, which
   invalidates every outstanding session for that student on their very next request — no
   server-side session store beyond this one integer was needed.
5. **A reset student's way back in is re-launching via the LMS** — the same recovery property ADR
   0006 point 6 already established, so a staff-initiated reset isn't a dead end.
6. **Separate small web bundle, not a route inside `App.tsx`.** No router is installed in `web/`,
   and `App.tsx` is already large; `web/staff/index.html` → `web/src/staff/StaffApp.tsx` is its own
   Vite entry point, reached at `/staff/` (one `nginx.conf` addition: `location /staff/`). This
   keeps student-facing and staff-facing code in genuinely separate bundles rather than gated
   behind client-side role checks in the same one.

## Alternatives Considered
* **Env-var email allowlist + magic-link email invites for staff.** The original design before
  full LTI was adopted. Dropped once ADR 0006 committed to LTI-only: roles are already
  authoritative from the same launch, so a parallel allowlist would just be a second, redundant
  source of truth to keep in sync — and it would have needed its own SMTP dependency ADR 0006's
  LTI re-launch model made unnecessary elsewhere too.
* **A real staff-accounts table with usernames/passwords.** Rejected for the same reason ADR 0005
  rejected student passwords: no password-reset infrastructure exists in this self-hosted,
  ops-light project, and it would duplicate identity that the LMS already manages.
* **Full class roster via LTI's Names and Role Provisioning Service (NRPS)**, so staff could see
  *all* enrolled students, including ones who've never launched the tool. Deferred: NRPS needs its
  own service-authorization (client-credentials JWT) flow beyond a plain launch, and
  "launched-so-far" is enough for v1. Worth revisiting if offices want "% of students who've used
  it" reporting.

## Consequences

### Positive
* Zero new credential system to build, operate, or secure — staff access is a direct consequence
  of something already verified (the LTI launch itself).
* Session revocation now exists at all, which it didn't before this ADR, for either students or
  staff.

### Negative
* A role change on the LMS side (e.g. demoting an Instructor) only takes effect on that person's
  *next* launch — `is_staff` is set once, at first sight, and not updated on subsequent launches
  (see `DirectoryDatabase.recordLaunch`'s docstring). Existing `StaffSession` cookies from before a
  demotion remain valid until they expire or are individually reset.
* No roster of students who haven't launched the tool yet — an office can't currently answer "how
  many of my 40 students have used this."
* `lastActiveMillis` can be `null` or stale if a tenant's db files were moved/deleted outside the
  normal flow (e.g. manual backup restore) — it's a best-effort filesystem signal, not a hard
  guarantee.
