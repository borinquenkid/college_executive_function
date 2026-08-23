# College Executive Function

An application designed to assist students with executive function challenges by providing a structured environment to manage academic sources, generate study materials, and maintain a comprehensive academic calendar.

---

## Agent Mandates

### Clarify Protocol
Before writing any phase plan into `ROADMAP.md`, ask these questions and get answers — do not proceed to the plan until each is resolved:

1. **Verification** — How will we know this is working? Can it be tested automatically, or does it require a manual walkthrough? If manual, who does it and under what conditions?
2. **Edge cases** — What inputs or states could break this? Name at least two.
3. **Quality Gate impact** — Which existing files does this touch? Will any of them exceed complexity 5 per method or 15 per file after the change?
4. **Dependencies** — Does this block or get blocked by anything else in the roadmap?

If any answer is "I don't know," resolve it before planning. A plan built on an unresolved unknown will produce a phase with a gap (like 9C — Drive picker unverifiability — which was a planning gap, not an implementation gap).

---

### Analyze Protocol
After writing a phase plan into `ROADMAP.md` but **before writing any code**, check cross-artifact consistency:

1. **Spec ↔ Plan:** Every user-facing behavior described in the motivation has a corresponding deliverable row. Nothing in the deliverables table is absent from the motivation.
2. **Plan ↔ Tests:** Every deliverable row has at least one corresponding test listed. No deliverable is marked done without a test requirement.
3. **Plan ↔ Quality Gate:** Every file in the "files changed" column has a Quality Gate acceptance criterion. No file is added to the plan without specifying its complexity budget.
4. **Plan ↔ Verification:** The plan explicitly states how 9C-class manual steps (things that can't be unit-tested) will be verified and by whom.

If any check fails, fix the plan before implementing. An inconsistency found here costs one edit to `ROADMAP.md`; the same inconsistency found after implementation costs a phase rollback.

---

### Converge Protocol
After completing a phase (after the Sonar Quality Gate check and build verification pass), perform a structured gap audit before closing the phase:

1. **Spec coverage** — Read the phase motivation. For each stated goal, confirm there is a commit that addresses it. Name the commit.
2. **Test coverage** — For each new class or method introduced, confirm a test exists that exercises its primary path. Check the Sonar dashboard (or `checkQualityGate`'s printed conditions) for 0% coverage entries in new files.
3. **UI reachability** — For any UI change, confirm the changed surface was actually reached during the session (screenshot, manual walkthrough, or Compose test). Unreachable UI = unverified UI.
4. **Regression check** — Run the full JVM test suite. Confirm no previously-passing tests now fail.
5. **Mandate compliance** — Confirm the phase respected all AGENTS.md mandates: complexity limits (Quality Gate), build verification, integration test naming, StateFlowReader/Writer pattern, Confabulation Gate for any new AI method.

If any item above is unresolved, document it as a named gap in ROADMAP.md under the phase (like `9C — NEEDS HUMAN`) rather than silently leaving it open. A gap that is named is a gap that gets closed; an unnamed gap becomes a bug.

**Trigger:** This protocol is mandatory at phase completion, in addition to — not instead of — the Static Analysis Quality Gate Protocol and Build Verification Protocol. Run all three.

---

### Build Verification Protocol
Whenever a task or feature is reported as "done" (except when specifically running unit tests or Quality Gate checks), verify that all four primary build targets compile successfully:
```bash
./gradlew :composeApp:assembleDebug :iosApp:assemble :server:assemble :androidApp:assembleDebug
```
Confirm these four builds pass before confirming completion. `:androidApp:assembleDebug` was
missing from this command until 2026-07-22 — its absence is exactly why a dependency-verification
gap (guava-parent, missing from `gradle/verification-metadata.xml`) passed local checks twice
(v3.0.0, v3.0.3) but failed on every CI/Xcode Cloud pipeline that actually configures
`:androidApp`, which is all of them. A warm local Gradle cache also won't reliably catch a stale
`verification-metadata.xml` — see `release.sh`'s pre-tag gate (`--refresh-dependencies` across all
four targets) for the check that actually simulates a fresh CI checkout; this everyday protocol
is for regular compile-error verification, not a substitute for that gate before tagging a release.

**Even that gate has a blind spot: OS-specific artifact classifiers.** `release.sh` runs on
whatever OS the developer is on (macOS here), so it only ever resolves and verifies that OS's
classifier of a native dependency — e.g. `aapt2-<version>-osx.jar`. **Deploy Android (Play Store)**
runs on `ubuntu-latest` and needs `aapt2-<version>-linux.jar`, a *different* artifact with its own
checksum entry in `verification-metadata.xml`. v3.0.6 hit exactly this: the local gate passed
clean, then Deploy Android failed dependency verification on the linux classifier moments later.
There is no local reproduction for this class of gap — when adding/bumping a native Android build
tool (aapt2, d8, r8, etc.), check `verification-metadata.xml` for both `-osx` *and* `-linux`
entries, or expect Deploy Android to be the only thing that catches a missing one.

### Static Analysis Quality Gate Protocol

> **Deviation from this file's own prior CRAP-index approach, as of 2026-07-05.** This
> section previously described a hand-rolled "CRAP index" regex/brace-counting tool
> (`CrapIndexReporter.kt`, `CRAP.md`/`COVERAGE.md`). Retired in favor of SonarQube
> Community Edition (self-hosted, local — see `docs/ops/sonarqube-local.md`), real
> AST-based analysis, after the sibling `oficio` project traced the old tool's regex to
> two mechanical bugs on a real file: double-counted `?.let{}` safe-call guards (matching
> both a `\?\.` pattern and a `\.let\b` pattern on the same characters) and English words
> ("for", "when") inside comments matched as real control flow — a ~3.5x complexity
> overcount, not a reasonable difference of opinion between two valid tools.
>
> Original lesson this section still honors: **TDD gets you coverage, it does not
> force refactor. Tested spaghetti is still spaghetti** — a real static-analysis gate
> is the check TDD doesn't enforce by itself.

To check code quality and coverage after code changes or test additions:
```bash
docker compose up -d sonarqube   # if not already running (see docs/ops/sonarqube-local.md)
./gradlew :composeApp:checkQualityGate
```
This single command chains `jvmTest` → `koverXmlReportJvm` → `sonar` → the Quality Gate
check automatically. If the Quality Gate is not OK — **the phase is not done.** The task
prints each failing condition (metric, actual value, threshold) before failing the build.

### UI Verification Protocol
For UI-related changes, verify the visual state by running the relevant module (e.g., `:composeApp:jvmRun`) and performing a screen capture (e.g., using macOS `screencapture`). Layout optimizations and visual features must be physically verified on-screen before being reported as complete.

### Quality Gate Remediation Protocol
When a file fails or nears the Quality Gate (high cyclomatic/cognitive complexity, low coverage, poor maintainability rating), prefer **decomposing it into smaller, single-responsibility files before writing tests against it**. Splitting one high-complexity file into focused units shrinks its complexity and duplication sharply on its own — often more than coverage alone would. Testing a monolith first is a sunk cost: once it's split, those tests have to be rewritten or relocated against the new shape anyway. Decompose first, then write targeted tests against the smaller, stable units that result.

### Reactive State Testability Pattern: StateFlowReader/StateFlowWriter

**Foundational Principle:** If you can't test it, you can't ship it. Raw reactive types (StateFlow, Flow) block testing just like raw IOStreams do in Java.

**Mandatory Pattern for all StateFlow usage:**

Never expose `StateFlow<T>` directly. Always wrap in reader/writer interfaces:

```kotlin
// Read-only interface for observers
interface StateFlowReader<out T> {
    val value: T
    suspend fun collect(collector: suspend (T) -> Unit)
}

// Write-only interface for owners
interface StateFlowWriter<in T> {
    fun setValue(value: T)
}

// Private implementation (not exposed)
class MutableStateFlowWrapper<T>(initialValue: T) : StateFlowReader<T>, StateFlowWriter<T> {
    private val _flow = MutableStateFlow(initialValue)
    override val value: T get() = _flow.value
    override suspend fun collect(collector: suspend (T) -> Unit) = _flow.collect(collector)
    fun setValue(newValue: T) { _flow.value = newValue }
}
```

**Application Rules:**
1. Never expose `StateFlow<T>` as a public property. Always use `StateFlowReader<T>` or `StateFlowWriter<T>`.
2. Components that read state receive `StateFlowReader<T>` only (e.g., `AppController` gets `sourceItems: StateFlowReader<List<SourceItem>>`)
3. Components that write state receive `StateFlowWriter<T>` only (e.g., `SourceManager` gets write access internally)
4. Tests can mock readers/writers independently without requiring real implementations
5. This enforces **Principle of Least Privilege** at the type level

**Why:** Exposing raw StateFlow forces tests to either:
- Mock the entire reactive lifecycle (fragile, error-prone) → forces using real implementations → untestable architecture
- Use the real implementation (defeats unit test isolation)

Wrapping separates concerns and makes mocking trivial:
```kotlin
// Test setup becomes simple
val mockSourceItems = mockk<StateFlowReader<List<SourceItem>>>()
every { mockContainer.sourceItems } returns mockSourceItems
```

### Complexity & Decomposition Standards
Prevent Quality Gate failures by designing for decomposition and testability **from the spec phase**, not by discovering problems during metrics review. Architectural requirements must enforce these limits:

1. **Per-Method Complexity Limit: 5**
   - Any public method exceeding 5 control flows (branches, loops, logical operators, safe calls, collection operators) must be refactored into smaller focused methods or delegated to extracted services.
   - Example: `GoogleRemoteCalendarRepository.clearCalendar()` had 6 branches → extracted loop logic to `EventRangeFilter`.

2. **Per-File Complexity Limit: 15**
   - No file shall exceed 15 total complexity across all methods. Files nearing this limit are candidates for service extraction.
   - Example: `GoogleRemoteCalendarRepository` at 30 → decomposed into three services (complexity 2–5 each).

3. **Thin Facade Pattern as Architectural Requirement**
   - Any class coordinating 3+ distinct responsibilities must delegate to separate, focused services rather than handle them inline.
   - Require explicit dependency injection of extracted services in constructors.
   - Example: Instead of "GoogleRemoteCalendarRepository handles calendar operations," spec: "GoogleRemoteCalendarRepository is a thin facade delegating to CalendarIdResolver, EventConflictDetector, and EventRangeFilter."

4. **Testability-First Requirements**
   - Every public method must be independently testable with focused unit tests (1–3 assertions, clear mocks).
   - Methods with 6+ control flows are flagged as "not testable cleanly" and must be split before merging.
   - Each extracted service must have its own unit test file with 5+ test cases covering happy paths, edge cases, and error conditions.

5. **Service Extraction Rules**
   - Extract a service when a method has 3+ distinct responsibilities (e.g., ID resolution + conflict detection + filtering).
   - Extracted services must be single-responsibility: handle exactly one concern (e.g., "detect overlaps," "filter by date," "resolve calendar ID").
   - Use dependency injection to wire extracted services; document the dependency graph in class comments.

6. **Coverage-by-Design Targets**
   - Business logic services (not UI, not Compose) target **100% line coverage** to catch missing branches early.
   - No file shall have coverage below the threshold determined at spec time (e.g., "no business logic below 80% coverage").
   - Coverage gaps are blockers: code with 0% coverage cannot be merged, even if "it's new."

7. **Quality Gate as Non-Functional Requirement**
   - Include Quality Gate acceptance criteria in the spec: "No file shall exceed cyclomatic complexity 20 / maintainability rating worse than A."
   - Files exceeding the threshold at PR time are rejected; author must decompose before re-review.
   - Use `./gradlew :composeApp:checkQualityGate` as part of the done definition, not post-hoc analysis.

**Pattern:** The three-service structure that emerged in Phase 0.23 (`CalendarIdResolver`, `EventConflictDetector`, `EventRangeFilter`) is what should have been in the original spec — not derived through refactoring. Spec decomposition prevents complexity from accruing; metrics review only measures what you missed.

### Confabulation Gate Protocol

Every method on `AIService` that returns **structured output** (events, metadata, tasks, or any typed result persisted or displayed to the user) **must have explicit gate coverage** in `GroundingGuardAIService`. Delegation-by-default through the `by delegate` interface is not a gate — it is the absence of one.

**Gate levels (apply the strictest level that the output type supports):**

1. **Year-level grounding** — required for all event-producing methods (`generateCalendarEvents`, `generateStudyPlan`). After extraction, filter events to **years explicitly mentioned in the source text**. An event dated 2099 from a source that only mentions 2026 is a confabulation and is dropped. Students may load syllabi from any semester (past, current, or future) and all events grounded in the source's years are kept. Do not filter by today's date — a Fall syllabus loaded in June should return Fall events.

2. **Source-fact grounding** — required for free-text output that will be persisted or injected into chat context (`analyzeDocument`, `generateChatResponse`). A gate here only has value when the loaded corpus contains concrete factual anchors — specific due dates, assignment weights, course policies. Without those anchors there is no ground truth to check against, and the gate would be checking form, not fact. Apply this level once students are loading syllabi with structured deadlines.

3. **Critic-only** — acceptable for generative outputs where grounding to source dates is not meaningful (`decomposeTask`, `categorizeSource`). The Critic-Actor loop is still required.

**Checklist for any new `AIService` method:**
- [ ] Method is overridden in `GroundingGuardAIService` (not delegated silently)
- [ ] Gate level is explicitly chosen and justified in a comment
- [ ] Unit test covers the gate: a confabulated value that should be dropped is dropped

**Known gaps and their readiness conditions:**

1. ~~`generateCalendarEvents` / `generateStudyPlan` — year-level grounding~~ ✅ Closed. Events whose year does not appear in the source text are dropped. Semester-level filtering was attempted but reverted: filtering by today's date is wrong because students load past and future syllabi intentionally.

2. ~~`analyzeDocument` / `generateChatResponse` — defer until the corpus has factual anchors.~~ ✅ Closed via `SourceFactGrounder`. Gate extracts date and grade-weight claims from free-text AI output and cross-checks each against the source text provided to the model. Ungrounded claims trigger a structured warning appended to the response; the response is not dropped (sentence-level surgery is out of scope). Corpus: 13 real UT Austin Fall 2025 syllabi in `contributions/tx/ut_austin/2025-2026/fall/` provided the factual-anchor readiness condition.

### Exception Handling: No Silent Catch-and-Rethrow
A `catch` block that rethrows (even wrapped in a clearer exception/message) is not a fix if nothing
ever observes that it fired. Every catch that doesn't let the original exception propagate
unchanged must also log or record it through this codebase's existing tooling (`Logger` in
`composeApp`, `println("[Tag] ...")` in `server`, `AppTracer`/telemetry where a span already exists)
— not just construct a new exception and throw it. Without that, a real production failure looks
identical to "working as intended" in every log and dashboard; the only difference is a slightly
better message the one caller who happens to catch it will see, if any caller catches it at all.

This came out of the ADR 0010 exception-path audit (2026-07): several fixes there wrapped a caught
exception in a clearer message before rethrowing, with no log/telemetry call alongside it — better
for whichever caller eventually sees the message, but invisible to anyone watching logs/telemetry
in the meantime. Applies equally to a `catch` that swallows to a sentinel value (`null`, empty
list, default) — same rule, log before returning the fallback.

### Native Dependency Management
Manual modification of Xcode project files (`.pbxproj`) and adding external Swift packages is strictly prohibited due to their brittleness in KMP builds. All native features MUST be implemented using platform-native APIs already available in the system frameworks, accessible via pure Kotlin/Native interop, to ensure build stability.

### Codebase Intelligence (MCP)
This project uses `repowise` as an MCP server for codebase intelligence (docs, graph, git signals). Install it with:
```bash
uv tool install repowise
```
The MCP server is configured in `.gemini/settings.json` to start automatically.

---

## Run Profiles

The application supports two distinct run profiles to manage different execution environments:

*   **`local` Profile (Runtime):**
    *   Interacts with the **real** Google Calendar.
    *   Uses your local `.env` or stored OAuth tokens.
*   **`test` Profile (Mock):**
    *   Uses a **Mock Calendar** to provide deterministic data for automated testing.
    *   Skips the network and real authentication.

---

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

### Cross-repo ops knowledge

Operational knowledge that spans this repo *and* others (Oficio) — e.g. the secret-rotation
runbook — does not live in a single code repo. It lives in `~/zed/second_brain`
(`github.com/borinquenkid/second-brain`, private), which has cross-repo visibility that no single
code repo has. Pending cross-repo ops tasks land in that repo's `ops/inbox/`; completed runbooks
land in `ops/runbooks/`. Don't duplicate that content here — link to it instead.

---

## Core Architecture

The application follows a strict data flow to consolidate academic data into a single, synchronized "Source of Truth."

*   **Inputs (Sources):**
    *   **External Calendars:** Read-only feeds from Google Calendar, Microsoft Outlook, or iCal (.ics) URLs.
    *   **Syllabus:** Course documents containing deadlines and deliverables parsed via AI.
    *   **Class Documents:** Supporting materials belonging to specific courses.
*   **Logic Layer (The Agents):**
    *   **`IngestionAgent`:** Manages intelligent extraction and structuring of raw content into high-fidelity `SourceFragments`.
    *   **`EventAgent`:** Consumes structured content to generate both direct Deliverables and proactive Study Plans using high-context reasoning.
    *   **`NormalizationService`:** Provides programmatic safeguards to standardize event categories and deduplicate entries.
    *   **`CalendarAgent`:** Intelligent gateway to the student's schedule, managing synchronization and conflict resolution across providers.
*   **The Object (Event):** All inputs are parsed into a unified `Event` model.
*   **The Destination:** All generated events are synchronized into the **Student's Master Calendar**.

---

## Features

*   **Sources Panel:** Manage inputs from Local Files, URLs (Public/Private), and Google Drive.
*   **Academic Calendar:** A central, editable dashboard that aggregates and synchronizes events from all sources.
*   **AI Integration:** Performs automated analysis of Syllabi and Documents to extract events and suggest proactive study blocks.
*   **Synchronization:** Handles the push/pull logic between the app's internal state and external calendar providers.

---

## AI Strategy

*   **Primary Engine:** **Gemini 1.5 Flash** (via REST API) — default engine for syllabus parsing and event extraction due to its 1-million-token context window and stability.
*   **Model Auto-Negotiation:** `ModelManager` caches the best available model in SQLite and retries on quota exhaustion with a fallback preference list.
*   **Privacy-First Setup:** Students provide their own free Gemini API key via Google AI Studio, keeping data within their own Google ecosystem.

---

## Testing Requirements

All business logic classes (Models, Agents, Services, and Utilities) MUST have associated unit tests using the Kotest framework. Any new business logic introduced must be accompanied by corresponding tests.

*   **Mocking:** Use `MockK` for unit tests.
*   **Network Testing:** Use Ktor `MockEngine` to verify API interactions (for Sync services).
*   **Integration Tests:** Headless IT tests verify full analysis pipelines using real dev keys and in-memory databases.
*   **UI Tests:** Compose tests for key screens and dialogs verifying state changes and user interactions.

### Integration Test Naming Convention

Any test class that makes **real external calls** (Gemini API, Google Calendar, live OAuth endpoints) **must** include `IntegrationTest` in its class name (e.g. `StlccIntegrationTest`, `IcsToGoogleIntegrationTest`). Tests that use `MockEngine`, `mockk`, or an in-memory database only — even if they wire up multiple components — do **not** qualify and must **not** carry the `IntegrationTest` suffix.

**Why naming, not tags:** Kotest 6's tag-filtering system property (`kotest.filter.tags`) is not reliably forwarded to the test JVM when Gradle reuses a daemon. Both `tags(Flaky)` in the spec body and `@Tags("Flaky")` class annotations were tried and failed to skip tests in practice.

**Default run — unit tests + fully-mocked tests (no API calls):**
```bash
./gradlew :composeApp:jvmTest
```
This excludes every class whose name contains `IntegrationTest` or `ContributorPdf`.

**To include AI/integration tests** (CI or explicit manual run):
```bash
./gradlew :composeApp:jvmTest -PrunAITests=true
```

**Running a single integration test class:**
```bash
./gradlew :composeApp:jvmTest --tests "com.borinquenterrier.cef.StlccIntegrationTest"
```

> **Warning — Android Studio "Run Tests" / `composeTest`:** Android Studio's built-in test runner may not pass `-PrunAITests`, but if your dev machine has a `CEF_GEMINI_API_KEY` or `GOOGLE_REFRESH_TOKEN` set, any leaked real-API test (one named without `IntegrationTest`) will still hit the live API. Always verify new test files follow the naming rule above.

Never use Kotest tags (`object Flaky : Tag()`, `tags(Flaky)`, `@io.kotest.core.annotation.Tags`) to gate integration tests in this project.

### AI Eval Corpus Gate (CI cadence & cost)

Three test classes are "eval-shaped" — they assert real extraction quality against hand-labeled/depth-checked expectations, not just "did it not crash": `SyllabusEvaluationIntegrationTest` (recall/date-accuracy thresholds against 2 hand-labeled syllabi), `ContributorPdfIntegrationTest` (depth assertions across the full 16-file `contributions/` corpus), and `StlccIntegrationTest` (per-document dedup/stability assertions on 3 STLCC docs). These run in `.github/workflows/eval-corpus.yml`.

**Cadence: nightly (`schedule: cron '0 8 * * *'`) plus manual `workflow_dispatch`, not on every PR.** A full run makes roughly 20-60 real Gemini calls against the **BorinquenTerrier paid (Tier 1)** `CEF_GEMINI_API_KEY` (see "Ops gotchas" above) — not the free-tier key students provide at runtime. The free-tier "all models share one RPM/RPD quota" failure mode documented in `ROADMAP.md`'s "Observed failure mode (June 2026)" note is about that per-user runtime key, not this CI secret. Even on the paid key, running this corpus on every PR would still compete for its quota against concurrent PRs and the app's own runtime usage, and would risk a rate-limit failure unrelated to the PR's actual change. Nightly gives same-day regression detection (a bad prompt/model/parser change merged today is caught by tomorrow morning) without that contention.

**Secret:** `CEF_GEMINI_API_KEY` is stored as a GitHub Actions repo secret used *only* by this workflow — it is never wired into `generateBuildSecrets` (`composeApp/build.gradle.kts`) or any packaging/release task, so it never ships inside the app binary. This is a scoped exception to the rule that the Gemini key is normally a per-user runtime `Settings` value (see `SettingsScreen.kt` / `AIService.jvm.kt`) — CI-test-only use was explicitly approved; build-time baking was not and remains off-limits.

**Fail loud, not silent:** `resolveApiKey()` returns `null` and *skips* the test (not fails) when no key is found — the exact silent-green failure mode `HARD-1` exists to catch elsewhere. The workflow's "Verify Gemini API key is configured" step fails the job outright if the secret is empty, so a revoked/deleted secret shows up as a red job rather than a suite of quietly-skipped tests.

---

## Development Roadmap

> See [ROADMAP.md](ROADMAP.md) for the full prioritized plan with dependency graph and implementation details.

### Completed

*   UI Scaffolding, General Styling, File Picker (Desktop, Android, iOS)
*   Settings Screen (API key, Google auth, drive settings)
*   Unified Event Model (`TimeEvent`, `DayEvent`, `SyncStatus`, `AcademicCategory`)
*   Routine Management — full create/view/persist recurring schedule
*   Calendar View — events from all sources, grouped by date
*   Testing Framework — Kotest, MockK, ~33 test files (unit + integration)
*   iCalendar Parsing — `.ics` → `SourceFragments` via `IcsCalendarSource`
*   Google Calendar REST Integration — fully KMP-compatible sync via Ktor
*   OAuth2 Authentication — JVM local-server flow + persistent token storage
*   AI Integration — Gemini REST, model auto-negotiation, SQLite model cache
*   Agentic Architecture — `IngestionAgent`, `EventAgent`, `CalendarAgent`, `NormalizationService`, `ContextAgent`
*   Multi-Format Extraction — PDF/DOCX native on Android, iOS, JVM
*   Native Mobile Auth — `GoogleSignInClient` (Android), `ASWebAuthenticationSession` (iOS)
*   AI Study Plan Constraints — 9–21 hr limits, lunch/dinner breaks, collision resolution
*   Debug Logging, Automatic Schema Migrations
*   Recursive Task Decomposition — `DecompositionOrchestrator` (depth-3 FIFO), full Kotest specs
*   Automatic Source Categorization — `IngestionAgent` calls `categorizeSource()` on all non-ICS content
*   "Break It Down" UI — `TaskDecompositionDialog` wired end-to-end for DEADLINE/FINALS events
*   Two-Way Sync — all four mutation scenarios verified by `CalendarSyncTest`
*   Multi-Source Chat Context — `ContextAgent.queryAllSources()`, conversation history, scope toggle
*   Critic-Actor Loop — `CriticActorAIService` with graph-based cycle/oscillation detection
*   .ics Export — `ICalGenerator` + expect/actual `writeIcsFile` (Downloads on JVM/Android, Share Sheet on iOS)
*   Sync Hardening — token refresh on 401, `pageToken` pagination, conflict resolution warnings
*   Visual Progress Tracking — `timeUntilDue`, `studyProgress`, countdown chips, Semester Health card
*   Scheduling Fine-Tuning — user-configurable study hours/breaks in Settings, injected into AI and resolver
*   Weighted Deliverables — grade weights extracted by AI, stored in `Event`, used for proportional study block allocation
*   Observability & Telemetry — `TelemetryManager` logging rate limits, parse exceptions, Critic-Actor outcomes
*   Client Secrets Management — automated build-time injection via custom Gradle task
*   Stateful User Preference Memory — track manual edits, derive implicit constraints, inject as prompt rules
*   Sync Re-negotiation UI — interactive proposal diff dialog replacing silent conflict resolution
*   Active Lifecycle Agent Harness — `AgentHarness` polling at startup and once daily
*   Token-Efficient Source Processing — analysis cache, mutex sequential queue, and global hold strategy
*   Custom Google Calendar Selection UI — fetch available calendars, save selection to preferences, and target chosen calendar during sync
*   Stale OAuth Connection Resolution — automatically detect invalid refresh tokens at startup in GoogleAccountFlow and transition cleanly to Unlinked
