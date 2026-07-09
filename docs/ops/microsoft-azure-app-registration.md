# Microsoft Entra App Registration — Setup Runbook

**Status: not started.** Blocks live testing of MS-1 through MS-3 in
[ROADMAP.md Phase 12](../../ROADMAP.md) and is the external prerequisite named in
[ADR-004](../decisions/ADR-004-outlook-microsoft-365-calendar-provider.md) §7. Everything in
this runbook is free — see "Cost summary" at the bottom — this is a setup-time and lead-time
cost, not a billing one.

This is split into two independent parts. **Do Part A now; Part B can wait.** Part A is the
actual OAuth prerequisite — nothing in Phase 12 compiles/runs against real Microsoft accounts
without it. Part B (publisher verification) only removes an "unverified publisher" warning on
the consent screen — polish, not a functional blocker, per ADR-004 §7.

---

## Part A — Core app registration (do this first)

### A.1 — Decide which Entra tenant to use

Publisher verification (Part B) requires the app to be registered under a **Microsoft Entra
work/school tenant**, not a personal Microsoft account. Decide this now even though Part B is
deferred, so Part A doesn't need to be redone later under a different tenant.

- If Borinquen Terrier LLC already has a Microsoft 365 / Entra tenant (e.g. from any existing
  Microsoft subscription), use that one.
- If not, create one for free at [azure.microsoft.com/free](https://azure.microsoft.com/free) —
  signing up for a free Azure account provisions a default Entra (Azure AD) tenant
  automatically. This does **not** require spending the $200/30-day trial credit; the tenant
  itself is free and doesn't expire when the credit does.

### A.2 — Register the app

In the [Microsoft Entra admin center](https://entra.microsoft.com) (or `portal.azure.com` →
Microsoft Entra ID): **Identity → Applications → App registrations → New registration.**

| Field | Value |
|---|---|
| Name | `College Executive Function` (or similar — user-facing on the consent screen) |
| Supported account types | **Accounts in any organizational directory and personal Microsoft accounts** — this is the `common`-tenant setting ADR-004 requires to support both university-issued M365 accounts and personal Outlook.com/Hotmail accounts |
| Redirect URI | Leave blank here — configured per-platform in A.3 |

Record the **Application (client) ID** shown after creation — this becomes `MICROSOFT_CLIENT_ID`.

### A.3 — Configure redirect URIs per platform

**App registration → Authentication → Add a platform.** CEF needs two platform configurations
under this one app registration, matching the JVM-vs-mobile split in ADR-004 §3 / ROADMAP MS-3–5:

1. **Web** platform (for the JVM local-server OAuth flow, mirroring
   `GoogleAuthService.jvm.kt`'s `LocalServerReceiver`):
   - Redirect URI: `http://localhost:<port>/` — use whatever loopback port
     `GoogleAuthService.jvm.kt`'s `LocalServerReceiver` is already configured with, so the
     Microsoft and Google flows don't collide if a developer runs both during testing.
   - This platform type supports a client secret (A.5) — matches Google's existing pattern of an
     embedded, obfuscated secret for the installed-app flow.
2. **Mobile and desktop applications** platform (for Android + iOS, PKCE, no secret):
   - Select the generated/standard redirect URI options offered, or add a custom URI scheme
     redirect (e.g. `msauth.com.borinquenterrier.cef://auth` — exact scheme should match
     whatever `MicrosoftAuthService.android.kt`/`MicrosoftAuthService.ios.kt` end up using for
     their `CustomTabsIntent`/`ASWebAuthenticationSession` callback capture in MS-4/MS-5).
   - This is a **public client** — no secret is generated or embedded for this platform, by
     design (mobile binaries shouldn't carry a client secret at all, unlike JVM's precedent of
     embedding one via `BuildSecrets`).
   - Under **Authentication → Advanced settings**, enable **"Allow public client flows"** — required
     for the PKCE-based mobile flow to work without a secret.

### A.4 — API permissions

**App registration → API permissions → Add a permission → Microsoft Graph → Delegated
permissions → `Calendars.ReadWrite`.** Grant admin consent is not required — this is a
delegated, non-admin-consent scope (ADR-004 Context), so each student consents individually at
sign-in.

`offline_access`, `openid`, and `profile` do **not** need to be added here — they're requested
directly in the OAuth scope parameter at runtime (A.6), not configured as static API permissions.

### A.5 — Client secret (Web/JVM platform only)

**App registration → Certificates & secrets → New client secret.** Note the secret value
immediately — it's only shown once. This becomes `MICROSOFT_CLIENT_SECRET`, used only by the
JVM build path (MS-3), never embedded in the Android or iOS binaries.

### A.6 — Scope string for the auth request

Whatever issues the authorization/token requests (MS-2/MS-3) should request:

```
openid profile offline_access https://graph.microsoft.com/Calendars.ReadWrite
```

`offline_access` must be explicit — Microsoft's v2.0 endpoint won't issue a refresh token
without it (ADR-004 §3, confirmed against Microsoft Graph docs).

### A.7 — Wire secrets into the build (ties to MS-1's acceptance criteria)

Add to the local dev environment (matching `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`'s existing
env → `local.properties` → `.env` priority chain in `generateBuildSecrets`,
`composeApp/build.gradle.kts:29-116`):

```
MICROSOFT_CLIENT_ID=<Application (client) ID from A.2>
MICROSOFT_CLIENT_SECRET=<secret value from A.5>
MICROSOFT_REDIRECT_URI=http://localhost:<port>/   # matches A.3's Web platform entry
```

Once `generateBuildSecrets` is extended to emit these into `BuildSecrets.kt` (MS-1's actual
code task), Part A is complete and MS-2/MS-3 can be built and tested against a real tenant.

---

## Part B — Publisher verification (optional, defer until Phase 12 ships)

Confirmed free of charge per Microsoft's own FAQ ("Microsoft doesn't charge developers for
publisher verification. No license is required.") — the earlier open question about cost is
resolved. What it costs is setup time and a multi-business-day wait, not money.

### B.1 — Add a verified custom domain to the Entra tenant

**Entra admin center → Identity → Settings → Domain names → Add custom domain** →
`borinquenterrier.com` (already Cloudflare-managed, per existing CEF/Oficio ops notes) → add the
TXT record Microsoft provides to the domain's DNS → **Verify**. The app's publisher domain
cannot be the default `*.onmicrosoft.com` domain, so this step is required before B.2.

### B.2 — Set the app's publisher domain

**App registration → Branding & properties → Publisher domain** → set to `borinquenterrier.com`
(now that it's verified in B.1).

### B.3 — Join the Microsoft AI Cloud Partner Program (CPP)

Sign up at [partner.microsoft.com/membership](https://partner.microsoft.com/membership) using an
account/email on the `borinquenterrier.com` domain. Complete business verification — Microsoft's
docs cite up to 5 business days for this step. This CPP account must be (or become) the **Partner
Global Account (PGA)** for the org.

### B.4 — Assign the required roles

- In **Microsoft Entra ID**: the user who initiates verification needs **Application
  Administrator** or **Cloud Application Administrator**.
- In **Partner Center**: that same user (or a paired one) needs **CPP Partner Admin** or
  **Account Admin**.
- That user must sign in using **Microsoft Entra multifactor authentication (MFA)** to perform
  the verification step.

### B.5 — Complete verification

Back in **App registration → Branding & properties**, associate the verified CPP PartnerID with
this app registration and accept the Microsoft identity platform for developers Terms of Use.
Per Microsoft's docs, once B.1–B.4's prerequisites are satisfied, this step itself completes "in
minutes." Confirm the blue **verified** badge now appears on the consent screen.

---

## Sequencing summary

```
A.1 → A.2 → A.3 → A.4 → A.5 → A.6 → A.7   (required — unblocks MS-2/MS-3 live testing)
                                              │
B.1 → B.2 → B.3 (5 business days) → B.4 → B.5   (optional — can start anytime after A.1,
                                                   don't block MS-2..MS-12 on it)
```

## Cost summary

| Item | Cost |
|---|---|
| Entra tenant creation | Free |
| App registration | Free |
| Client secret | Free |
| Microsoft Graph `Calendars.ReadWrite` usage | Free, for both personal and work/school accounts |
| Custom domain verification on the tenant | Free (domain itself already owned/managed) |
| Microsoft AI Cloud Partner Program membership | Free |
| Publisher verification | Free, per Microsoft's own FAQ |

Nothing in this runbook draws on the $200/30-day Azure trial credit or requires an ongoing Azure
subscription with billing enabled.
