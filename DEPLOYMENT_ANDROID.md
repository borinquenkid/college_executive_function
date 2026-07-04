# Google Play Store Deployment Plan

Publisher: **Borinquen Terrier LLC**
Package: `com.borinquenterrier.cef`

> **Not legal advice.** The disclaimer draft in the shared prerequisites below is a starting point to bring to an actual lawyer before publishing — LLC liability and app store agreements are worth a short paid consult, not a guess.

## Start here — shared with the App Store plan

Do these once; both platform plans assume they're in progress or done. See `DEPLOYMENT_IOS.md` for the iOS-specific plan.

1. **Confirm your Apple account type** (Individual vs Organization) — needed for iOS, not Android, but listed here since it's a shared prerequisite.
2. **Enroll in Google Play Console** (play.google.com/console/signup) as an **Organization** account, $25 one-time, with Borinquen Terrier LLC's business details.
3. **Draft and host a Privacy Policy + Terms of Service.** Both stores require a public Privacy Policy URL before submission. Host as a static page anywhere reachable.
4. **Move the Google OAuth consent screen from Testing to Production — start this first, it's the longest pole.** The app's Calendar/Drive scopes are "sensitive/restricted," so Google's verification can require a CASA security assessment taking days to weeks. Needs the privacy policy URL from step 3.
5. **Use one publisher name everywhere** — "Borinquen Terrier LLC" should match exactly across App Store Connect's Business/Agreements page and Play Console's org profile.

### Draft AI-disclaimer clause for the Terms of Service

Starter draft — have a lawyer review before publishing:

> **AI-Generated Content.** College Executive Function uses artificial intelligence (including third-party models such as Google's Gemini) to extract, generate, and organize academic deadlines, calendar events, and study plans from documents and calendars you provide. AI-generated content may be incomplete, inaccurate, or out of date.
>
> You are solely responsible for verifying all dates, deadlines, and academic requirements against your official course syllabus, institution, and instructors before relying on them. Borinquen Terrier LLC provides the application "as is" and, to the fullest extent permitted by law, disclaims all liability for missed deadlines, academic consequences, or other damages arising from reliance on AI-generated or AI-assisted content.

---

## Phase 1 — Account

- [ ] **Enroll in Google Play Console as an Organization.** play.google.com/console/signup, $25 one-time. Choose the Organization account type and provide Borinquen Terrier LLC's business verification details — same enrollment listed in Start Here above.

## Phase 2 — App readiness (code) — hard blockers

- [ ] **Add a launcher icon.** There is currently no `mipmap`/adaptive-icon resource at all, and `AndroidManifest.xml`'s `<application>` tag has no `android:icon`. The app builds today with the generic system icon — this blocks a credible store listing.
- [ ] **Fix the hardcoded `versionCode`.** It's fixed at `1` in `androidApp/build.gradle.kts`. Play rejects a re-upload with a duplicate version code, so an incrementing strategy is needed before the second release — tying it into `release.sh` alongside `versionName` is the natural place.
- [x] **Signed release build** — ready. The keystore and env-var-first signing config (`CEF_KEYSTORE_PATH` etc.) are already wired up. Confirm `bundleRelease` produces a signed `.aab` (Play requires App Bundles, not APKs) once the icon/versionCode fixes above land.

## Phase 3 — Play Console listing

- [ ] **Store graphics.** Hi-res icon (512×512), feature graphic (1024×500), and phone + tablet screenshots — none of these exist yet, separate from the in-app launcher icon above.
- [ ] **Content rating (IARC) & target audience.** Answer the questionnaire, set target audience to adult/college-age (not child-directed).
- [ ] **Data Safety section.** Declare what's collected/shared — should mirror the App Privacy label filled in for iOS and the privacy policy itself. Ads, IAP, government/financial/health content: all "No" given the app is free with no monetization.

## Phase 4 — Testing & release

- [ ] **Check whether a closed-testing period is required first.** Play's newer policy can require 12 testers for 14 days on a testing track before opening Production, mainly for new personal developer accounts — verify whether this applies to the Organization account in Console before planning a release date.
- [ ] **Upload & promote to Production.** Upload the signed `.aab` to a testing track, then promote once satisfied.

## Explicitly not addressed here

- R8/ProGuard minification — deliberately left off; the app is small and OSS, so obfuscation/size-shrinking benefits don't apply, and turning it on for the first time risks silently stripping reflection-reached code for no real gain.
- `versionCode` hardcoded to `1` beyond the fix above — no active Play Store release cadence yet that would be blocked by a manual bump in the meantime.
- Migrating `GoogleAuthActivity`'s legacy `startActivityForResult` to the modern Activity Result API, and migrating off `GoogleSignInClient`/`GoogleAuthUtil` toward Credential Manager — real modernization work, out of scope for a submission-readiness pass.
