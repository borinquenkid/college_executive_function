# ADR-004: Outlook/Microsoft 365 as a Second Calendar Provider

## Status
Proposed

## Date
2026-07-09

## Context

CEF's Core Architecture section (`AGENTS.md:271`) has always named Microsoft Outlook alongside
Google Calendar and iCal as an intended external-calendar source. It has never been built — CEF
today syncs to exactly one provider, Google Calendar, and the assumption that Google is the only
remote provider is structural, not incidental:

- `StudyPreferences` (`StudyPreferences.kt:16-17`) hardcodes `googleCalendarId`/`googleCalendarName`
  as the single calendar target — no per-provider schema.
- `CalendarSyncManager` takes an `isGoogleLinked: Boolean` flag (`CalendarSyncManager.kt:14`), not
  a provider enum.
- `DependencyContainer.kt:65-81` is the sole wiring site: it constructs one concrete
  `GoogleRemoteCalendarRepository` and injects it into `CalendarAgent`. There is no factory or
  registry a second provider could plug into.
- `GoogleAuthService`, `GoogleAccountFlow`, `GoogleTokenRepository`, `CalendarIdResolver`,
  `GoogleCalendarPanel`, `GoogleCalendarSelector` are all Google-concrete classes, not
  implementations of a generic interface. The one interface that *is* provider-agnostic today is
  `RemoteCalendarRepository` (`CalendarInterfaces.kt:104-114`) — `GoogleRemoteCalendarRepository`
  is its only implementation.

A sister project (Oficio, an unrelated HVAC-contractor SaaS also owned by Borinquen Terrier LLC)
recently wrote and parked ADR-0023 on the identical question for its own codebase, concluding
Outlook support wasn't worth building yet **for that product's audience** — SMB trade operators,
where market data (Litmus/DemandSage 2026) shows Gmail dominance and no real signups existed yet
to justify the cost.

That conclusion does not transfer to CEF. CEF's audience is college students, not SMB trade
operators, and university email is disproportionately Microsoft 365 — many institutions issue
students an `@university.edu` mailbox hosted on Exchange Online/M365, and students who don't use
their school account for calendaring commonly have a personal Outlook.com/Hotmail account instead
of Gmail. A student on Outlook cannot self-serve into CEF's calendar sync today without either
avoiding calendar sync entirely or creating a Gmail account solely to use CEF. Given AGENTS.md
already named this as intended scope, this is a real, named gap being closed, not new speculative
scope.

**What Oficio's ADR-0023 got right that transfers directly (verified against Microsoft Graph docs
as of 2026-07-09, not re-verified here — see that ADR for citations):**
- `common` tenant endpoint (`https://login.microsoftonline.com/common/...`) supports both
  work/school M365 accounts and personal Outlook.com/Hotmail accounts under one app registration.
  `organizations`-only would exclude exactly the personal-account students this ADR exists to
  serve.
- `offline_access` must be requested explicitly to get a refresh token — Microsoft's v2.0 endpoint
  differs from Google's `access_type=offline&prompt=consent` here; this is provider-specific
  mechanics, not shared code.
- Delegated `Calendars.ReadWrite` does not require admin consent — a student can complete the OAuth
  consent screen themselves, no university IT department in the loop, on either a personal or a
  work/school account.

**What does not transfer, because CEF's problem shape is different from Oficio's:**
Oficio needed OAuth + write-a-booking + compute-free/busy-for-scheduling (a booking tool), which is
why its design split into three narrow interfaces (`CalendarOAuthService`/`CalendarEventWriter`/
`CalendarAvailabilityService`) dispatched per-request by a multi-tenant token resolver. CEF needs
full two-way sync — create/read/update/delete a student's own events, reconciled against local
state — which is exactly what `RemoteCalendarRepository` and `GoogleRemoteCalendarRepository`
already do for Google. CEF is also a single-user local app (one `Settings` store per install, no
multi-tenant database), so Oficio's `CompositeCalendarTokenResolver` — built to answer "which
provider did *this* tenant, out of many, connect?" on every request — has no equivalent problem to
solve here. The provider a student connected is resolved once, at app wiring/startup, not
per-request.

## Decision

Build a Microsoft Graph implementation of `RemoteCalendarRepository`, plus the provider-selection
mechanism that has never existed because there was never a second provider, following CEF's
existing Google pattern class-for-class rather than Oficio's multi-tenant shape.

### 1. `MicrosoftCalendarSyncService` — low-level REST client

Mirrors `GoogleCalendarSyncService.kt` exactly: hand-rolled `ktor-client-cio` calls (**no
Microsoft Graph SDK, no MSAL** — CEF has never taken a vendor SDK dependency for a calendar
provider, and Oficio independently arrived at and validated the same no-SDK approach for the same
API), against `https://graph.microsoft.com/v1.0`:

- List/create calendars: `GET/POST /me/calendars` (same shape as Google's `calendarList`/
  `calendars`, feeds `CalendarIdResolver`'s find-or-create logic unchanged in spirit).
- Event CRUD: `GET/POST /me/calendars/{id}/events`, `PATCH/DELETE /me/events/{id}` — the two-way
  sync surface `GoogleRemoteCalendarRepository` already exercises for Google, so
  `CalendarSyncTest`'s four mutation scenarios (`ADR` — see `CalendarSyncTest`) become the
  acceptance bar for the Microsoft path too.
- Own `MicrosoftEvent`/`MicrosoftCalendarItem` DTOs (`kotlinx.serialization`), own
  `toCalendarException` mapping from Graph's error shape — Google's DTOs and error mapping are
  not reused, matching the existing precedent that each provider's REST client owns its own wire
  format.

### 2. `MicrosoftRemoteCalendarRepository` — facade implementing `RemoteCalendarRepository`

Mirrors `GoogleRemoteCalendarRepository.kt` structurally: delegates to `MicrosoftCalendarSyncService`
and a new `MicrosoftCalendarIdResolver` (a straight port of `CalendarIdResolver.kt`, retargeted at
`MicrosoftCalendarSyncService` and new `microsoftCalendarId`/`microsoftCalendarName` fields on
`StudyPreferences`, following the existing `googleCalendarId`/`googleCalendarName` field pattern
rather than generalizing `StudyPreferences` into a provider map). Reuses `EventQueryService` and
`EventRangeFilter` as-is — both are already provider-agnostic pure logic operating on
`getAllEvents`/date-range/sync-status, exactly as `GoogleRemoteCalendarRepository` uses them today.

**Not carried forward:** `GoogleRemoteCalendarRepository` constructor-injects an `EventConflictDetector`
that is never actually called in the class body (`GoogleRemoteCalendarRepository.kt:12`, confirmed
dead). `MicrosoftRemoteCalendarRepository` should not repeat that dead dependency — fixing the
existing Google instance is out of scope for this ADR, but this ADR should not propagate the same
mistake into new code.

### 3. Provider-agnostic OAuth boundary

Today's `GoogleAuthService`/`GoogleAccountFlow`/`GoogleTokenRepository` are Google-concrete with no
shared interface — `GoogleAuthService` is declared `expect class GoogleAuthService(...)`, not
`expect class AuthService`. Rather than retrofit a generic interface onto the existing Google
classes (a real refactor, unnecessary for shipping a second provider, and risking regressions in
working Google auth), add a parallel, independently-testable set:

- `expect class MicrosoftAuthService(settings, appEnv)` with the same `login()`/
  `refreshAccessToken()`/`logout()` shape as `GoogleAuthService`.
- `MicrosoftTokenRepository` mirrors `GoogleTokenRepository`, storing
  `MICROSOFT_ACCESS_TOKEN`/`MICROSOFT_REFRESH_TOKEN` in the same `Settings` store (not SQLDelight,
  not a file — matching the existing precedent exactly).
- `MicrosoftAccountFlow` mirrors `GoogleAccountFlow`'s FSA (Unlinked/Connecting/Linked/Error),
  constructor-injecting `MicrosoftAuthService`/`MicrosoftTokenRepository`/
  `MicrosoftCalendarSyncService` directly, same as Google's does.

**Per-platform auth UI — one real divergence from the Google precedent.** Google's Android path uses
the native `GoogleSignIn`/`GoogleAuthUtil` SDK (`GoogleAuthService.android.kt:12-34`), which has no
Microsoft equivalent under the "no SDK" constraint from Decision §1. Microsoft's Android and iOS
paths should both use a browser-based authorization-code + PKCE flow — Android via `CustomTabsIntent`
capturing the redirect, iOS reusing the exact `ASWebAuthenticationSession` + PKCE + manual
`OAuthExchange` pattern CEF already proved out for Google on iOS (`GoogleAuthService.ios.kt:46-130`).
JVM mirrors Google's `AuthorizationCodeInstalledApp`/`LocalServerReceiver` local-server flow, adapted
to Microsoft's authorize/token URLs. This means Android's Microsoft implementation is closer in
shape to CEF's existing iOS Google implementation than to CEF's existing Android Google
implementation — worth flagging explicitly so it isn't built by copy-pasting the wrong platform
file.

### 4. Provider selection: resolved once at wiring time, not per-request

Because CEF is single-user/single-install (unlike Oficio's multi-tenant server), there is no
per-request "which provider does this tenant use" question to answer — only "which provider, if
any, has this install connected." `DependencyContainer.kt` gains a `CalendarProvider` enum
(`NONE`/`GOOGLE`/`MICROSOFT`) resolved at startup by checking `GoogleTokenRepository`/
`MicrosoftTokenRepository` for a stored token (mirroring Oficio's "exactly one provider connected"
invariant, but checked once, not per-call). The active `RemoteCalendarRepository` — Google's or
Microsoft's — is constructed and injected into `CalendarAgent` based on that result.
`CalendarSyncManager`'s `isGoogleLinked: Boolean` becomes `connectedProvider: CalendarProvider?`, a
mechanical rename/retype at its one call site, same category of change as Oficio's OC-1 pure
refactor.

A student switching providers (disconnect Google, connect Microsoft, or vice versa) is a
disconnect-then-reconnect flow, not simultaneous dual-provider sync — no student needs both at
once, matching Oficio's identical "one-way choice, switching out of scope" call in ADR-0023.

### 5. UI: a single "Calendar & Drive" card with a two-button picker, not two independent cards

Today `GoogleCalendarPanel.kt:92-118` owns its own "not connected" branch — it renders "Connect
Google Account" itself whenever `!isGoogleLinked`. Naively adding a sibling
`MicrosoftCalendarPanel` with the same self-contained shape would render **two full cards, each
with its own connect button, simultaneously** whenever nothing is connected — technically
functional, but it visually implies both could be connected at once, which the one-provider-at-a-
time invariant (Decision §4) says is never true.

Instead, "not connected" becomes a state owned one level up, not by either panel:

- **`connectedProvider == null`:** one "Calendar & Drive" card renders both "Connect Google
  Account" and "Connect Outlook Account" buttons, stacked. Neither `GoogleCalendarPanel` nor
  `MicrosoftCalendarPanel` renders at all in this state.
- **`connectedProvider == GOOGLE`:** only `GoogleCalendarPanel` renders (selector + disconnect,
  same as today) — no Outlook button anywhere on screen.
- **`connectedProvider == MICROSOFT`:** only `MicrosoftCalendarPanel` renders (mirrored shape) —
  no Google button anywhere on screen.

Switching providers surfaces naturally from this: disconnecting (Decision in MS-11/HARD-9 parity)
returns `connectedProvider` to `null`, which brings back the two-button picker — there is no
separate "switch provider" affordance to build.

This requires trimming `GoogleCalendarPanel`'s existing "not linked" connect-button branch
(`GoogleCalendarPanel.kt:92-109`) out into the new parent-level picker, since that responsibility
moves up a level — a small, deliberate change to already-shipped Google code, not purely additive,
called out explicitly rather than left implicit. `MicrosoftCalendarPanel`/`MicrosoftCalendarSelector`
are new composables mirroring `GoogleCalendarPanel.kt`/`GoogleCalendarSelector.kt`'s *linked-state*
shape only. `CalendarDisplayName.kt` needs no change — it's already provider-agnostic pure string
logic.

### 6. Client secrets

Extend the existing `generateBuildSecrets` Gradle task (`composeApp/build.gradle.kts:29-116`) with
`MICROSOFT_CLIENT_ID`/`MICROSOFT_CLIENT_SECRET`, following the exact same env → local.properties →
`.env` → obfuscated `BuildSecrets.kt` priority chain already built for Google. `MicrosoftAuthService.jvm.kt`
reads secrets in the same priority order as `GoogleAuthService.jvm.kt:140-197`.

### 7. Azure AD app registration (external, non-code prerequisite)

A multi-tenant Azure AD app registration is needed before any OAuth call can succeed, same
external-dependency category as Google's own `client_secret.json` setup. Unlike Oficio's B2B SaaS
context, CEF is a free consumer app requesting only delegated, non-admin-consent scopes
(`Calendars.ReadWrite`) — an "unverified publisher" warning on the consent screen (if Microsoft
Partner Center publisher verification isn't completed) is a UX wart students can click through, not
a functional blocker, unlike Oficio's B2B context where an unverified badge could plausibly cost a
sale. Worth doing eventually for polish; not a blocking prerequisite for shipping.

## Alternatives Considered

### Read-only Outlook feed via `.ics` export URL (reusing `IcsCalendarSource`)
- **Pros:** Zero new OAuth surface — CEF already parses `.ics` URLs (`IcsCalendarSource.kt`).
  Outlook.com and Office 365 both expose a per-calendar "publish as ICS" URL.
- **Cons:** Read-only, no push-back to Outlook — a materially worse experience than Google gets
  today (full two-way sync, `CalendarSyncTest`'s four mutation scenarios). Also requires the
  student to manually find and paste their calendar's publish URL, a worse UX than an OAuth "Connect"
  button. Matches the literal (stale) wording in `AGENTS.md:271` ("Read-only feeds..."), but that
  wording predates Google's two-way sync work and was never revisited.
- **Rejected:** would ship Outlook users a second-class experience for no real engineering savings
  once OAuth is being built anyway — the OAuth+Graph work is the expensive part regardless of
  whether write support is included.

### Generalize `RemoteCalendarRepository`'s dependencies (`CalendarIdResolver`, auth classes) into shared provider-agnostic interfaces before adding Microsoft
- **Pros:** Would avoid the class-for-class duplication in Decision §1–3 (two `*CalendarIdResolver`s
  instead of one generic one, two `*AuthService`s instead of one).
  - **Cons:** `GoogleCalendarSyncService`/`CalendarIdResolver`/`GoogleAuthService` are all in active
  production use; retrofitting a generic interface onto them risks regressing working Google sync
  as a side effect of adding a feature that doesn't need it. `RemoteCalendarRepository` itself is
  already the one interface that needed to be provider-agnostic, and already is.
- **Rejected for this pass:** premature generalization. If a third provider is ever added, the
  duplication between two mirrored implementations becomes a real signal for what to extract —
  extracting it now, from one data point, is guessing at the right seam.

## Consequences

### Positive
- Closes a gap CEF's own architecture doc has named since before this ADR, for the audience most
  likely to actually need it (college students on university-issued or personal Microsoft
  accounts).
- `EventQueryService`, `EventRangeFilter`, `CalendarDisplayName`, `Event` (provider-neutral sealed
  interface, no Google-specific fields per `Event.kt:54-77`) all require zero changes — validates
  that `RemoteCalendarRepository`'s existing boundary was already the right shape for a second
  provider.
- Reuses Oficio's independently-verified Microsoft Graph research (`common` tenant, `offline_access`
  requirement, no-admin-consent delegated scope) instead of re-deriving it from scratch.
- No new SDK dependency category, consistent with CEF's existing Google implementation and Oficio's
  independent validation of the same choice.

### Negative / accepted tradeoffs
- Real duplication: `MicrosoftCalendarSyncService`/`MicrosoftRemoteCalendarRepository`/
  `MicrosoftCalendarIdResolver`/`MicrosoftAuthService`/`MicrosoftAccountFlow` are new files mirroring
  existing Google files, not shared implementations — a deliberate choice (see Alternatives) that
  should be revisited only if a third provider ever makes the pattern repeat a third time.
- Android's Microsoft auth flow is structurally different from Android's Google auth flow (browser+
  PKCE vs. native SDK) — a genuine platform-specific complexity increase, not a mechanical port.
- `StudyPreferences` grows a second pair of calendar-id/name fields rather than a clean
  provider-keyed map; acceptable duplication at two providers, would need revisiting at three.
- Azure AD app registration is a real external setup step with its own lead time (though, per
  Decision §7, not launch-blocking the way Oficio's B2B publisher-verification concern was).

### Out of scope (this ADR)
- **Simultaneous dual-provider sync for one student.** One connected provider at a time; switching
  is disconnect-then-reconnect.
- **Fixing `GoogleRemoteCalendarRepository`'s unused `EventConflictDetector` injection.** Noted in
  Decision §2 as a pre-existing issue not to repeat in new code, not something this ADR fixes in the
  existing Google path.
- **iCloud/CalDAV** — no modern OAuth story, not requested, not evaluated here.
- **Generalizing `RemoteCalendarRepository`'s auth/id-resolver dependencies into shared interfaces**
  — see Alternatives; deferred until a third provider makes the seam obvious.

## Relationship to Other ADRs / Documents
- **Oficio ADR-0023** (`Outlook/Microsoft 365 as an Optional Calendar Provider`, parked
  2026-07-09) — this ADR is the CEF-specific counterpart to that design, reusing its verified
  Microsoft Graph research (`common` tenant, `offline_access`, delegated-scope no-admin-consent
  finding) while diverging on architecture because CEF's sync shape (full two-way, single-user,
  `RemoteCalendarRepository`) differs from Oficio's (write + free/busy, multi-tenant, three split
  interfaces). Oficio's ADR stays parked for its own audience; this ADR is not blocked by that
  status.
- **`ROADMAP.md`'s "iOS native Calendar (EventKit)" idea** (raised 2026-07-05, not yet scoped) —
  a separate, still-deferred idea to introduce a `CalendarProvider` abstraction so iOS writes to
  EventKit instead of Google's REST API directly. If both this ADR and that idea are eventually
  built, `CalendarProvider` naming should be reconciled (this ADR's enum is "which remote *account*
  connected," that idea's is "which local OS calendar API to write through" — related but distinct
  axes) rather than assumed to be the same concept.
