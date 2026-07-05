# Apple App Store Deployment Plan

Publisher: **Borinquen Terrier LLC**
Bundle ID: `com.borinquenterrier.cef` (matches Android — resolved 2026-07-04, see Phase 1)

> **Not legal advice.** The disclaimer draft in the shared prerequisites below is a starting point to bring to an actual lawyer before publishing — LLC liability and app store agreements are worth a short paid consult, not a guess.

## Start here — shared with the Play Store plan

Do these once; both platform plans assume they're in progress or done. See `DEPLOYMENT_ANDROID.md` for the Android-specific plan.

1. **Confirm your Apple account type.** developer.apple.com/account → check if it says *Individual* or shows Borinquen Terrier LLC as an *Organization*. An Individual account can't convert to an Organization after the fact — it requires a brand-new enrollment with a D-U-N-S number for the LLC.
2. **Enroll in Google Play Console** (play.google.com/console/signup) as an **Organization** account, $25 one-time, with Borinquen Terrier LLC's business details. ✅ **DONE** — enrolled. (Needed for the Android plan, not iOS — listed here since it's a shared prerequisite.)
3. **Draft and host a Privacy Policy + Terms of Service.** ✅ **DONE 2026-07-04** — live at `https://borinquenterrier.com/cef-privacy-policy` and `https://borinquenterrier.com/cef-terms-of-service` (CEF-specific pages, distinct from Borinquen Terrier's other product's `/privacy-policy`/`/terms-and-conditions` — verified content covers the actual Google Calendar scope, Gemini API/BYOK data flow, and local-only storage, not boilerplate).
4. **Move the Google OAuth consent screen from Testing to Production.** ✅ **DONE 2026-07-04.** Publishing status confirmed "In production." Branding verified (app domain now points at the dedicated `/college-executive-function` page — see Phase 1 below) and full OAuth verification submitted to Google for the `calendar` scope (scope justification + demo video at `https://youtu.be/biU8Vohe0lU` recorded from the Desktop client). Status: **under review**. The app only requests the `calendar` scope — the Drive scope was dropped entirely in favor of native OS file pickers (no Drive API calls, no OAuth scope needed). `calendar` is a "sensitive" scope requiring standard Google review, not the CASA-tier security assessment a "restricted" scope like `drive.readonly` would need.
5. **Use one publisher name everywhere** — "Borinquen Terrier LLC" should match exactly across App Store Connect's Business/Agreements page and Play Console's org profile.

### Draft AI-disclaimer clause for the Terms of Service

Starter draft — have a lawyer review before publishing:

> **AI-Generated Content.** College Executive Function uses artificial intelligence (including third-party models such as Google's Gemini) to extract, generate, and organize academic deadlines, calendar events, and study plans from documents and calendars you provide. AI-generated content may be incomplete, inaccurate, or out of date.
>
> You are solely responsible for verifying all dates, deadlines, and academic requirements against your official course syllabus, institution, and instructors before relying on them. Borinquen Terrier LLC provides the application "as is" and, to the fullest extent permitted by law, disclaims all liability for missed deadlines, academic consequences, or other damages arising from reliance on AI-generated or AI-assisted content.

---

## Phase 1 — Accounts & identity

- [x] **Resolve which team is your production team.** ✅ **DONE 2026-07-04.** `F4GSKN4DLP` confirmed as Borinquen Terrier LLC's paid Company account (Xcode's cached team list shows `isFreeProvisioningTeam = false`, `teamType = Company`, once the BT Apple ID was signed into Xcode's Accounts pane). Release now points to `F4GSKN4DLP`; Debug moved to a free personal team (`J749E89A2L`) for local dev signing. The old `6PS2FVLY6K` team on Release was orphaned — not in the account's team list at all.
- [x] **Fix the bundle ID mismatch.** ✅ **DONE 2026-07-04.** Decided on `com.borinquenterrier.cef` to match Android. Updated in `Config.xcconfig` and `GoogleService-Info.plist`. **Still needed (account action, not code):** register a matching App ID for `com.borinquenterrier.cef` in the Apple Developer portal before creating the App Store Connect record. Google Sign-In is unaffected — it uses a manual OAuth flow keyed off the `com.googleusercontent.apps.<id>` URL scheme in `Info.plist`, not the bundle ID string, so no redirect/scheme changes were needed. Optional hygiene: update the Bundle ID field on the iOS OAuth client in Google Cloud Console to match, for consistency (not functionally required).

## Phase 2 — App readiness (code)

- [x] **Add a `PrivacyInfo.xcprivacy` manifest.** ✅ **DONE 2026-07-05.** The app reads `NSUserDefaults` directly (via `NSUserDefaultsSettings` in `SettingsFactory.ios.kt`), one of Apple's "required-reason" API categories. File added at `iosApp/iosApp/PrivacyInfo.xcprivacy` declaring `NSPrivacyAccessedAPICategoryUserDefaults` with reason `CA92.1` (own-app-only access — matches actual usage, no App Group). **No Xcode GUI/pbxproj step was needed** — this project uses Xcode's file-system-synchronized groups (`PBXFileSystemSynchronizedRootGroup` in `project.pbxproj`), which auto-include any file placed in the `iosApp` folder unless explicitly excepted (only `Info.plist` is excepted). Verified via `xcodebuild -project iosApp.xcodeproj -target iosApp -sdk iphonesimulator build`: `PrivacyInfo.xcprivacy` is present in the built `CollegeExecutiveFunction.app` bundle.
- [x] **Sync the version number with Android.** ✅ **DONE 2026-07-04.** `MARKETING_VERSION` bumped to `2.0.0` in `Config.xcconfig` to match Android; `CURRENT_PROJECT_VERSION` (build number) left at `1` as the first build of this marketing version.
- [x] **Icon & accent color polish.** ✅ Already done in commit `ee272b0` — `AppIcon.appiconset` has base/dark/tinted variants and `AccentColor` is set to the amber accent.

## Phase 2.5 — Hardening pass (blocks submission)

**Gate: none of Phase 4 starts until every item below is checked.** These are cross-platform
(shared `composeApp`/KMP code, so they apply to iOS, Android, and desktop alike) correctness and
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

## Phase 3 — App Store Connect setup

- [x] **Create the app record.** ✅ **DONE 2026-07-05.** "College Executive Function" created in App Store Connect — Company Borinquen Terrier LLC, bundle ID `com.borinquenterrier.cef`, SKU `com.borinquenterrier.cef`, primary language English (U.S.). Version corrected from Apple's "1.0" placeholder to `2.0.1` (matching Android/`Config.xcconfig` after that fix — see Phase 2). Note: an EU trader-status banner appeared on the Apps page (Digital Services Act compliance) — unresolved, needs an Admin/Account Holder to fill in before EU distribution.
- [x] **Fill in the App Privacy "nutrition label."** ✅ **DONE 2026-07-05, published.** Declared: **User Content** (calendar events + syllabus/document text — App Functionality, linked to identity) and **Diagnostics** (OTEL telemetry — App Functionality, linked to identity). Not declared: Contact Info (verified in code — `GoogleAuthService.ios.kt:29` requests only the `calendar` OAuth scope, no email/profile), Location, Financial, Health, Contacts, Browsing/Search History, Purchases, Identifiers. Neither data type is used for tracking. Along the way, found and fixed 9 sites across 6 files where OTEL spans carried raw identifying data (calendar IDs, source/document titles, URLs) instead of anonymous operational metrics — added `TelemetryIdHasher` (one-way SHA-256, 12 hex chars) so telemetry keeps its correlation value without ever transmitting the raw value. New `TelemetryIdHasherTest.kt`; full JVM suite (202 files) and all 3 build targets pass clean.
- [x] **Age rating.** ✅ **DONE 2026-07-05.** Calculated **4+** (172 countries/regions), "Not Applicable" override (not a Kids-category app — targets college students, requires Google Sign-In). All content-frequency questions answered "None"/"No" (no gambling, violence, mature themes, UGC distribution, user-to-user messaging, ads).
- [x] **Description, keywords, subtitle, category, support/marketing URLs.** ✅ **DONE 2026-07-05.** Subtitle "Smart Academic Planner + AI", Category Education (primary) / Productivity (secondary), Content Rights "No third-party content", Support URL & Marketing URL both `https://borinquenterrier.com/college-executive-function` (verified live), Copyright "2026 Borinquen Terrier LLC", full Promotional Text/Description/Keywords drafted and saved (ADHD/AuADHD framing intentionally left out of public copy per product decision — kept general "executive function challenges" language instead). **Updated 2026-07-05** after the TestFlight pass: the "HOW IT WORKS" import bullet now names Google Drive/Dropbox/iCloud Drive/other cloud storage apps explicitly, since the iOS file picker surfaces whatever provider apps are enabled as Files "Locations," not just Drive.
- [x] **Screenshots.** ✅ **DONE 2026-07-05.** Captured via `iPhone 17 Pro Max` and `iPad Pro 13-inch (M5)` simulators (exact-pixel matches for the 6.9"/13" required buckets — 1320×2868 and 2064×2752 respectively; the connected real iPhone 17 Pro was the wrong size class for the required bucket). 3 screenshots each (Home/Chat, Calendar, Settings) uploaded; both platforms' secondary size buckets (iPhone 6.5", iPad 11"/12.9") auto-inherited from the primary upload, no separate action needed.
- [x] **App Encryption Documentation.** ✅ **DONE 2026-07-05.** Answered "None of the algorithms" (CEF only uses standard HTTPS/TLS via the OS's built-in networking — Ktor's Darwin engine wraps `NSURLSession` — no custom or bundled crypto). Also added `ITSAppUsesNonExemptEncryption = false` to `Info.plist` so this question doesn't need re-answering on every future upload.

## Phase 4 — Build, test, submit

- [x] **Confirm Phase 2.5 (Hardening pass) is fully checked off.** ✅ Confirmed — HARD-1..9 all done (see above).
- [x] **Archive & upload.** ✅ **DONE 2026-07-05.** Build `2.0.1 (1)` archived and uploaded via Xcode's Product → Archive → Organizer → Distribute App → App Store Connect. Now shows "Ready to Submit" in TestFlight.
  - **Known gotcha hit during this release — document for next time:** `xcodebuild archive -allowProvisioningUpdates` hung indefinitely (3 attempts, ~15 min each) with the connected physical iPhone as the destination; stack sampling showed it stuck in `CoreDevice`/`DTDKRemoteDeviceConnection` device-discovery code. **Fix:** switch the scheme's run destination to the generic **"Any iOS Device (arm64)"** placeholder instead of a specific connected physical device before archiving — avoids any real-device communication entirely.
  - **Second gotcha:** the Organizer's "Distribute App" step failed with `Copy failed` / `rsync error: syntax or usage error (code 1)`. Root cause: **Homebrew's `rsync` (3.4.4) shadows Apple's `/usr/bin/rsync`** on this machine's PATH, and Xcode's IPA packaging step isn't compatible with the newer rsync. **Fix:** temporarily `mv /opt/homebrew/bin/rsync /opt/homebrew/bin/rsync.bak` before archiving/distributing, restore after. Worth a permanent PATH fix (e.g. an Xcode-only shell wrapper) if this recurs on future releases.
- [ ] **TestFlight pass, then submit for review.** Build is uploaded and compliance-cleared ("Ready to Submit"); internal testing group created, tester (`fduquedeestrada@icloud.com`, added as a Developer-role team member) completed a real TestFlight pass on-device 2026-07-05 and reported 4 bugs — all fixed and verified (see ROADMAP.md's i18n/EventKit backlog entries and the calendar-picker fix committed `cf67783`). Still need to: re-run/confirm the TestFlight pass against the fixed build, then submit for App Review. **App Review notes: DONE 2026-07-05** — added to the App Review Information "Notes" field in App Store Connect, covering (1) Google Sign-In uses standard OAuth, any Google account works, only the Calendar scope is requested; (2) the app needs a free Gemini API key from the user (link to `aistudio.google.com/apikey` included so reviewers aren't stuck); (3) cloud-storage imports (Drive/Dropbox/iCloud Drive/etc.) go through iOS's native file picker and require the provider app to be enabled as a Files "Location" first — a reviewer with none enabled won't see one appear as a source, which is expected, not a bug.

## Explicitly not addressed here

- Migrating off the legacy `startActivityForResult`-style patterns or deprecated auth libraries — out of scope for a submission-readiness pass.
- R8/ProGuard-equivalent iOS bitcode/obfuscation concerns — not applicable to this app's risk profile (small, OSS).
