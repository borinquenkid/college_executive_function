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

- [ ] **Add a `PrivacyInfo.xcprivacy` manifest.** The app reads `NSUserDefaults` directly, one of Apple's "required-reason" API categories. Apple's binary validation can warn or reject without a privacy manifest declaring the reason code.
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
- [ ] **HARD-9** — Full teardown (or an explicit, honest retention choice) on Google account disconnect — **do this before finalizing the Phase 3 App Privacy nutrition label below**, since what disconnect actually does to local data determines what that label should say.

## Phase 3 — App Store Connect setup

- [ ] **Create the app record** — bundle ID, app name, SKU, primary language, in App Store Connect → My Apps → New App.
- [ ] **Fill in the App Privacy "nutrition label."** Declare the data types actually collected: email/account (Google Sign-In), calendar data, files/documents you ingest, and any data sent to Gemini. Must match what the privacy policy says. **Depends on HARD-9 (Phase 2.5) being resolved first** — the label must reflect what actually happens to synced calendar data on disconnect.
- [ ] **Age rating, screenshots, description.** Screenshots needed for 6.7" iPhone and iPad (the project targets both device families) — plus description, keywords, support URL, and the privacy policy URL.

## Phase 4 — Build, test, submit

- [ ] **Confirm Phase 2.5 (Hardening pass) is fully checked off.** Do not archive/submit until it is.
- [ ] **Archive & upload.** No Fastlane exists yet — do this via Xcode's Product → Archive, then upload through Organizer or Transporter.
- [ ] **TestFlight pass, then submit for review.** Run an internal TestFlight build first. Include App Review notes explaining how to test Google Sign-In — reviewers need a clear path through any login-gated flow, a common first-round rejection reason. Also note in the review notes that importing from Google Drive requires the separate Drive app to be installed on the test device (Drive access goes through iOS's native file picker now, not an in-app browser) — otherwise a reviewer without Drive installed may not find where "Drive" is and flag it as broken.

## Explicitly not addressed here

- Migrating off the legacy `startActivityForResult`-style patterns or deprecated auth libraries — out of scope for a submission-readiness pass.
- R8/ProGuard-equivalent iOS bitcode/obfuscation concerns — not applicable to this app's risk profile (small, OSS).
