# ADR 0008: Self-Serve Google Calendar OAuth for Web Tenants

## Status
Accepted

## Context
Before this change, the web client's Settings page told students to "run the desktop app to
authenticate via OAuth, then refresh this page" — a hard requirement to install and run a second,
platform-specific application just to link Calendar sync from the web. The server had no OAuth
endpoints at all; only `GoogleAuthService.jvm.kt` (desktop) and `GoogleAuthService.android.kt`
implement the interactive authorization flow. For a deployment that's meant to be usable entirely
through a browser (the whole point of `/server` and `/web` existing), this was a real gap, not
just an inconvenience — a disability-services office self-hosting only the web client had no way
to offer Calendar sync at all.

## Decision
1. **A second, separate Google OAuth client is required — this is a hard Google platform
   constraint, not a design choice.** The existing `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` are a
   **Desktop app**-type OAuth client, and Google restricts that client type's redirect URIs to
   loopback/`urn:ietf:wg:oauth:2.0:oob` addresses — it cannot redirect to an arbitrary HTTPS
   domain. A **Web application**-type client (same Google Cloud project, one more credential pair)
   is required, with its authorized redirect URI set to
   `<CEF_APP_BASE_URL>/api/auth/google/callback`. New env vars: `CEF_GOOGLE_WEB_CLIENT_ID`/
   `CEF_GOOGLE_WEB_CLIENT_SECRET` — deliberately not overloading the existing desktop ones, so a
   misconfigured deployment fails loudly (wrong client type rejected by Google) rather than
   silently trying to use a client that can't work for this flow.
2. **`GoogleWebOAuthService`** drives the authorization-code flow over plain HTTP redirects
   (`GET /api/auth/google/start` → Google's consent screen → `GET /api/auth/google/callback`),
   instead of `GoogleAuthService.jvm.kt`'s `AuthorizationCodeInstalledApp`/`LocalServerReceiver`
   (which opens a local browser and a loopback HTTP server — meaningless for a server process).
3. **`state` is bound to the initiating student's session, not just a random CSRF token.**
   `/api/auth/google/start` creates `state = OAuthStateStore.create(studentId)`;
   `/api/auth/google/callback` re-resolves the *current* session's studentId and rejects unless it
   matches the studentId the state was minted for. This closes a real account-linking CSRF: without
   it, an attacker who starts their own OAuth flow (getting a valid `code`+`state` for their own
   Google account) could try to trick a different logged-in student into completing it, linking the
   student's CEF account to the attacker's Google credentials.
4. **Tokens land in the exact same place the desktop/Android flows already write to** —
   `container.tokenRepository.saveTokens(...)` (`GoogleTokenRepository`, shared `commonMain` code)
   — so `GoogleTokenService`, `WebSettingsHandler.handleGetGoogleAuthStatus`, and calendar sync all
   work completely unmodified once tokens land; nothing about how tokens are *used* needed to
   change, only how they get there for a web tenant.
5. **`prompt=consent`** is set on the authorization URL so Google reliably returns a refresh token
   even if this isn't the student's very first authorization against this client — `access_type=
   offline` alone only guarantees one on a genuinely first-time consent.
6. **Google Calendar sync remains optional, unlike LTI.** `GoogleWebOAuthConfig.resolveFromEnv()`
   returns `null` (not a fail-fast error) when `CEF_GOOGLE_WEB_CLIENT_ID`/`SECRET` aren't set — the
   two new routes respond `503` rather than the whole deployment refusing to boot. This mirrors how
   the existing desktop-flow `GOOGLE_CLIENT_ID`/`SECRET` have always been optional.
7. **The real Google token-exchange call is injectable** (`GoogleWebOAuthService`'s
   `tokenExchanger` constructor parameter) so `GoogleWebOAuthIntegrationTest.kt` can exercise the
   whole `/api/auth/google/start` → `/api/auth/google/callback` → `tokenRepository.hasTokens()`
   path without a live call to Google, the same pattern `LtiTestSupport` uses for LTI's JWKS.

## Alternatives Considered
* **Reuse the existing Desktop-app `GOOGLE_CLIENT_ID`/`SECRET` for the web flow.** Not viable —
  Google rejects a non-loopback `redirect_uri` for a Desktop-app-type client outright; this isn't a
  trade-off, it's a hard platform restriction.
* **Client-side OAuth (a JS library driving the flow directly from the browser).** Rejected: would
  need the client secret exposed to the browser (or a proxy endpoint anyway), and Google's
  server-side authorization-code flow is the standard, more secure pattern for a flow that already
  has a backend to receive the callback.
* **Skip account-linking CSRF protection (treat `state` as opaque, not identity-bound).** Rejected
  as a real, exploitable gap for the reason in point 3 — the extra state-to-studentId binding is a
  handful of lines and directly closes it.

## Consequences

### Positive
* Calendar sync is now reachable entirely from the browser — no desktop app install required for a
  purely web-hosted deployment, closing the gap this ADR exists to fix.
* One more one-time, bounded IT/institution task (registering a second OAuth client) in the same
  category as LTI registration (docs/adr/0006) — not a new category of friction for this audience.

### Negative
* Institutions now need to create and manage *two* Google OAuth clients instead of one, if they
  want both desktop app support and web-based linking — slightly more setup surface.
* `GoogleWebOAuthService`'s `MemoryDataStoreFactory` means the underlying `GoogleAuthorizationCodeFlow`
  object holds no state between the `/start` and `/callback` requests (a fresh flow is built for
  each) — this is fine because CEF's own `GoogleTokenRepository`/`OAuthStateStore` are the actual
  source of truth, not the flow object, but it's a deliberate divergence from the "normal" way this
  library is typically used (where the flow's own credential store persists things) worth noting
  for future readers of `GoogleWebOAuthService`.
