# Release, Store Submission and Ops Gotchas

> Moved out of AGENTS.md on 2026-11-07 (best-practices review T2). AGENTS.md keeps mandates only.

## Release

`./release.sh X.Y.Z` bumps `cef.versionName` in `gradle.properties`, auto-increments
`cef.versionCode`, syncs iOS `MARKETING_VERSION` in `iosApp/Configuration/Config.xcconfig`,
commits, pushes the branch, then tags `vX.Y.Z` and pushes the tag. Run it from the branch you
intend to release.

**Pushing the `vX.Y.Z` tag is a live trigger, not a no-op.** `release-desktop.yml` (**Release
Desktop (JVM)**, whose `verify` job also runs `:server:test`) and `deploy.yml` (**Deploy Android
(Play Store)**) both fire on any `v*.*.*` tag push, and Xcode Cloud watches the same tag for its
iOS archive/build — the same 4 targets (composeApp/Desktop, server, androidApp, iosApp) covered
by `release.sh`'s pre-tag verification gate. So running `release.sh` — or the tag-push step
inside it — kicks off real CI/CD on all 4 platforms immediately; there's no separate
confirmation step before those builds start.

### Choosing X.Y.Z (semver)

One version number covers the whole monorepo (Android/iOS/Desktop apps *and* the self-hosted
`/server` + `/web` deployment), so pick the bump based on the most severe change across
everything shipped since the last tag, not just the apps:

- **MAJOR** — anything that breaks an existing self-hosted deployment or an app user without
  action on their part: removed/incompatible API routes, new *required* server env vars (the
  server now fails to boot without them), an auth/data model change existing deployments must
  reconfigure for. Example: the LTI 1.3 auth rewrite (`docs/adr/0006`) removed
  `POST /api/auth/start` entirely and made `CEF_APP_BASE_URL`/`CEF_LTI_*` required — existing
  deployments crash-loop until reconfigured. That's MAJOR (2.4.2 → 3.0.0), even though the
  Android/iOS/Desktop apps themselves were untouched.
- **MINOR** — new functionality that doesn't break existing usage: new optional env vars, new
  endpoints/features, UI additions, a new app capability.
- **PATCH** — bug fixes, CI/workflow fixes, dependency bumps, docs, refactors with no behavior
  change for any existing user or deployment.

When in doubt, check whether a deployment that was working on the previous version keeps working,
unmodified, on the new one. If not, it's MAJOR regardless of how small the code diff looks.

- **Caveat:** the branch push only happens when the script actually creates a version-bump
  commit. If `gradle.properties` is already at that version, it pushes *only* the tag — any
  unpushed code commits are left behind. So bump to a new version each release.
- The repo is public OSS, so cutting a desktop/CI release is independent of the app stores —
  you can release even while a store submission is pending. **Never commit secrets or anything
  with real user data here** (screenshots live gitignored under `branding/play-store/screenshots/`).

### Store submission reference

Store-listing assets and questionnaire drafts live in `branding/play-store/`.

- **Privacy Policy:** https://borinquenterrier.com/cef-privacy-policy
- **Terms of Service:** https://borinquenterrier.com/cef-terms-of-service
- **Marketing page:** https://borinquenterrier.com/college-executive-function
- Support/contact: privacy@borinquenterrier.com
- **iOS** (App Store Connect, team *Borinquen Terrier LLC* / `F4GSKN4DLP`, signed in the
  **Release** config): archive via Xcode GUI → Organizer → Distribute → App Store Connect →
  Upload. You **cannot have two versions in review at once** (must "remove from review" to swap
  a build), so don't re-submit while a prior version is Waiting for Review.
- **Android** (Play Console, org account *Borinquen Terrier LLC*, package
  `com.borinquenterrier.cef`): `./gradlew :androidApp:bundleRelease` → signed `.aab` at
  `androidApp/build/outputs/bundle/release/` (keystore `cef-release`, configured via
  `local.properties`/env — never checked in). App is Free, no ads/IAP, not child-directed.

### Ops gotchas (cost us time — don't relearn)

- **iOS upload hangs** at *"Waiting for App Store Connect analysis response"* when a Homebrew
  `rsync` shadows Apple's `/usr/bin/rsync` (Xcode's uploader shells out to it). Fix:
  `brew uninstall rsync` so PATH falls back to `/usr/bin/rsync`. This Xcode has no transport
  picker in the Organizer.
- **`eval-corpus` CI** needs `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` secrets (the
  `generateBuildSecrets` task aborts config without them), plus `CEF_GEMINI_API_KEY` = the
  **BorinquenTerrier paid (Tier 1) Gemini key** for integration tests — the free tier's shared
  RPM quota degrades extraction recall. `CEF_TEST_USER_API_KEY` is a separate manual-testing key.
- **Flaky async tests:** verify StateFlow-collector invocation counts with `runTest` +
  `StandardTestDispatcher(testScheduler)` + `advanceUntilIdle()`, not `Dispatchers.Unconfined`
  + wall-clock `eventually()` (that raced under CI load — see `AppControllerTest`).
- **Deploy Android (Play Store) took 5 releases (v3.0.4→v3.0.8, 2026-07-22) to actually go green,
  one root cause per attempt** — `deploy.yml` only triggers on a real `v*.*.*` tag push, so each
  fix required a full release cycle to even observe the next failure:
  1. `curl -sf -X POST ... | python3` with no `set -o pipefail` masked every real HTTP failure as
     a generic downstream `JSONDecodeError` on empty input.
  2. `gcloud auth application-default print-access-token` mints its **own** token from the ADC
     file using gcloud's default `cloud-platform` scope — it silently ignores the
     `google-github-actions/auth@v2` step's `access_token_scopes` input, which only ever applies
     to that step's own `outputs.access_token`. Read `steps.auth.outputs.access_token` directly
     instead of shelling out to `gcloud`.
  3. The best-effort cleanup call (deleting the verification draft edit) ran under this step's
     implicit `bash -e`, so its failure took the whole job down even after the real access check
     had already passed. Made non-fatal (`::warning::`, not `exit 1`).
  4. `gradle/verification-metadata.xml` only had `aapt2-<ver>-osx.jar` (dev machines here are
     macOS); `ubuntu-latest` needs `-linux`. **`release.sh`'s local 4-target gate cannot catch
     this class of gap** — it only ever resolves the developer's own OS's classifier of a native
     dependency. Check for both `-osx` and `-linux` entries whenever bumping a native Android
     build tool (aapt2, d8, r8, etc.).
  5. `deploy.yml`'s publish step never forwarded `CEF_OTLP_ENDPOINT`/`CEF_OTLP_USER`/
     `CEF_OTLP_PASSWORD` (required by composeApp's `verifyReleaseTelemetrySecrets`, HARD-1) or
     `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` (required by `generateBuildSecrets`, which compiles
     into every target and hard-errors under `GITHUB_ACTIONS=true`) — unlike
     `release-desktop.yml`'s build step, which already had all five.

- **GitHub Actions repo secrets can silently drift from the GCP project you think you're
  verifying** — `release-desktop.yml`'s `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` were last set
  2026-06-11, two weeks *before* the real, verified GCP project (`college-executive-function`,
  `1014783111965`) was even created (2026-06-27). Every desktop release from v3.0-era through
  v3.2.2 kept shipping OAuth credentials from an earlier decoy project
  (`neural-cortex-474922-u0`, `118849293337`, under a personal Gmail account) that was never
  submitted for Google verification and is permanently stuck in Testing status — so users kept
  seeing "Google hasn't verified this app" no matter how much verification work landed on the
  real project. Confirmed by decoding the XOR-obfuscated `BuildSecrets.class` inside a downloaded
  release artifact (obfuscation key `19007`, see `composeApp/build.gradle.kts`'s
  `generateBuildSecrets`). **A correct local `.env` proves nothing about what CI actually bakes
  in** — `gh secret list` timestamps vs. GCP project creation dates is the only reliable check.
  Fixed 2026-08-23: rotated the Desktop OAuth client secret, updated the three GitHub secrets
  (`GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`/`GOOGLE_REFRESH_TOKEN` — the refresh token has to be
  re-minted too, since it's bound to the specific client that issued it), shipped v3.2.3 clean.
