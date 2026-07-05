# Google Play Store Deployment Plan

Publisher: **Borinquen Terrier LLC**
Package: `com.borinquenterrier.cef`

> **Not legal advice.** The disclaimer draft in the shared prerequisites below is a starting point to bring to an actual lawyer before publishing — LLC liability and app store agreements are worth a short paid consult, not a guess.

## Start here — shared with the App Store plan

Do these once; both platform plans assume they're in progress or done. See `DEPLOYMENT_IOS.md` for the iOS-specific plan.

1. **Confirm your Apple account type** (Individual vs Organization) — needed for iOS, not Android, but listed here since it's a shared prerequisite.
2. **Enroll in Google Play Console** (play.google.com/console/signup) as an **Organization** account, $25 one-time, with Borinquen Terrier LLC's business details. ✅ **DONE** — enrolled.
3. **Draft and host a Privacy Policy + Terms of Service.** ✅ **DONE 2026-07-04** — live at `https://borinquenterrier.com/cef-privacy-policy` and `https://borinquenterrier.com/cef-terms-of-service` (CEF-specific pages, distinct from Borinquen Terrier's other product's `/privacy-policy`/`/terms-and-conditions` — verified content covers the actual Google Calendar scope, Gemini API/BYOK data flow, and local-only storage, not boilerplate).
4. **Move the Google OAuth consent screen from Testing to Production.** ✅ **DONE 2026-07-04.** Publishing status confirmed "In production." Branding verified (app domain now points at the dedicated `/college-executive-function` page — see Phase 1 below) and full OAuth verification submitted to Google for the `calendar` scope (scope justification + demo video at `https://youtu.be/biU8Vohe0lU` recorded from the Desktop client). Status: **under review**. The app only requests the `calendar` scope — the Drive scope was dropped entirely in favor of native OS file pickers (Android's Storage Access Framework already browses into Drive with no Drive API calls, no OAuth scope needed). `calendar` is a "sensitive" scope requiring standard Google review, not the CASA-tier security assessment a "restricted" scope like `drive.readonly` would need.
5. **Use one publisher name everywhere** — "Borinquen Terrier LLC" should match exactly across App Store Connect's Business/Agreements page and Play Console's org profile.

### Draft AI-disclaimer clause for the Terms of Service

Starter draft — have a lawyer review before publishing:

> **AI-Generated Content.** College Executive Function uses artificial intelligence (including third-party models such as Google's Gemini) to extract, generate, and organize academic deadlines, calendar events, and study plans from documents and calendars you provide. AI-generated content may be incomplete, inaccurate, or out of date.
>
> You are solely responsible for verifying all dates, deadlines, and academic requirements against your official course syllabus, institution, and instructors before relying on them. Borinquen Terrier LLC provides the application "as is" and, to the fullest extent permitted by law, disclaims all liability for missed deadlines, academic consequences, or other damages arising from reliance on AI-generated or AI-assisted content.

---

## Phase 1 — Account

- [x] **Enroll in Google Play Console as an Organization.** ✅ **DONE** — enrolled with Borinquen Terrier LLC's business verification details, $25 one-time — same enrollment listed in Start Here above.

## Phase 2 — App readiness (code) — hard blockers

- [x] **Add a launcher icon.** ✅ Already done in commit `ee272b0` — `mipmap-*` and adaptive-icon resources exist and `AndroidManifest.xml` sets `android:icon`/`android:roundIcon`. (This item was stale; the icon landed alongside the iOS/desktop/web branding pass.)
- [ ] **Fix the hardcoded `versionCode`.** It's fixed at `1` in `androidApp/build.gradle.kts`. Play rejects a re-upload with a duplicate version code, so an incrementing strategy is needed before the second release — tying it into `release.sh` alongside `versionName` is the natural place.
- [x] **Signed release build** — ready. The keystore and env-var-first signing config (`CEF_KEYSTORE_PATH` etc.) are already wired up. Confirm `bundleRelease` produces a signed `.aab` (Play requires App Bundles, not APKs) once the versionCode fix above lands.

## Phase 2.5 — Hardening pass (blocks submission)

**Gate: none of Phase 4 starts until every item below is checked.** These are cross-platform
(shared `composeApp`/KMP code, so they apply to Android, iOS, and desktop alike) correctness and
reliability fixes identified in ROADMAP.md's "Phase 10 — Hardening Pass" post-feature-complete audit — shipping a store
listing before they land means putting real bugs and an unmeasured AI pipeline in front of
reviewers and new users at once. See ROADMAP.md's Phase 10 section for full write-ups, acceptance criteria, and
file references; check items off here as they land there.

- [x] **HARD-1** — ✅ **DONE 2026-07-04.** Fail loud (not silent) when telemetry degrades to a no-op tracer in a packaged/release build.
- [x] **HARD-2** — Fix the hardcoded Fall-2024 default date range in `AddRoutineItemDialog` (recurrences silently never fire).
- [x] **HARD-3** — ✅ **DONE 2026-07-04.** Surface a persistent `LOCAL_ONLY` sync failure on event update instead of swallowing it.
- [x] **HARD-4** — ✅ **DONE 2026-07-05.** Add real pass/fail thresholds to `SyllabusEvaluationIntegrationTest` (today it only prints, never fails).
- [x] **HARD-5** — Wire the real corpus evals into CI as an actual gate (depends on HARD-4 landing first).
- [x] **HARD-6** — Cap desktop `debug_logs.txt` growth in packaged release builds.
- [x] **HARD-7** — ✅ **DONE 2026-07-04.** Wire up the already-built override-log retention (`pruneOldLogs`) into a recurring entry point.
- [x] **HARD-8** — ✅ **DONE 2026-07-05.** Warn before a document is large enough to trigger extra Gemini cost.
- [x] **HARD-9** — ✅ **DONE 2026-07-05.** Full teardown (or an explicit, honest retention choice) on Google account disconnect — decision: disconnect touches zero local event data (see ROADMAP.md); the gap closed was the missing confirmation dialog and honest messaging, not a data change.

## Phase 3 — Play Console listing

- [ ] **Store graphics.** Hi-res icon (512×512), feature graphic (1024×500), and phone + tablet screenshots — the in-app launcher icon (Phase 2) is separate from these store-listing graphics, which still need to be produced.
- [ ] **Content rating (IARC) & target audience.** Answer the questionnaire, set target audience to adult/college-age (not child-directed).
- [ ] **Data Safety section.** Declare what's collected/shared — should mirror the App Privacy label filled in for iOS and the privacy policy itself. Ads, IAP, government/financial/health content: all "No" given the app is free with no monetization. **Depends on HARD-9 (Phase 2.5) being resolved first.**

## Phase 4 — Testing & release

- [ ] **Confirm Phase 2.5 (Hardening pass) is fully checked off.** Do not upload/promote until it is.
- [ ] **Check whether a closed-testing period is required first.** Play's newer policy can require 12 testers for 14 days on a testing track before opening Production, mainly for new personal developer accounts — verify whether this applies to the Organization account in Console before planning a release date.
- [ ] **Upload & promote to Production.** Upload the signed `.aab` to a testing track, then promote once satisfied.
- [ ] **Note the Drive app dependency for testers/reviewers.** Importing from Google Drive goes through Android's native file picker now, not an in-app browser — it only shows Drive as a location if the Drive app is installed on the device. Mention this in any tester/review notes so it isn't mistaken for a missing feature.

## Explicitly not addressed here

- R8/ProGuard minification — deliberately left off; the app is small and OSS, so obfuscation/size-shrinking benefits don't apply, and turning it on for the first time risks silently stripping reflection-reached code for no real gain.
- `versionCode` hardcoded to `1` beyond the fix above — no active Play Store release cadence yet that would be blocked by a manual bump in the meantime.
- Migrating `GoogleAuthActivity`'s legacy `startActivityForResult` to the modern Activity Result API, and migrating off `GoogleSignInClient`/`GoogleAuthUtil` toward Credential Manager — real modernization work, out of scope for a submission-readiness pass.
