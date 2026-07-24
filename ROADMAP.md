# College Executive Function — Development Roadmap

> **Source of truth for all planned work.** `AGENTS.md` provides mandates and architecture context.
> **CRITICAL PRIORITY**: High-complexity, low-coverage files are the primary source of bugs.
> Phases are ordered: (1) Complexity Remediation, (2) User-Reported Issues, (3) New Features.
> Within phases, items are ordered by user impact × implementation readiness.
>
> **Tooling note (2026-07-05):** the CRAP-index tooling (`CrapIndexReporter.kt`, `CRAP.md`,
> `COVERAGE.md`) referenced throughout the phase write-ups below has been retired and
> replaced with SonarQube — see AGENTS.md's "Static Analysis Quality Gate Protocol" and
> `docs/ops/sonarqube-local.md`. The phase entries below are a historical record of what
> each one measured at the time and are left as-is; new work should measure against
> `./gradlew :composeApp:checkQualityGate` instead.

---

## 🎯 Current Status (June 2026)

**Current Phase: All desktop/mobile phases complete (through Phase 9)** — Phase 9 done: window title, Studio FAB polish, and Drive picker manually verified end-to-end with a real Google account (search, chips, sorted rows all confirmed working 2026-06-25). **Phase 6b (Web Client & AG-UI Protocol Integration)**: 6.1–6.4 done. 6.2's SSE endpoint (real timestamps/runId, JSON escaping, real Critic-Actor loop wiring) was completed 2026-07-04. **6.5 (Dynamic Agentic UI Views) is next** — the React client still renders only a single fixed reasoning line and the server still streams the final answer as one chunk; see Phase 6b for the gap list. (Phase 0.25's `HttpOtelTracer` tests were found already complete on 2026-07-04 and deprioritized.) **Phase 10 (Hardening Pass)** is done — certification gate cleared 2026-07-05. **Phase 11 (Supply-Chain Hardening)** is proposed and prioritized above Phase 12 — see [`docs/ops/supply-chain-hardening.md`](docs/ops/supply-chain-hardening.md); not started. **Phase 12 (Outlook/Microsoft 365 Calendar Provider)** is proposed — see [ADR-004](docs/decisions/ADR-004-outlook-microsoft-365-calendar-provider.md) and the task breakdown below; A.1–A.2 of the Azure setup are done (see [`docs/ops/microsoft-azure-app-registration.md`](docs/ops/microsoft-azure-app-registration.md)), MS-1 onward not started, and is paused behind Phase 11 per an explicit priority call (hardening over new features). **Phase 13 (Eval Baseline/Delta + Cross-Term Memory) is DONE as of 2026-07-10** — see [ADR 0004](docs/adr/0004-eval-baseline-delta-and-cross-term-memory.md); EB-1/EB-2/EB-3/XM-1..5 all implemented and tested (commit `9271731`), including EB-2's initial baselines recorded against a real live-Gemini run and committed (`evals/baseline_*.json`). `TermBoundaryTrigger` is wired to a real invocation site (`CalendarAgent.synchronize()` — see XM-3). SonarQube quality gate re-verified and passing (`new_coverage: 90.2 ≥ 80`, `new_duplicated_lines_density: 0.0 ≤ 3`, `new_violations: 0`) — the earlier "expired `SONAR_TOKEN`" note was actually a Keychain-sourcing issue, not a dead token (see `docs/ops/keychain-secrets-migration.md`'s 2026-07-10 OOC section); the real gate run caught one genuine pre-existing `kotlin:S6310` (hardcoded dispatcher) violation in `TermProfileRepository.kt`, fixed to match the already-established `SqlDelightChatRepository` convention (injected `CoroutineDispatcher` param, defaulted to `Dispatchers.Default`). **Phase 14 (Accessibility Conformance — WCAG 2.1 AA + VPAT)** is proposed — see [ADR 0011](docs/adr/0011-accessibility-conformance-target-and-vpat.md); triggered by a 2026-07-23 request for defensible "ADA compliant" marketing language, which the project cannot currently back (ADR 0009 fixed real defects but set no conformance level, has no VPAT, and no automated a11y regression tests beyond static linting). AC-1 (this ADR) is done. AC-2 (Vitest + RTL + vitest-axe test infra, wired into CI) is done as of
2026-07-24 — found and fixed a real `heading-order` violation on the Calendar and Settings tabs
along the way. AC-3 (contrast audit) is done as of 2026-07-24 — one real live violation
(`.btn-primary`/`.chat-msg.user` white-on-purple, 3.95:1) fixed, plus `--text-muted` lightened;
also found that jsdom has no layout engine at all, so axe's `color-contrast` rule can never run
meaningfully in the AC-2 Vitest suite (confirmed, not fixable there) — closed for real the same day
with a second, browser-backed test layer (Playwright + `@axe-core/playwright`, `web/e2e/`), added
after discussing the tradeoff with the user; verified it actually catches the real bug AC-3 found by
reverting the fix and confirming all 6 specs fail, then restoring it. AC-4's keyboard-only pass is
done as of 2026-07-24 (found and fixed a real WCAG 2.1.1 failure — the Sources tab's file-upload
dropzone was completely unreachable by keyboard); its VoiceOver/NVDA passes are open — those
genuinely need a human, not automated tooling. AC-5 onward not started. **Phase 15 (Decouple Upload from Processing) is DONE as of 2026-07-24** — see [ADR 0012](docs/adr/0012-decouple-upload-from-processing.md); triggered by the same 2026-07-23 demo rehearsal, which surfaced that `POST /api/sources` holds one HTTP request open for the entire 20-30+s AI pipeline with no phase visibility. AU-1 through AU-5 all implemented and tested, verified with a live manual smoke test and the full four-target build + Sonar Quality Gate.

### CRAP Remediation Progress (Phases 0.1–0.8)

| Phase | Target File(s) | Status | Completed | Notes |
|---|---|---|---|---|
| 0.1 | GeminiAIService.kt | ✅ DONE | Phase 0.13 | GeminiRequestExecutor extracted |
| 0.2 | SettingsScreen.kt | ✅ DONE | Phase 0.X | Preferences parser extracted |
| 0.3 | AppController.kt | ✅ DONE | Phase 0.X | Sync/polling orchestration extracted |
| 0.4 | AcademicCalendar.kt | ✅ DONE | Phase 0.X | Layout & event filtering decomposed |
| 0.5 | ContextAgent.kt | ✅ DONE | Phase 0.X | Fragment ranking & aggregation extracted |
| 0.6 | AiPrompts.kt | ✅ DONE | Phase 0.X | Prompt builders decomposed |
| 0.7 | CollisionResolver.kt | ✅ DONE | Phase 0.X | Scheduling algorithms extracted |
| 0.8 | AgentHarness.kt | ✅ DONE | Phase 0.14 | Expanded test coverage for extracted services |
| **0.9+** | **File Ingestion Services** | 🔄 IN PROGRESS | **Phase 0.17** | DriveFileScanner, LocalFileScanner, DirectoryPreferencesManager |

---

## ⚠️ Known Issues / Tech Debt

| Issue | Notes |
|---|---|
| `GoogleOAuthIntegrationTest` enabled in CI | Renamed from `ModelNegotiationIntegrationTest` to correctly reflect that it verifies Google sign-in credentials. Configured to run in CI (`pr-check.yml` & `release-desktop.yml`) only when secrets are available, ensuring we validate client secrets before merging/building. |
| **Internationalization (i18n)** | Found during the 2026-07-05 TestFlight pass: the Semester Window date fields hardcoded a "YYYY-MM-DD" (ISO-8601, dash-separated) format, which isn't the standard US date convention and confused a real tester. Fixed the immediate case by replacing free-text entry with a native calendar picker (`DatePickerField.kt`) + a locale-neutral "Jul 5, 2026" display format (`DateDisplayFormatter.kt`) — see git history around 2026-07-05. That's a targeted fix, not real i18n: there's still no locale-aware date/number/string formatting anywhere in the app, and all user-facing strings are hardcoded English with no localization framework. **Not scoped or prioritized yet** — flagging so it doesn't get silently forgotten. Needs its own Clarify Protocol pass (which locales/languages first, string-resource strategy across Android/iOS/Desktop/JVM, RTL support scope) before planning.|
| **iOS native Calendar (EventKit) instead of Google Calendar API** | Raised during the 2026-07-05 TestFlight pass. Idea: introduce a `CalendarProvider` abstraction so Android/Desktop keep syncing to Google Calendar via the existing API path, but iOS writes events via EventKit into the device's native Calendar app instead. Upside: drops the Google `calendar` OAuth scope entirely on iOS, and events show up in whatever calendar app the user actually uses (which can itself sync to Google/Outlook/etc. via iOS's own account settings). Cost: a new provider interface, an EventKit permission flow, and an iOS-specific port of the dedup/sync logic that `EventGenerationService` currently does for Google Calendar. **Not scoped or prioritized yet** — deliberately deferred past the current App Store submission push; needs its own design pass before work starts. |


---

## 🔴 PRIORITY: CRAP Index Remediation (Phases 0.1 – 0.17+)

### Phases 0.9+ — File Ingestion & Infrastructure Decomposition

These phases continue the CRAP remediation strategy across file ingestion and preference management services:

| Phase | Work Item | Status | Commit |
|---|---|---|---|
| 0.9–0.12 | Initial decomposition passes (not yet documented) | ✅ Implied complete | — |
| 0.13 | Extract `GeminiRequestExecutor` from AI service orchestration | ✅ **COMPLETED** | a526489 |
| 0.14 | Expand test coverage for Phase 0.8 extracted services | ✅ **COMPLETED** | e9703e9 |
| 0.15 | Decompose `DriveFileScanner` service | ✅ **COMPLETED** | 02d79cd |
| 0.16 | Decompose `LocalFileScanner` service | ✅ **COMPLETED** | ef9f85d |
| 0.17 | Decompose `DirectoryPreferencesManager` | ✅ **COMPLETED** | 5f6eea8 |
| 0.18 | Decompose `GeminiErrorHandler` (CRAP 110.00) | ✅ **COMPLETED** | 3d98e9b |
| 0.19 | Test coverage for `PreferenceSerializer` (CRAP 56.00) | ✅ **COMPLETED** | (Phase 0.19) |
| 0.20 | Decompose `DriveFileFetcher` (CRAP 72.00) | ✅ **COMPLETED** | 95f8ddf |
| 0.21 | Decompose `DirectoryPreferencesManager` (CRAP 72.00) | ✅ **COMPLETED** | 4c653d0 |
| 0.22 | Decompose `ContextAgent` (CRAP 31.03) | ✅ **COMPLETED** | dd5782a |
| 0.23 | Refactor Event & Study Plan prompts to ADR-002 | ✅ **COMPLETED** | 26ee8ee |
| 0.24 | Refactor ChatBuilder prompts to ADR-002 | ✅ **COMPLETED** | (Phase 0.24) |
| 0.25 | Add Unit Tests for HttpOtelTracer | ⏳ **NEXT** | — |

---

### Phase 0.18 — GeminiErrorHandler Decomposition ✅ **COMPLETED**

**Highest-risk file remaining.** Error handling is critical for reliability; zero coverage creates blind spots.

**Deliverables:**
1. ✅ `RetryAfterParser` — parses Retry-After delays from error responses (14 test cases, 100% coverage)
2. ✅ `ErrorCategorizer` — classifies errors (quota, auth, structural, server) delegating to specialized services (13 test cases, 100% coverage)
3. ✅ `QuotaExhaustionDetector` — detects quota exhaustion vs transient rate limits (13 test cases, 100% coverage)
4. ✅ `GeminiErrorHandler` refactored as thin facade — delegates to ErrorCategorizer, reduced complexity
5. ✅ GeminiRequestExecutor updated to use new services

**Results:**
- Added 40 new unit tests (all passing)
- Each extracted module: CRAP < 15, coverage 100%
- GeminiErrorHandler: complexity reduced from 10 to 3, becomes pure router
- All existing error handling behavior preserved

**Dependencies:** None — standalone refactor. Completed 2026-06-09.

---

### Phase 0.19 — PreferenceSerializer Test Coverage ✅ **COMPLETED**

**Target:** Add comprehensive unit tests for serialization/deserialization (CRAP 56.00)

**Deliverables:**
1. ✅ `PreferenceSerializerTest.kt` — 24 test cases covering round-trip serialization, null/blank handling, edge cases
2. ✅ Tests verified 100% functional; coverage detection pending (Kover instrumentation may need tweaking)

**Results:**
- Added 24 unit tests (all passing)
- Verified `kotlinx.serialization` usage is correct (not hand-coded parsing)
- Established pattern for serialization testing

**Completed 2026-06-09.**

---

### Phase 0.20 — DriveFileFetcher Decomposition ✅ **COMPLETED**

**Target:** Decompose concurrent file fetching and deduplication (CRAP 72.00)

**Deliverables:**
1. ✅ `ConcurrentFolderFetcher` — async orchestration with error isolation (Complexity 6)
2. ✅ `FileDuplicateFilter` — deduplication logic (Complexity 2)
3. ✅ `DriveFileFetcher` — refactored as thin facade (Complexity 8→3)
4. ✅ Tests: 23 test cases (FileDuplicateFilterTest 8, ConcurrentFolderFetcherTest 8, SourceCountTest 7)

**Results:**
- Complex async logic isolated in ConcurrentFolderFetcher
- Deduplication logic easily testable in FileDuplicateFilter
- DriveFileFetcher coordinates both services with minimal logic

**Completed 2026-06-09.**

---

### Phase 0.21 — DirectoryPreferencesManager Decomposition ✅ **COMPLETED**

**Target:** Decompose preference management across local and GDrive directories (CRAP 72.00)

**Deliverables:**
1. ✅ `LocalDirectoryPreferences` — manages local directory preferences (Complexity 3)
2. ✅ `DriveDirectoryPreferences` — manages GDrive folder preferences (Complexity 3)
3. ✅ `DirectoryPreferencesManager` — refactored as thin facade (Complexity 8→2)
4. ✅ Tests: 24 test cases (DirectoryPreferencesManagerTest 8, LocalDirectoryPreferencesTest 8, DriveDirectoryPreferencesTest 8)

**Results:**
- Preference management patterns isolated and independently testable
- Facade coordinates both preference managers with minimal logic
- DirectoryPreferencesManager removed from top-15 high-risk list

**Completed 2026-06-09.**

---

### Phase 0.22+ — Coverage & Infrastructure (Planned)

**Next targets:** ConcurrentFolderFetcher (CRAP 56) and PreferenceSerializer (CRAP 56) need coverage detection verification.

**PreferenceSerializer.kt status:** ✅ Already using `kotlinx.serialization` (not hand-coded). Issue is **0% coverage**. Phase 0.19 should add unit tests for:
- Successful serialization/deserialization round-trips
- Null/blank string handling
- Exception paths (malformed JSON, truncated strings)

**General serialization rule:** All new serialization should use `@Serializable` + `kotlinx.serialization`, never hand-coded JSON/XML parsing. Hand-coded parsing is a bug vector and diverges from codebase standard.

---

## 🔴 PRIORITY: CRAP Index Remediation (Phases 0.1 – 0.8) — Original Plan

High CRAP scores indicate high risk of bugs. Per `AGENTS.md`, high-complexity files should be **decomposed into smaller, single-responsibility modules BEFORE adding tests** — splitting reduces complexity² sharply. See `CRAP.md` for current metrics.

### Phase 0.1 — GeminiAIService.kt (CRAP 67.50 → Target < 40)
**Highest risk.** Complexity 56, Coverage 84.6%. Contains all AI interaction logic (retry, categorization, events, chat, model negotiation).

**Decomposition Plan:**
1. Extract `RetryStrategy` + backoff logic → new `GeminiRetryService`
2. Extract model negotiation → new `GeminiModelNegotiator`
3. Extract JSON parsing → new `GeminiResponseParser` (partially done; complete it)
4. Extract response builders → new `GeminiPromptBuilder`
5. Leave `GeminiAIService` as thin facade coordinating calls

**Acceptance:** CRAP < 40, each extracted module has CRAP < 15.

---

### Phase 0.2 — SettingsScreen.kt (CRAP 57.03 → Target < 25)
**Second highest risk.** Complexity 35, Coverage 73.8%. UI component with heavy business logic.

**Decomposition Plan:**
1. Extract preference parsing → `SettingsPreferencesParser` (partially done; complete coverage)
2. Extract API key validation → `ApiKeyValidator`
3. Extract Google auth flow → `GoogleAuthSettingsFlow`
4. Extract drive settings → `DriveSettingsPanel` (separate Composable)
5. Leave `SettingsScreen` as pure layout + delegation

**Acceptance:** CRAP < 25, UI component has 0% coverage (acceptable for pure UI), logic modules > 80% coverage.

---

### Phase 0.3 — AppController.kt (CRAP 30.51 → Target < 15)
**Third priority.** Complexity 20, Coverage 70.3%. Central orchestrator; low complexity but low coverage drives risk.

**Decomposition + Coverage Plan:**
1. Extract sync logic → new `SyncOrchestrator`
2. Extract agent polling → new `AgentPollingService`
3. Add integration tests for state transitions
4. Target 90%+ coverage on extracted modules

**Acceptance:** CRAP < 15, coverage > 85%.

---

### Phase 0.4 — AcademicCalendar.kt (CRAP 34.21 → Target < 20)
**Compose UI with logic.** Complexity 29, Coverage 81.6%.

**Decomposition Plan:**
1. Extract event filtering → `EventFilterService`
2. Extract layout logic → separate `CalendarListPanel`, `CalendarDetailsPanel` Composables
3. Extract event mutations → `CalendarEventMutationHandler`
4. Add Compose UI tests for key interactions

**Acceptance:** CRAP < 20, pure Composables exempt from coverage but logic modules > 80%.

---

### Phase 0.5 — ContextAgent.kt (CRAP 31.03 → Target < 20)
**Logic with moderate complexity.** Complexity 31, Coverage 96.8% (already excellent).

**Refactoring Plan:**
1. Extract TF-IDF ranking → `FragmentRanker`
2. Extract context aggregation → `ContextAggregator`
3. Extract prompt injection → `ContextualPromptBuilder`

**Acceptance:** CRAP < 20, coverage remains > 95%.

---

### Phase 0.6 — AiPrompts.kt (CRAP 41.30 → Target < 25)
**Complexity 41, Coverage 94.4%** (already high coverage; pure decomposition).

**Refactoring Plan:**
1. Extract study plan constraints → `StudyPlanPromptBuilder`
2. Extract categorization rules → `CategorizationPromptBuilder`
3. Extract event extraction → `EventExtractionPromptBuilder`
4. Extract chat system prompts → `ChatSystemPromptBuilder`
5. Leave `AiPrompts` as coordinator

**Acceptance:** CRAP < 25, maintain > 90% coverage.

---

### Phase 0.7 — CollisionResolver.kt (CRAP 41.09 → Target < 20)
**Complexity 41, Coverage 96.3%** (excellent coverage; pure decomposition).

**Refactoring Plan:**
1. Extract scheduling algorithm → `SchedulingAlgorithm`
2. Extract constraint validation → `ScheduleConstraintValidator`
3. Extract collision detection → `CollisionDetector`

**Acceptance:** CRAP < 20, maintain > 95% coverage.

---

### Phase 0.8 — AgentHarness.kt (CRAP 38.41 → Target < 20)
**Complexity 37, Coverage 89.9%.**

**Refactoring Plan:**
1. Extract directory polling → `DirectoryPoller`
2. Extract file ingestion orchestration → `FileIngestionOrchestrator`
3. Extract sync coordination → `SyncCoordinator`

**Acceptance:** CRAP < 20, maintain > 85% coverage.

---

## 🆕 Planned Work & User-Reported Issues

### 🔜 v2.1 — Chat History & Compaction ⏳ **SCOPED** (2026-07-06)
"Managing the chat." Make the multi-source AI chat **persistent and context-safe**. Today chat is
in-memory only (wiped on restart; no chat table) and context-naive (flat prompt, `takeLast(10)`,
zero token accounting). Two halves:
* **Chat history (customer):** multiple named conversations that survive restart, each with a
  pinned source scope; new / rename / delete / switch.
* **Compaction (model):** per-model context windows + a token budget + a **rolling summary** so
  long chats stay in-window instead of dropping turns or erroring on size; plus oversized-request
  recovery and `maxOutputTokens`.
* **Full design + data model + phasing:** [docs/design-2.1-chat-history-and-compaction.md](docs/design-2.1-chat-history-and-compaction.md).
* **Phase 1 (Persistence) ✅ DONE:** typed `ChatMessage`/`ChatRole`, `Conversation`/`ChatMessage`
  tables, `ChatRepository`, and startup hydration — chat now survives restart (single implicit
  conversation).
* **Phase 2 (Management UI) ✅ DONE:** conversation drawer (list, switch, new/rename/delete),
  per-chat source scope pin, first-message title derivation.
* **Phase 3 (Compaction) ✅ DONE:** `TokenEstimator`, `ModelContextWindow`, per-turn budget
  allocation, and a rolling summary that replaces the naive `takeLast(10)` cut — long chats now
  stay in-window instead of silently dropping early context. Next: Phase 4 (Robustness —
  oversized-request recovery, `maxOutputTokens`, critic-cost handling, `querySource` unification).
* Also folds in the drop/withdrawal-deadline-as-`DEADLINE` thread (NormalizationService fix already
  on `main`).

---

### Phase 1 — Custom Google Calendar Selection UI ✅ **COMPLETED**
Add ability to fetch available Google Calendars, save the selected calendar ID/name to preferences, and configure the synchronization pipeline to target the chosen calendar. This enables flexible desktop testing using specific test calendars instead of hardcoding target IDs.
* **Status**: ✅ Completed

---

### Phase 2 — Google Calendar, Gemini Quota, and OAuth Improvements ✅ **COMPLETED**
These are user-reported issues and feature requests targeted for the next development cycles:
* **Target Google Calendar Creation Capability** (Feature Request) ✅ **COMPLETED**
  * **Description**: The app currently only allows picking from existing calendars retrieved from the user's Google Account. There is no option in the settings UI to create a *new* calendar.
  * **Proposed Solution**: Add a "Create New Calendar" button/dialog in `SettingsScreen.kt` that calls `GoogleCalendarSyncService.createCalendar(name)` to instantiate a fresh calendar directly from the app.
* **Gemini API Daily Quota Rate Limit Issue** — ⚠️ **Superseded by Phase 6** (see below) ✅ **COMPLETED**
  * **Description**: Ingesting a calendar and two syllabi simultaneously frequently triggers a `QuotaExhausted: Rate limit reached` error due to multiple concurrent requests.
  * **Superseded solution**: Full plan in **Phase 6** — content-addressable cache (6.1), sequential processing queue (6.2), global rate-limit hold strategy (6.3).
* **Google OAuth Stale Connection / JSON Auth Error** ✅ **COMPLETED**
  * **Description**: On startup, if local session tokens are stale/expired, the connection shows a raw JSON authentication error. Disconnecting and reconnecting resolves it.
  * **Proposed Solution**: Auto-detect invalid refresh tokens at startup inside `GoogleAccountFlow` and transition the status cleanly to `Unlinked` instead of throwing raw JSON error messages.

---

### Phase 3 — Web Ingestion REST Endpoints (ADR 0001 Happy Path)
Implement the missing REST endpoints on the Ktor server to support the React web client's file ingestion, event loading, and settings management as described in [SPEC.md](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/SPEC.md).
* **Status**: ⏳ **IN PROGRESS**
* **Tasks**:
  1. Add Ktor REST endpoints to [Application.kt](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/server/src/main/kotlin/com/borinquenterrier/cef/Application.kt) delegating to a clean [WebIngestionController](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/server/src/main/kotlin/com/borinquenterrier/cef/WebIngestionController.kt).
  2. Implement multipart file upload and URL processing in [WebIngestionController](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/server/src/main/kotlin/com/borinquenterrier/cef/WebIngestionController.kt).
  3. Implement event retrieval, source listing/deletion, and settings persistence.
  4. Write E2E integration tests in Ktor test server verifying file/URL ingestion and deletion flows using checked-in test documents (e.g. `sample.pdf`).

---

### Phase 4 — Multi-Tenant Institutional Scaling ([ADR 0002](docs/adr/0002-multi-tenant-docker-path-partitioned-storage.md)) ✅ **COMPLETED**
Implement the database-per-student, connection caching, Litestream replication, and async worker pool architecture, then actually wire it into the live server and give it a deployment story.
* **Status**: ✅ Completed
* **Tasks**:
  1. ✅ Implement hashed database-per-student sharding and an LRU connection cache to prevent handle leaks.
  2. ✅ Isolate student settings and Google OAuth tokens in their sharded SQLite database files instead of a global shared JVM preference store.
  3. ✅ Create a coroutine-based async ingest worker pool to isolate document parsing and vector indexing from the main HTTP thread pool.
  4. ✅ Wire `ServerContainer` to use `TenantSettingsFactory` instead of the global `PreferencesSettings` instance.
  5. ✅ Set up Litestream parameters and nightly compacted snapshot backups (`VACUUM INTO`).
  6. ✅ Implement an automated multi-database schema migration runner to run upgrades across all active tenant files.
  7. ✅ Wire `X-Student-ID`-based tenant resolution into every live HTTP route in `Application.kt` (previously only reachable via direct test construction) — validated against path-traversal via `^[A-Za-z0-9_-]{1,128}$` before it reaches any file-path-building code.
  8. ✅ Give the server + web client a real deployment story: `server/Dockerfile`, `web/Dockerfile`, `docker-compose.yml`, one mounted volume with the existing hash-partitioned tenant storage. See ADR 0002.

---

## ✅ User-Identified Issues & UX Enhancements (Completed June 2026)

These are the immediate issues identified by the user regarding source management, input validation, key interactions, copy/paste functionality, and quota error friendliness.

*   **Source Deletion Capability**: Added `deleteSource(sourceId)` to `SourceRepository`/`SqlDelightSourceRepository`. Wired the UI (`SourcesPanel`, `SourceItemView`) with a delete button to allow physical removal of sources and their associated calendar events, followed by database synchronization.
*   **Syllabus & Calendar Classification & Validation**:
    *   Restructured `SourceCategory` to include `CALENDAR` alongside `SYLLABUS`.
    *   Updated the AI categorization prompt in `AiPrompts.kt` to classify raw text strictly as either `Syllabus` or `Calendar`.
    *   Added semantic validation rules:
        *   A **Calendar** must contain at least one day-long event, deadline, or holiday.
        *   A **Syllabus** must contain at least one repeating meeting time or deliverable (quiz, homework, test with deadline).
    *   Modified `GeminiResponseParser` to parse the `isValid` and `reason` fields returned by the AI, throwing a `SourceValidationException` if validation fails.
    *   Updated `IngestionAgent` to classify `.ics` files as `CALENDAR` and validate that they contain at least one event.
*   **Submit on ENTER Key**: Intercepted Enter key presses in the chat panel input field (`ChatPanel.kt`) and URL input dialog (`CommonSourceProviders.kt`) using Compose `onKeyEvent` to trigger submission automatically.
*   **Copy/Paste (c/p) Interaction**:
    *   Wrapped chat bubbles in `SelectionContainer` to enable highlighting and copying text.
    *   Added a dedicated "Copy" icon button to both user and AI message bubbles for single-click copy to the clipboard.
    *   Added a native `MenuBar` in `main.kt` containing an "Edit" menu (Cut, Copy, Paste, Select All) to support native OS copy/paste interaction on macOS.
*   **Friendly Quota & Rate Limit Errors**:
    *   Refactored `GeminiAIService.executeWithRetry` and rate limit handling to throw clean, user-friendly messages instead of using unfriendly technical terms like "ms" or "exceeds threshold".
    *   Implemented a static `rateLimitResetTime` tracker. If a rate limit (429) is hit, subsequent requests fast-fail immediately during the lockout window, showing a remaining wait time in seconds that correctly decreases over time rather than increasing.

---

## ✅ Phase 9 — Polish: Window Title, Studio FAB, Drive Picker Verification ✅ COMPLETE

Three issues found in the 2026-06-25 contrarian audit. No architecture changes — all are small, targeted, low-risk.

### 9A — Window title `CollegeExecutiveFunction` → `College Executive Function` ⏳ PLANNED

**Root cause:** `main.kt:12` passes the Kotlin class name as the display title. Visible in macOS title bar and ⌘+Tab.

**Fix:** `title = "College Executive Function"` (1-line change, `main.kt:12`)

---

### 9B — Studio FAB: `tertiaryContainer` (pink) → `surfaceVariant` + tooltip + label ⏳ PLANNED

**Root cause:** `Theme.kt` sets `tertiary = Pink40/Pink80`. M3 derives a pink/rose `tertiaryContainer`. On the lavender UI, pink reads as "error state" to users — especially AuADHD students who are attuned to visual affordances. No tooltip or label explains what the wrench does.

**Files changed:**

| File | Change |
|---|---|
| `UniversalHomeLayout.kt:163` | `containerColor = MaterialTheme.colorScheme.surfaceVariant` (matches Sources FAB's neutral tone) |
| `UniversalHomeLayout.kt:168` | `contentDescription = "Open AI Studio panel"` (screen reader + tooltip text) |
| `UniversalHomeLayout.kt` | Wrap FAB in M3 `TooltipBox` to show "AI Studio" label on hover |

**CRAP acceptance:** No complexity change. Pure color/accessibility edit.

---

### 9C — Drive picker manual verification gate ⏳ NEEDS HUMAN

**Problem:** The Phase 7 search/chips/typed-rows UI was built and unit-tested but never exercised end-to-end. A layout or rendering bug could exist that 16 unit tests would never catch.

**What to verify (manual walkthrough):**
1. Connect a Google account in Settings
2. Click the Sources (left) FAB → "Add from Google Drive"
3. Confirm: search field visible at top, chip row below (All / Google Doc / PDF / DOCX / ICS), file list alphabetically sorted with type labels
4. Type a partial filename — confirm list filters live
5. Click a chip — confirm list narrows to that type
6. Confirm empty-state message distinguishes "No files found" vs "No files match your search"

**No code needed.** Mark DONE when walkthrough passes. Phase 7 status should remain "Pending Manual Verification" until then.

---

### Phase 9 Progress

| Step | Artifact | Status |
|---|---|---|
| 9A | `main.kt` — window title | ✅ DONE |
| 9B | `UniversalHomeLayout.kt` — FAB color + tooltip + contentDescription | ✅ DONE |
| 9C | Manual Drive picker walkthrough | ✅ VERIFIED 2026-06-25 |

---

## ✅ Phase 8 — Bug Fixes: API Key Masking + Midnight Event Overflow ✅ COMPLETE

Discovered via contrarian JVM app audit (2026-06-25). Two bugs with confirmed root causes and exact file locations.

### Phase 8A — API Key Plaintext (`GeminiSetupPanel.kt`) ⏳ PLANNED

**Root cause:** `OutlinedTextField` for the Gemini API key has no `visualTransformation`, rendering the full key in plaintext. Visible in screenshots, screen shares, and pair-programming sessions — a real exposure risk for students.

**Files changed:**

| File | Change |
|---|---|
| `GeminiSetupPanel.kt` | Add `keyVisible` state; add `PasswordVisualTransformation`; replace `trailingIcon` with eye-toggle + clear in a `Row` |

**Implementation:**
1. Add `var keyVisible by remember { mutableStateOf(false) }` above the field
2. Add `visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation()` to the `OutlinedTextField`
3. Replace `trailingIcon` with a `Row` containing: eye/eye-off visibility toggle + existing clear button

**Tests required:**
- Field renders masked (dots) when key is pre-populated
- Clicking eye reveals plaintext; clicking again re-masks
- Clear button still clears the key when visible or masked

**CRAP acceptance:** No complexity change — `PasswordVisualTransformation` is a leaf call. Implement first; zero risk.

---

### Phase 8B — `23:59 to 23:59:59` Midnight Event Overflow ⏳ PLANNED

**Root cause:** Two places share the same flawed overflow fallback. When a `TimeEvent`'s `startTime ≥ 23:00` and end time is missing or invalid (AI omitted it, defaulted to 10:00), adding 60 minutes would cross midnight, so both sites fall back to `LocalTime(23, 59, 59)` to pass validation. This creates a 1-second event — useless on the calendar and confusing for students.

The comment in the parser even says *"so validate() passes"* — the sentinel value was a workaround, not a correct value.

**Files changed:**

| File | Lines | Change |
|---|---|---|
| `GeminiResponseParser.kt` | 91 | Replace `LocalTime(23, 59, 59)` fallback: return `DayEvent(...)` when overflow detected |
| `EventTimeRepairer.kt` | 12 | Replace `LocalTime(23, 59, 59)` fallback: return `DayEvent(...)` when overflow detected |
| `EventTimeRepairerTest.kt` | new | Unit tests for all repair paths |
| `GeminiResponseParserTest.kt` | additions | Overflow case: parser returns DayEvent, not 1-second TimeEvent |

**Implementation — `EventTimeRepairer.kt`:**
```kotlin
object EventTimeRepairer {
    fun repair(event: Event): Event {
        if (event !is TimeEvent || event.endTime > event.startTime) return event
        val plusHourMins = event.startTime.hour * 60 + event.startTime.minute + 60
        return if (plusHourMins < 24 * 60)
            event.copy(endTime = LocalTime(plusHourMins / 60, plusHourMins % 60))
        else
            DayEvent(title = event.title, source = event.source, date = event.date,
                     category = event.category, warning = event.warning)
    }
}
```

Apply the same pattern in `GeminiResponseParser.kt:91` — instead of `TimeEvent` with `LocalTime(23, 59, 59)`, return a `DayEvent`.

**Tests required (`EventTimeRepairerTest`):**
- `TimeEvent(startTime=23:59, endTime=23:00)` → `DayEvent` (overflow)
- `TimeEvent(startTime=23:30, endTime=22:00)` → `DayEvent` (overflow)
- `TimeEvent(startTime=22:30, endTime=21:00)` → `TimeEvent(endTime=23:30)` (normal 1-hr add, no overflow)
- `TimeEvent(startTime=10:00, endTime=11:00)` → unchanged (already valid)
- `DayEvent` input → unchanged (not a TimeEvent)

**Tests required (`GeminiResponseParserTest` additions):**
- Raw event `type=TIME`, `startTime=23:59`, `endTime=` omitted (defaults to 10:00) → parser returns `DayEvent`

**CRAP acceptance:** `EventTimeRepairer` complexity stays ≤ 3. `GeminiResponseParser` complexity unchanged (replacing one branch, not adding one).

**Implement after 8A** — touches the parsing pipeline so deserves its own isolated commit.

---

### Phase 8 Progress

| Step | Artifact | Status |
|---|---|---|
| 8A | `GeminiSetupPanel.kt` — API key masking + reveal toggle | ✅ DONE |
| 8B-1 | `EventTimeRepairer.kt` — DayEvent conversion on overflow | ✅ DONE |
| 8B-2 | `GeminiResponseParser.kt` — DayEvent conversion on overflow | ✅ DONE |
| 8B-3 | `EventTimeRepairerTest.kt` updated + `GeminiResponseParserTest.kt` additions | ✅ DONE |

---

## ✅ Phase 7 — Drive Picker UX: Search-First File Browser ✅ COMPLETE

### Progress

| Step | Artifact | Status |
|---|---|---|
| 0 | `DriveFileFilter.kt` + `DriveFileFilterTest.kt` (16 tests, all pass) | ✅ DONE |
| 1 | Search bar in `DrivePickerDialog.kt` | ✅ DONE |
| 2 | Type-filter chips in `DrivePickerDialog.kt` | ✅ DONE |
| 3 | Typed list rows (icon + name + type label) | ✅ DONE |
| 4 | Alphabetical sort wired end-to-end | ✅ DONE |

**Motivation:** The current `DrivePickerDialog` is a flat, unsorted plain-text list with no visual differentiation between file types and no way to find a specific file quickly. For students with AuADHD, search beats folder navigation: browsing a folder tree requires remembering *where* a file lives (executive function load), while name/type filtering meets users where they are — even a vague memory ("I know it was a PDF syllabus") is enough.

**Scope:** Pure UI change to `DrivePickerDialog.kt` (commonMain) plus one extracted logic class. No new API calls, no service changes. The `DriveFile` model already carries `mimeType` — that is the only data needed.

**Why search over folder navigation:**
- Folders require organizational memory — high executive function burden, especially on an unstructured Drive
- Client-side filtering gives instant feedback with zero network latency
- Type chips narrow results even without remembering the exact file name

### Deliverables

| # | Artifact | Description |
|---|---|---|
| 1 | Search bar | `OutlinedTextField` at top of dialog; filters `file.name` case-insensitively as user types; client-side, no API calls |
| 2 | Type-filter chips | Chip row below search: `All` / `Google Doc` / `PDF` / `DOCX` / `ICS`; tapping narrows the list; `All` resets |
| 3 | Typed list rows | Each row: leading file-type icon + file name (primary) + type label (secondary, e.g. "Google Doc", "PDF"); replaces bare `Text` |
| 4 | Alphabetical sort | List sorted by `file.name` ascending after every filter update |

### Architecture & CRAP Constraints

Per `AGENTS.md`, all extracted logic must stay ≤ 5 cyclomatic complexity per method. Filter logic must be extracted out of the Composable into a plain, testable class:

```kotlin
// DriveFileFilter.kt — pure logic, zero Compose dependencies
class DriveFileFilter {
    fun filter(files: List<DriveFile>, query: String, type: DriveFileType?): List<DriveFile>
    fun sort(files: List<DriveFile>): List<DriveFile>
}

enum class DriveFileType(val label: String) {
    GOOGLE_DOC("Google Doc"),
    PDF("PDF"),
    DOCX("DOCX"),
    ICS("ICS")
}
```

`DrivePickerDialog` becomes a thin Composable delegating all data manipulation to `DriveFileFilter`.

### Files Changed

| File | Change |
|---|---|
| `DrivePickerDialog.kt` | Replace flat `LazyColumn` with search bar + chip row + typed rows; delegate filter/sort to `DriveFileFilter` |
| `DriveFileFilter.kt` | **New** — pure filter/sort logic; no Compose imports |
| `DriveFileFilterTest.kt` | **New** — unit tests for all filter/sort combinations |

### Tests Required (`DriveFileFilterTest`)

- Empty query + no type → all files returned, sorted alphabetically
- Name query matches substring case-insensitively → only matching files
- Name query matches nothing → empty list
- Type filter `PDF` → only `application/pdf` files
- Type filter `GOOGLE_DOC` → only `application/vnd.google-apps.document` files
- Type filter `null` (`All`) → all MIME types
- Combined name query + type filter → intersection of both
- ICS chip: matches files whose name ends in `.ics` (Drive does not guarantee `text/calendar` MIME for these)
- Sort is stable: equal names preserve input order; output always alphabetical

### Implementation Notes

- ICS files from Drive carry inconsistent MIME types; use `file.name.endsWith(".ics", ignoreCase = true)` for the ICS chip filter rather than MIME.
- The existing `AlertDialog` `text` slot + `LazyColumn` layout works fine — keep it.
- Search `OutlinedTextField` should be `singleLine = true`, `modifier = Modifier.fillMaxWidth()`.

### CRAP Acceptance Criteria

- `DriveFileFilter`: CRAP < 10, coverage ≥ 90% (pure logic — no UI exemption)
- `DrivePickerDialog`: CRAP < 15 (UI file — coverage exemption applies)
- No existing file's CRAP score increases

### Dependencies

None. Pure UI change; no Phase 6 or earlier work required.

---

### Phase 0.23 — Refactor Event & Study Plan prompts to ADR-002 ✅ **COMPLETED**

**Motivation:** AI-generated event extraction and study planning were subject to run-to-run confabulations (hallucinated events or shifted dates). Restructuring the prompts to the 4-part Memorandum Brief standard and fencing all inputs/contexts in explicit XML tag boundaries fixes prompt leakage and anchors the model strictly to the target syllabus text.

**Deliverables:**
1. ✅ `EventBuilder.kt` prompts restructured to Memorandum Brief standard (Clarification, Material, Task, Constraints) with XML boundaries.
2. ✅ `StudyPlanBuilder.kt` prompts restructured to Memorandum Brief standard with XML boundaries.
3. ✅ Tests: Updated assertions in `EventBuilderTest.kt` and `StudyPlanBuilderTest.kt` to verify XML tags and Memorandum headers.

**CRAP Acceptance:**
- `EventBuilder`: Complexity < 15, coverage > 90%
- `StudyPlanBuilder`: Complexity < 15, coverage > 90%

**Verification:**
- Verified by passing `EventBuilderTest` and `StudyPlanBuilderTest` JVM suites.
- Verified by running the app and executing syllabus ingestion and study plan generation end-to-end.

---

### Phase 0.24 — Refactor ChatBuilder prompts to ADR-002 ✅ **COMPLETED**

**Motivation:** Chat prompt builders (`ChatBuilder.kt`) use standard unstructured markdown sections which can lead the LLM to blend raw syllabus metadata rules with instructions or hallucinate claims. Standardizing the chat and chat critique prompts to the ADR-002 standard (Memorandum Brief and XML tag boundaries) preserves answer accuracy and grounding.

**Deliverables:**
1. ✅ `ChatBuilder.kt` — Restructure `getMultiSourceChatPrompt` and `getChatCritiquePrompt` to Memorandum Brief standard and fence input structures in XML tags (`<course_materials>`, `<conversation_history>`, `<chat_response_to_audit>`, etc.).
2. ✅ Tests: Update assertions in `ChatBuilderTest.kt` to check for these XML tag boundaries and Memorandum sections.

**CRAP Acceptance:**
- `ChatBuilder`: Complexity < 10, method complexity < 5.

**Verification:**
- Verified automatically by passing `ChatBuilderTest.kt`.
- Verified manually by a walkthrough in the app's Chat Panel, verifying that document-grounded chat and critique loop remain 100% functional.

### Phase 0.25 — Add Unit Tests for HttpOtelTracer ✅ **COMPLETED** (verified 2026-07-04)

**Motivation:** `HttpOtelTracer` provides KMP-native OTLP/HTTP telemetry but is currently untested. Supporting dependency injection of `HttpClient` will enable unit testing of span exports, headers, parent-child trace mapping, and error resilience.

**Deliverables:**
1. ✅ `HttpOtelTracer.kt` — `client: HttpClient` constructor param with default instantiation already present.
2. ✅ `HttpOtelTracerTest.kt` — full suite already exists and passes (`./gradlew :composeApp:jvmTest --tests "...HttpOtelTracerTest"`):
   - Export serialization structure (spans, resource attributes, events, errors).
   - Trace context propagation (parent-child nested trace/span relationship).
   - Silent error handling (swallowing client post exception).

**CRAP Acceptance:**
- `HttpOtelTracer`: Complexity < 15, coverage > 85%

**Verification:**
- Confirmed by re-running `HttpOtelTracerTest.kt` — passes clean. This entry was stale; deprioritized to the bottom of the queue in favor of Phase 6b below, which has real open gaps.

---

## ✅ Completed (as of June 2026)

All items below have been verified against the actual codebase — not just the roadmap.

| Feature | Notes |
|---|---|
| UI Scaffolding | All three panels complete |
| General Styling | Consistent theme, typography, borders |
| File Picker | Desktop, Android, iOS (`UIDocumentPickerViewController`) |
| Settings Screen | API key, Google auth, drive settings |
| Unified Event Model | `TimeEvent`, `DayEvent`, `SyncStatus`, `AcademicCategory` |
| Routine Management | Full create/view/persist recurring schedule |
| Calendar View | Events from all sources, grouped by date |
| Testing Framework | Kotest, MockK, ~33 test files (unit + integration) |
| iCalendar Parsing | `.ics` → `SourceFragments` via `IcsCalendarSource` |
| Google Calendar REST | Fully KMP-compatible sync via Ktor |
| OAuth2 Auth | JVM local-server flow + persistent token storage |
| AI Integration | Gemini REST, model auto-negotiation, SQLite model cache |
| Agentic Architecture | `IngestionAgent`, `EventAgent`, `CalendarAgent`, `NormalizationService`, `ContextAgent` |
| Multi-Format Extraction | PDF/DOCX native on Android, iOS, JVM |
| Native Mobile Auth | `GoogleSignInClient` (Android), `ASWebAuthenticationSession` (iOS) |
| AI Study Plan Constraints | 9–21 hr limits, lunch/dinner breaks, collision resolution |
| Debug Logging | Platform-aware logger |
| Automatic Schema Migrations | `DriverFactory` detects and creates missing tables |
| Recursive Task Decomposition | `DecompositionOrchestrator` (depth-3 FIFO), `WorkUnit` sealed interface, full Kotest specs |
| **Automatic Source Categorization** | `IngestionAgent` calls `GeminiAIService.categorizeSource()` on all non-ICS content; category displayed in `SourceItemView` |
| **"Break It Down" UI** | `TaskDecompositionDialog` in `AcademicCalendar.kt` — wired end-to-end for DEADLINE/FINALS events |
| **Two-Way Sync — Remote Deletions** | `synchronize()` Step 4: hard-deletes local SYNCED events absent from remote fetch |
| **Two-Way Sync — Offline Mutation Queue** | `DELETED_LOCALLY` + `LOCAL_ONLY` states; flushed on next `synchronize()` call |
| **Contrarian (Critic-Actor) Loop** | Critique pass for event extraction, study plans, chat responses, and task decomposition |
| **Multi-Source Chat Context** | ContextAgent queryAllSources() aggregates fragments, conversation history threaded through prompt |
| **.ics Export** | Refactored `ICalGenerator` + expect/actual `writeIcsFile` with iOS Share Sheet & Android MediaStore actuals |
| **Sync Hardening** | Token refresh retry internally in GoogleCalendarSyncService, pageToken pagination, conflict resolution warnings |
| **Visual Progress Tracking** | `timeUntilDue` and `studyProgress` helpers on `Event`, linear progress and countdown chips in calendar list, and Semester Health summary card in StudioPanel |
| **Scheduling Fine-Tuning** | User-configurable study hours, break lengths, and limits in Settings; injected into AI study plan generation and collision resolver |
| **Weighted Deliverables** | Syllabus grade weights extracted by AI, stored in Event models, and used to allocate study block durations proportionally |
| **Lenient Title Matching** | Refactored `SyllabusEvaluationIntegrationTest` matching logic with normalized containment check to eliminate false negatives |
| **Observability & Telemetry** | Multiplatform `TelemetryManager` logging rate limits, JSON parsing exceptions, and Critic-Actor decorator outcomes |
| **Client Secrets Management** | Automated build-time Google API client secrets injection via custom Gradle task |
| **Compose UI Tests** | Added Composable flow verification tests for key screens and dialogs |
| **Source Fragment Indexing** | Relevance ranking (TF-IDF) in `ContextAgent` before prompt injection |
| **Stateful User Preference Memory** | Track manual study block moves/deletions, derive implicit constraints, and inject as prompt rules and resolver filters |
| **Sync Re-negotiation UI** | Replace silent collision resolution on remote sync with interactive user proposal diff dialog |
| **Headless Multi-Source Ingestion Integration Test** | Ingests a dynamically compiled Spring 2025 calendar PDF alongside BDAN 250 and HIST 152 syllabi, resolving collision-free and matching ground-truth events |
| **SettingsScreen.kt Refactoring** | Extracted `SettingsPreferencesParser` from the 9-complexity `parseAndSave()` method and added unit tests |
| **AgentHarness.kt Refactoring** | Extracted modular sub-functions from the 23-complexity `runHarness()` method and added tests for watched directories |

---

## Phase 1 — High Impact, Near-Term (Do First)

These items have clear scope, high user value, and sufficient existing infrastructure to build on.

### ~~1.1 — Multi-Source Chat Context~~ ✅ **COMPLETED**

**Why first:** Chat is the app's primary interaction layer. Currently every chat message is a fresh,
single-source prompt — defeating the purpose of ingesting multiple syllabi and documents.

**Current state:**
- `ContextAgent.querySource(source, query)` — single-source only
- `ChatPanel` passes only `selectedSource` to it
- No conversation history is threaded through; each turn is a fresh prompt

**Work items:**
1. Add `ContextAgent.queryAllSources(sources, conversationHistory, query)` — aggregates fragments from all
   stored `SourceItem`s + their SQLite `SourceFragment` rows into a single ranked context window
2. Thread `conversationHistory: List<ChatMessage>` into `GeminiAIService.generateChatResponse()`
   so the model has prior turns for follow-up questions
3. Update `ChatPanel` to use `queryAllSources` (with a "Scope: All Sources / This Source" toggle)
4. Update `AppController` to persist `chatMessages` across sessions (currently in-memory only)
5. Add Kotest unit tests for aggregation logic + MockK test for context builder

**Dependencies:** None — all infrastructure exists.

---

### ~~1.2 — .ics Export~~ ✅ **COMPLETED**

**Why second:** `ICalGenerator` on JVM is fully implemented but dead code — never called from any UI
or agent. Wiring it to a UI button is low-effort and unlocks a highly requested student workflow
(sharing a study schedule with external calendars).

**Current state:**
- `ICalGenerator.kt` (jvmMain): `buildAcademicCalendar()` + `calendarToString()` exist but build
  a hardcoded stub, not the real event list
- No UI entry point, no file write/share logic

**Work items:**
1. Refactor `ICalGenerator.buildAcademicCalendar(events: List<Event>)` to accept the real event list
   instead of the hardcoded sample
2. Add `expect fun writeIcsFile(content: String): String` / `actual` for JVM (write to `~/Downloads`),
   Android (MediaStore), iOS (share sheet)
3. Add "Export to .ics" button in `StudioPanel` (or calendar overflow menu)
4. Wire button → `ICalGenerator` → `writeIcsFile` → toast/snackbar with file path
5. Add Kotest unit test verifying round-trip: events → ICS string → parse back → same events

**Dependencies:** 1.1 not required. Can be done in parallel.

---

### ~~1.3 — Sync Hardening~~ ✅ **COMPLETED**

**Why now:** The core sync algorithm is solid and tested. These three gaps are the remaining failure
modes that will surface in production use.

**Work items (ordered by risk):**

#### 1.3a — Token Refresh in Sync Loop
- **Gap:** If the Google access token expires mid-`synchronize()`, it throws and leaves data in an
  inconsistent state (some steps done, others not)
- **Fix:** Wrap each HTTP call in `GoogleCalendarSyncService` with a token-refresh retry: if a `401`
  is returned, call `GoogleAuthService.refreshToken()` and retry once, then throw

#### 1.3b — Pagination for `getEvents()`
- **Gap:** `getEvents()` fetches all events in a single request. Large calendars will hit the
  Google Calendar API's default `maxResults=250` silently truncating results
- **Fix:** Implement `pageToken`-based pagination loop in `GoogleRemoteCalendarRepository.getEvents()`
  until `nextPageToken` is null

#### 1.3c — Update Conflict Resolution Strategy
- **Gap:** If the same event is edited both locally and remotely between syncs, the remote always
  wins silently. This is acceptable for now but should be documented and surfaced to the user
- **Fix:** On conflict (local-modified event exists in remote with a newer `updated` timestamp),
  log a conflict warning and display a diff/merge prompt in the Calendar UI (or document the
  remote-wins policy explicitly in code comments)

**Dependencies:** None — these are hardening changes to existing code.

---

## Phase 2 — Medium Impact (Do Second)

These items add significant UX polish and address real student needs, but depend on Phase 1 being
stable first.

### ~~2.1 — Visual Progress Tracking~~ ✅ **COMPLETED**

**Why:** A visual "Time Remaining" indicator directly supports executive function challenges — the
primary mission of the app.

**Current state:** Only `CircularProgressIndicator` loading spinners exist. No deadline countdowns,
no linear progress bars, no completion percentages.

**Work items:**
1. Add a `timeUntilDue(event: Event): Duration` utility in `Event.kt`
2. In `EventItemView`, display a `LinearProgressIndicator` for DEADLINE/FINALS events showing
   completion of the window between `studyPlanStart` and `dueDate`
3. Add a "Due in X days" chip on deadline events in the calendar list
4. In `StudioPanel`, add a "Semester Health" summary card: events due in next 7 days, next 30 days
5. Add unit tests for `timeUntilDue` edge cases (past due, due today, future)

**Dependencies:** Phase 1.1 (multi-source context) recommended first so progress tracking has
access to the full event set from all sources.

---

### ~~2.2 — Scheduling Fine-Tuning (User-Configurable Study Parameters)~~ ✅ **COMPLETED**

**Why:** The AI constraint system works well with fixed defaults (9–21 hr window, 12–13 lunch,
17–19 dinner). Making these configurable per student multiplies the app's effectiveness for
different learning styles and schedules.

**Current state:** `CollisionResolver` uses hardcoded hour constants. `AiPrompts.kt` has hardcoded
study window strings injected into the Gemini prompt.

**Work items:**
1. Add `StudyPreferences` data class: `studyStartHour`, `studyEndHour`, `lunchStart`, `lunchEnd`,
   `dinnerStart`, `dinnerEnd`, `maxStudyBlockHours`, `preferredBreakMinutes`
2. Persist `StudyPreferences` via `RoutineRepository` (or a new `PreferencesRepository`)
3. Inject `StudyPreferences` into `CollisionResolver` constructor and `AiPrompts.getStudyPlanPrompt()`
4. Add UI controls in `SettingsScreen` for all parameters
5. Add Kotest parameterized tests for `CollisionResolver` with custom preferences

**Dependencies:** Phase 1 complete.

---

### ~~2.3 — Syllabus-to-Study Schedule Fine-Tuning (Weighted Deliverables)~~ ✅ **COMPLETED**

**Why:** Currently all deliverables get the same study block allocation. A 40%-weight final exam
should command more preparation time than a 5%-weight quiz.

**Current state:** `EventAgent.generateStudyPlan()` sends deliverables to Gemini without weight
metadata. `AcademicCategory` has `priority: Int` but it's not derived from grade weight.

**Work items:**
1. Extend `GeminiAIService.generateCalendarEvents()` to extract `gradeWeight: Float?` from
   syllabus text alongside the event title/date
2. Store `gradeWeight` in the `Event` model
3. Update `AiPrompts.getStudyPlanPrompt()` to include grade weights per deliverable so Gemini
   can allocate proportional study time
4. Add `ConfabulationGuardTest`-style tests to verify weight extraction accuracy

**Dependencies:** 2.2 recommended first (preferences infrastructure).

---

## Phase 3 — Infrastructure & Polish (COMPLETED)

Low user-facing impact but important for production readiness and maintainability.

### 3.1 — Client Secrets Management (COMPLETED)
Secure build-time injection mechanism for Google client ID and secret using a custom Gradle task to prevent secrets from being committed or manually configured.

### 3.2 — Compose UI Tests for Key Flows (COMPLETED)
Added Composable flow tests for `TaskDecompositionDialog`, `ChatPanel`, and `SettingsScreen` verifying state progressions, API key handling, and message submissions.

### 3.3 — Performance: Source Fragment Indexing (COMPLETED)
Implemented relevance ranking (TF-IDF) in `ContextAgent.rankFragments` to select and inject only the top-K most relevant source fragments for prompt optimization.

---

## Phase 4 — Model Evaluations & Failure Monitoring (COMPLETED)

### 4.1 — Diversify Test Syllabi (COMPLETED)
- Ingest and commit 5–10 real college syllabi from different fields (STEM, humanities, design), lengths, and calendar structures (semesters vs. quarters).
- Verify native format readers (PDF, DOCX, text) parse their content accurately.

### 4.2 — Offline Evaluation Framework (COMPLETED)
- Create a test runner script/class (`SyllabusEvaluationIntegrationTest`) that parses the test syllabi.
- Save companion "ground truth" JSON files containing manually verified academic events (dates, weights, categories) for each syllabus.
- Compute performance metrics: **Precision** (avoiding spurious events), **Recall** (finding all deadlines), and **Date Accuracy** (verifying correct date computation).

### 4.3 — Production Telemetry & Observability (COMPLETED)
- Instrument `AIService` with error monitoring (e.g., Sentry) to report rate limits (429), API timeouts, and JSON parsing exceptions in the wild.
- Track metrics for the **Critic-Actor Loop**: record how often the Critic rejects Actor output to assess prompts and model stability over time.
- Implement user feedback signals (thumbs-up/down) for AI-generated events to capture silent failures.

---

## Phase 5 — Iterative Refinement & Agent Harness (COMPLETED)

Refining the Critic-Actor loop into a graph-based state tracker and implementing a lifecycle-driven background polling harness.

### 5.1 — Graph-Based Cycle Detection (COMPLETED)
Implement visited-state graph tracking in `CriticActorAIService` to handle natural convergence and multi-step oscillation cycles. Guided by the CRAP Risk Reduction Plan below.

### 5.2 — Active Lifecycle Agent Harness (COMPLETED)
Implement `AgentHarness` to orchestrate startup and once-daily polling of local watched directories and Google Drive, sequentially processing new files and synchronizing calendar mutations. Guided by the CRAP Risk Reduction Plan below.

### 5.3 — Startup Check-In Interview Loop (COMPLETED)
Query incomplete past-due study blocks and tasks at startup/daily check-in, presenting an interactive interview dialog for completion confirmation or automated rescheduling. Guided by the CRAP Risk Reduction Plan below.

### 5.4 — Stateful User Preference Memory (COMPLETED)
Store user calendar edits and deletions to derive implicit scheduling constraints, injecting them into future AI prompt generations. Guided by the CRAP Risk Reduction Plan below.

### 5.5 — Sync Re-negotiation UI (COMPLETED)
Replace silent conflict resolution during two-way sync with interactive user proposal diffs. Guided by the CRAP Risk Reduction Plan below.

---

## 📊 Updated CRAP Reduction Priority (June 2026)

Based on current CRAP.md metrics (generated 2026-06-09), the following files have been re-prioritized as the primary focus due to their high bug risk. These 8 files account for the majority of complexity-driven bugs.

### Current High-Risk Files Requiring Immediate Refactoring

| Priority | Phase | File | Current CRAP | Target | Complexity | Coverage | Strategy |
|---|---|---|---|---|---|---|---|
| **🔴 1** | **0.1** | GeminiAIService.kt | **67.50** | < 40 | 56 | 84.6% | Decompose: RetryService, ModelNegotiator, ResponseParser, PromptBuilder |
| **🔴 2** | **0.2** | SettingsScreen.kt | **57.03** | < 25 | 35 | 73.8% | Decompose: PreferencesParser, ApiKeyValidator, AuthSettingsFlow, DriveSettingsPanel |
| **🟠 3** | **0.6** | AiPrompts.kt | **41.30** | < 25 | 41 | 94.4% | Decompose: StudyPlanBuilder, CategorizationBuilder, EventBuilder, ChatBuilder |
| **🟠 4** | **0.7** | CollisionResolver.kt | **41.09** | < 20 | 41 | 96.3% | Decompose: SchedulingAlgorithm, ConstraintValidator, CollisionDetector |
| **🟠 5** | **0.8** | AgentHarness.kt | **38.41** | < 20 | 37 | 89.9% | Decompose: DirectoryPoller, IngestionOrchestrator, SyncCoordinator |
| **🟠 6** | **0.4** | AcademicCalendar.kt | **34.21** | < 20 | 29 | 81.6% | Decompose: EventFilterService, LayoutPanels, MutationHandler; Add UI tests |
| **🟠 7** | **0.5** | ContextAgent.kt | **31.03** | < 20 | 31 | 96.8% | Decompose: FragmentRanker, ContextAggregator, PromptBuilder |
| **🟡 8** | **0.3** | AppController.kt | **30.51** | < 15 | 20 | 70.3% | Decompose: SyncOrchestrator, AgentPollingService; Add integration tests |

### Refactoring Execution Order

**Week 1: High-Impact AI Services (Phases 0.1 → 0.2)**
- GeminiAIService: Unlock cleaner error handling, clearer prompts
- SettingsScreen: Unlock preference management reuse

**Week 2: Prompt & Algorithm Experts (Phases 0.6 → 0.7)**
- AiPrompts: Unlock maintainable prompt engineering
- CollisionResolver: Unlock testable scheduling logic

**Week 3: Infrastructure (Phases 0.8 → 0.3)**
- AgentHarness: Unlock reliable background operations
- AppController: Unlock clearer orchestration
- AcademicCalendar: Unlock composable UI patterns
- ContextAgent: Already 96.8% coverage; quick decomposition

### Success Metrics

After Phase 0.8 completion:
- ✅ All 8 files CRAP < 30
- ✅ Each extracted module has CRAP < 15
- ✅ Coverage increases in low-coverage files (AppController → 85%, SettingsScreen → 85%)
- ✅ Zero new high-risk files introduced

---

## Historical CRAP Risk Reduction Plan (Phases 1–9, COMPLETED)

### Strategy
Two complementary levers reduce CRAP:
1. **Coverage** — adding tests brings `(1 - coverage)^3` toward 0. Most effective when coverage is low.
2. **Refactoring** — splitting large methods reduces `complexity^2`. Most effective when a single method dominates complexity.

Pure Compose UI files (`App.kt`, `AddRoutineItemDialog.kt`, `AcademicCalendar.kt`, `RoutineScreen.kt`) score high only because they have 0% coverage — not because of dangerous logic. They are deferred to Compose UI testing pass (Phase 5 of the CRAP plan) and skipped here.

### Progress Tracker

| Phase | File | Baseline CRAP | Target | Status |
|---|---|---|---|---|
| 1 | GoogleRemoteCalendarRepository.kt | 220.94 | < 50 | ✅ Done |
| 2 | ModelManager.kt | 41.20 | < 15 | ✅ Done |
| 3 | EventPresenter.kt | 40.37 | < 20 | ✅ Done |
| 4 | GoogleDriveService.kt | 39.58 | < 20 | ✅ Done |
| 5 | GeminiAIService.kt | 153.53 | < 80 | ✅ Done |
| 6 | CriticActorAIService.kt | 110.11 | < 60 | ✅ Done |
| 7 | CalendarAgent.kt + EventAgent.kt | 95.59 / 83.57 | < 60 / < 50 | ✅ Done |
| 8 | AiPrompts.kt | 42.06 | < 25 | ✅ Done |
| 9 | Compose UI files | 210 / 182 / 35 / 20 | — | ⏳ Future |

### Refactoring & Coverage Details

#### Phase 1 — `GoogleRemoteCalendarRepository.kt` (CRAP 220.94)
- **Actions**: Created `GoogleRemoteCalendarRepositoryTest.kt` in `jvmTest`. Mocked `GoogleCalendarSyncService` with `MockK`. Added tests for all currently-uncovered paths: `getCEFCalendarId` (create path), `deleteEvent` (swallow), `clearCalendar`, `getEventsInRange`, overlap detection, and isolated updates.

#### Phase 2 — `ModelManager.kt` (CRAP 41.20)
- **Actions**: Fixed progress emission bug in `downloadModel` (emitted intermediate progress inside chunk loop). Added tests for successful and 404 download paths, verifying that `DownloadProgress(1f, true)` is emitted last.

#### Phase 3 — `EventPresenter.kt` (CRAP 40.37)
- **Actions**: Added exhaustive `getEventBorderColor` and `getCategoryLabel` tests for missing combinations (`FINALS`, `SEMESTER_BOUND`, etc.). Extracted duplicated `when(source)` block into a shared private helper.

#### Phase 4 — `GoogleDriveService.kt` (CRAP 39.58)
- **Actions**: Fixed `validateConnection` to use `withToken`. Fixed 401 detection to use `ResponseException.response.status`. Created `GoogleDriveServiceTest.kt` with MockEngine verifying validation, file listings, and retry behavior.

#### Phase 5 — `GeminiAIService.kt` (CRAP 153.53)
- **Actions**: Extracted focused private helpers from the 148-line `executeWithRetry` God method (`handleRpdError`, `handleStructuralError`, `handleAuthError`, `applyExponentialBackoff`). Extracted JSON parsers for tasks and categorizations. Fixed time parsing to support both `HH:mm:ss` and `HH:mm`.

#### Phase 6 — `CriticActorAIService.kt` (CRAP 110.11)
- **Actions**: Extracted loop bodies into `parseEventFromJson` and `parseTaskFromJson` methods, significantly reducing cyclomatic complexity.

#### Phase 7 — `CalendarAgent.kt` + `EventAgent.kt` (CRAP 95.59 / 83.57)
- **Actions**: Added synchronization logic tests for Ollama timeouts. Added tests for event completion updates, calendar pushes, rescheduling, and skipping.

#### Phase 8 — `AiPrompts.kt` (CRAP 42.06)
- **Actions**: Added coverage for hour formatting edge cases (midnight=0, noon=12, PM hours).

---

---

## Phase 6 — Token-Efficient Source Processing ✅ **COMPLETED**

> **Status:** ✅ Completed. Content-addressable analysis cache (6.1), Mutex sequential queue (6.2), and API key global hold strategy (6.3) are all implemented, verified, and committed.

### Background & Motivation

Observed failure mode (June 2026): user deleted all STLCC sources and re-added them. Four documents dropped simultaneously triggered ~20 API calls in under a minute. All HEAVY-tier models (gemini-2.5-flash → gemini-2.0-flash → gemini-2.5-flash-lite → gemini-2.0-flash-lite → gemini-2.0-flash-001) hit free-tier RPM limits in rapid sequence. `GeminiRequestExecutor` blacklisted each one and cascaded to the next, exhausting all fallbacks. The 5-minute global rate-limit window activated, leaving the last two sources entirely unprocessed.

Two root causes:
1. **No analysis cache** — deleting and re-adding an unchanged PDF reruns the full AI pipeline from scratch (CriticActor 2-3 passes + SyllabusAuditor + ContextAgent = 4-5 API calls per doc)
2. **Concurrent processing** — `SourceAdder.addSource()` launches one coroutine per file with no serialization; N simultaneous drops = N concurrent AI pipelines sharing one API key

Secondary issue: when RPM limits hit, the executor treats "delay too long → blacklist model → try next" as a per-model problem, but on a free-tier key **all models share the same quota** — cascading blacklisting makes it worse.

**Human caveat (important):** Professors update syllabi; users may receive addenda. The cache must key on document **content**, not filename. A modified file (even one byte different) produces a different SHA-256 → automatic cache miss → AI reruns. A "Re-analyze" escape hatch bypasses the cache when the user wants fresh analysis of technically-unchanged content (e.g., AI model improved, prior analysis missed something).

---

### Phase 6.1 — Content-Addressable Analysis Cache

**Goal:** Delete + re-add of unchanged PDF → zero AI calls. Modified file → automatic cache miss → full AI reruns. No stale analysis possible under normal use.

#### New artifacts

| File | Type | Purpose |
|---|---|---|
| `AppDatabase.sq` | modify | Add `AnalysisCacheEntity` table and 3 queries |
| `DriverFactory.kt` | modify | Add `ALTER TABLE SourceEntity ADD COLUMN contentHash TEXT` migration |
| `ContentHasher.kt` | new, `commonMain` | `fun hash(fragments: List<SourceFragment>): String` — SHA-256 using `okio.ByteString.encodeUtf8().sha256().hex()` (same pattern as `EventGenerationService.kt:110`) |
| `CachedAnalysis.kt` | new, `commonMain` | `@Serializable data class CachedAnalysis(val sourceHash: String, val cachedEventsJson: String, val cachedMetadataJson: String?, val createdAt: Long)` |
| `AnalysisCacheRepository.kt` | new, `commonMain` | Interface: `suspend fun getCached(hash: String): CachedAnalysis?` / `suspend fun putCache(analysis: CachedAnalysis)` / `suspend fun evict(hash: String)` |
| `SqlDelightAnalysisCacheRepository.kt` | new, `commonMain` | SqlDelight impl of `AnalysisCacheRepository` |
| `SourceAdder.kt` | modify | Add `forceRefresh: Boolean = false` param; hash-check before AI; cache write after AI |
| `DependencyContainer.kt` | modify | Wire `ContentHasher` and `AnalysisCacheRepository` into `SourceAdder` |
| Source item UI (SourceItemView or SourcesPanel) | modify | "Re-analyze" action → `sourceManager.reanalyze(source)` which calls `addSource(source, forceRefresh = true)` |

#### DB additions — add to `AppDatabase.sq`

```sql
CREATE TABLE IF NOT EXISTS AnalysisCacheEntity (
    sourceHash TEXT PRIMARY KEY NOT NULL,
    cachedEventsJson TEXT NOT NULL,
    cachedMetadataJson TEXT,
    createdAt INTEGER NOT NULL
);

getCachedAnalysis:
SELECT * FROM AnalysisCacheEntity WHERE sourceHash = ?;

insertCachedAnalysis:
INSERT OR REPLACE INTO AnalysisCacheEntity(sourceHash, cachedEventsJson, cachedMetadataJson, createdAt)
VALUES (?, ?, ?, ?);

deleteCachedAnalysis:
DELETE FROM AnalysisCacheEntity WHERE sourceHash = ?;
```

Also add to `DriverFactory.kt` in the migration block (after the existing `ALTER TABLE` statements):
```kotlin
driver.execute(null, "ALTER TABLE SourceEntity ADD COLUMN contentHash TEXT", 0)
```

#### Event JSON serialization for the cache

`TimeEvent` and `DayEvent` are both `@Serializable`. To serialize `List<Event>` (sealed interface) to JSON, add a `@SerialName` discriminator to each concrete type and register the polymorphic module:

```kotlin
// In Event.kt — add to each concrete class
@Serializable
@SerialName("TimeEvent")
data class TimeEvent(...) : Event

@Serializable
@SerialName("DayEvent")
data class DayEvent(...) : Event
```

Register the module wherever `Json {}` is constructed for cache serialization:
```kotlin
val cacheJson = Json {
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
        polymorphic(Event::class) {
            subclass(TimeEvent::class)
            subclass(DayEvent::class)
        }
    }
}
```

Use `cacheJson.encodeToString(ListSerializer(PolymorphicSerializer(Event::class)), events)` to write and `cacheJson.decodeFromString(...)` to read. Verify the existing `EventGenerationService.kt` serialization pattern first — do not duplicate the Json configuration; extract it to a shared `CefJson` object if needed.

#### Implementation steps (in strict order)

1. Add `AnalysisCacheEntity` table and queries to `AppDatabase.sq`
2. Add `contentHash TEXT` migration to `DriverFactory.kt`
3. Implement `ContentHasher.kt` (compute `okio.ByteString.encodeUtf8(fragments.joinToString("\n\n") { it.text }).sha256().hex()`)
4. Add `@SerialName` to `TimeEvent` and `DayEvent`; create or extend `CefJson` configuration with polymorphic module
5. Implement `CachedAnalysis` data class
6. Implement `AnalysisCacheRepository` interface
7. Implement `SqlDelightAnalysisCacheRepository`
8. Modify `SourceAdder.addSource(source: SourceItem, forceRefresh: Boolean = false)`:
   - Compute `val hash = ContentHasher.hash(source.fragments)`
   - If `!forceRefresh`: `val cached = cacheRepository.getCached(hash)` → if non-null, deserialize events from `cached.cachedEventsJson`, call `onEventsAdded(events)`, skip all AI, return
   - If miss (or `forceRefresh`): run existing AI pipeline; on success, serialize events and metadata to JSON, call `cacheRepository.putCache(CachedAnalysis(hash, eventsJson, metadataJson, Clock.System.now().toEpochMilliseconds()))`
9. Wire `ContentHasher` and `SqlDelightAnalysisCacheRepository` in `DependencyContainer`; update `SourceAdder` construction there
10. Add "Re-analyze" action to source item UI

#### Tests required

- `ContentHasherTest`: same input → same hash; different input → different hash; hash is stable across JVM restarts (test twice in same process); empty fragments → non-empty stable hash
- `SqlDelightAnalysisCacheRepositoryTest`: put → get round-trip; evict removes entry; miss returns null; two different hashes don't collide
- `SourceAdderTest` additions:
  - Cache hit: `aiService.generateCalendarEvents` NOT called; `onEventsAdded` receives deserialized cached events
  - Cache miss: AI called; `cacheRepository.putCache` called with correct hash
  - `forceRefresh = true`: AI called even when cache has entry; cache overwritten with new result
  - Different hash (modified content): AI reruns, cache updated with new hash

**Acceptance criteria:** Delete + re-add of identical PDF file → exactly 0 AI calls. Re-add of modified file (any change) → full pipeline. Force-refresh on identical file → full pipeline, cache updated.

---

### Phase 6.2 — Sequential Source Processing Queue

**Goal:** Multiple files dropped simultaneously process one at a time, eliminating concurrent AI call cascades.

**Current behavior:** `SourceAdder.addSource()` launches a new coroutine per call. Four files dropped → four coroutines enter the AI block simultaneously → ~20 API calls in under a minute on a shared key.

**Fix:** A `Mutex` inside `SourceAdder` serializes AI access. Callers are non-blocking (the coroutine suspends inside `launch`, not at the call site), so the UI remains responsive. With 6.1 in place, cache hits acquire and release the mutex immediately — the queue drains fast for already-analyzed docs.

#### Files changed

| File | Change |
|---|---|
| `SourceAdder.kt` | Add `private val processingMutex = Mutex()` (kotlinx.coroutines); wrap the AI block in `processingMutex.withLock { ... }` — cache check and AI call both inside the lock to prevent two concurrent cache-miss writes for the same hash |
| `SourceAdderTest.kt` | Add test: two concurrent `addSource` calls → AI invoked exactly twice but never overlapping (use `CountDownLatch` or `Channel` to verify sequential ordering) |

**Acceptance criteria:** Dropping N files simultaneously → AI calls execute strictly one at a time. No change in external behavior beyond ordering.

---

### Phase 6.3 — Global Rate-Limit Hold Strategy

**Goal:** When all models report long rate-limit delays (indicating API-key-level saturation, not per-model limits), wait for the delay and retry the preferred model — instead of cascading through all fallbacks and activating the 5-minute global window.

**Root cause in `GeminiRequestExecutor.executeWithRetry()`:** Line ~140:
```kotlin
if (delayMs > 10_000L) {
    modelNegotiator.blacklistModel(modelName)
    continue  // tries next model — which is also rate-limited
}
```
On a free-tier key, **all models share the same per-minute quota**. Two consecutive long-delay events signals key-level saturation. Continuing to the next model wastes an attempt and accelerates total model exhaustion.

#### Changes to `GeminiRetryService`

Add alongside existing `rateLimitResetTime`:
```kotlin
companion object {
    private var rateLimitResetTime: Long = 0L
    private var globalHoldUntil: Long = 0L  // NEW

    fun clearRateLimitResetForTesting() { rateLimitResetTime = 0L }
    fun clearGlobalHoldForTesting() { globalHoldUntil = 0L }  // NEW
}

fun activateGlobalHold(delayMs: Long) {  // NEW
    globalHoldUntil = Clock.System.now().toEpochMilliseconds() + delayMs + 2_000L
}
```

Update `checkRateLimitWindow()` to also throw if `now < globalHoldUntil`:
```kotlin
fun checkRateLimitWindow() {
    val now = Clock.System.now().toEpochMilliseconds()
    if (now < rateLimitResetTime) { ... }  // existing
    if (now < globalHoldUntil) {           // NEW
        val remainingSeconds = ((globalHoldUntil - now) + 999L) / 1000L
        throw Exception("QuotaExhausted: Rate limit reached. Please wait $remainingSeconds seconds before trying again.")
    }
}
```

#### Changes to `GeminiRequestExecutor.executeWithRetry()`

Add `var consecutiveRateLimitCount = 0` local to the retry loop. In the `delayMs > 10_000L` branch:
```kotlin
if (delayMs > 10_000L) {
    consecutiveRateLimitCount++
    if (consecutiveRateLimitCount >= 2) {
        // All models rate-limited — this is key-level saturation, not a per-model problem
        logger?.e(tag, "⚠️ API key saturated (${consecutiveRateLimitCount} consecutive long rate limits). Holding ${delayMs}ms before retry.")
        retryService.activateGlobalHold(delayMs)
        retryService.wait(delayMs)
        consecutiveRateLimitCount = 0
        continue  // retry preferred model after hold, not next fallback
    }
    // Single long delay — still treat as per-model, blacklist and try next
    modelNegotiator.blacklistModel(modelName)
    modelNegotiator.evictFromCache(modelName)
    logger?.e(tag, "⚠️ Model $modelName rate limit delay ${delayMs}ms too long. Blacklisted. Trying next model...")
    ...
    continue
}
```

On any successful response, reset `consecutiveRateLimitCount = 0`.

#### Tests required

- Single long rate-limit event → model blacklisted, `consecutiveRateLimitCount = 1`, no global hold
- Two consecutive long rate-limits → `activateGlobalHold` called, executor waits, no further model cascade
- Success between two long delays → count resets; second long delay treated as a fresh single event
- `checkRateLimitWindow` throws during active global hold; passes after hold expires
- `clearGlobalHoldForTesting()` clears hold for test isolation

**Acceptance criteria:** When free-tier models hit RPM simultaneously, the executor waits for the reported delay and retries without cascading to further models or activating the 5-minute global window.

---

### Implementation Order for Phase 6

Execute strictly in this sequence. Do not start the next step until the previous step's tests pass and `./gradlew :composeApp:jvmTest` is green.

| Step | Phase | Rationale |
|---|---|---|
| 1 | **6.2 — Mutex queue** | One-file change, immediate impact; prevents races in 6.1 cache writes |
| 2 | **6.1 — Analysis cache** | Multi-file; eliminates most redundant AI calls; 6.2 must be in place first |
| 3 | **6.3 — Global hold** | Supplements 6.1+6.2; less critical once cache eliminates most re-analysis |

**CRAP targets:** `SourceAdder` complexity must stay ≤ 5 after modifications. If the hash+cache logic raises it above 5, extract a `CacheAwareSourceAdder` decorator that wraps `SourceAdder` and handles cache checks, leaving `SourceAdder` as a pure AI caller.

---

## Phase 6b — Web Client & Agent-User Interaction (AG-UI) Protocol Integration 🔴 IN PROGRESS — 6.5 next up

Bring CEF to the web using a React frontend that dynamically communicates with the Ktor server via a real-time agentic stream, eliminating duplicated data models.

**Verified 2026-07-04:** 6.1–6.4 are done (server depends on `:composeApp`, `/web` Vite+React app exists with `useAgentStream.ts` + `App.tsx`, 890 lines; 6.2's SSE endpoint now emits real timestamps/runId, escaped JSON, and real Critic-Actor loop events). **6.5 is the remaining gap** — the frontend doesn't yet render the richer event stream distinctly, and responses aren't chunked word-by-word.

### 6.1 — Gradle & Dependency Realignment ✅ DONE
* Update `server/build.gradle.kts` to depend on `:composeApp` JVM compile target.
* Align library catalog dependencies (`kotlinx-datetime`, `multiplatform-settings`, Ktor JSON serialization) for server scope.
* Configure duplicatesStrategy for copy and zip archive tasks.

### 6.2 — Ktor AG-UI SSE Stream Endpoint ✅ **COMPLETED** (2026-07-04)

**Deliverables:**
1. ✅ Real timestamps via `Clock.System.now().toEpochMilliseconds()` on every emitted event (was hardcoded `1717720000000`).
2. ✅ Real per-request `runId` via `randomHexId(8)`, extracted (along with JSON escaping) from `HttpOtelTracer.kt` into a shared `JsonEncoding.kt` (`escapeJsonString()`, `randomHexId()`) so both files use one implementation.
3. ✅ All interpolated string fields (`query`, `responseText`, error messages) now pass through `escapeJsonString()`.
4. ✅ The real Critic-Actor loop is now wired into the stream: `CriticActorAIService.generateChatResponse` reports `ACTOR_START`/`ACTOR_DONE`/`CRITIQUE_START`/`CRITIQUE_DONE` via a new opt-in coroutine-context hook (`CriticProgress.kt`, mirroring `HttpOtelTracer`'s `SpanCtx` pattern — no `AIService` interface change, so Android/iOS/desktop are unaffected). The SSE endpoint listens via `withContext(CriticProgressContext(...))` and emits a distinct `TOOL_CALL_START`/`TOOL_CALL_RESULT` group for the actor pass and the critique pass, instead of one opaque `queryAllSources` call.
5. ✅ Tests added: `AgentStreamTest.kt` — every emitted `data:` line is valid JSON (including a dedicated escaping test with quotes/newlines/backslashes in the query and response), `runId` is unique per request, and the actor/critique passes surface as their own tool-call event groups (3 start/result pairs: `queryAllSources`, `actorPass`, `critiquePass`). `CriticActorAIServiceTest.kt` — new tests assert the exact phase sequence reported, including the "critique skipped" path when the first pass is blank.

**Known gap carried into 6.5:** the React client (`App.tsx`) doesn't yet render the new intermediate events distinctly — it still shows one fixed reasoning line — and the server still sends the final answer as one `TEXT_MESSAGE_DELTA` rather than word-by-word chunks. Backend plumbing (this phase) is done; the frontend/streaming-UX work is scoped in 6.5.

### 6.3 — React Frontend & Proxy Scaffolding ✅ DONE
* Bootstrap a Vite-React-TypeScript application inside `/web`.
* Set up standard `/api` request redirection to Ktor backend in `vite.config.ts`.
* Establish custom typography (Space Grotesk, Outfit) and layouts in `index.css`.

### 6.4 — Client useAgentStream Connection Hook ✅ DONE
* Develop custom `useAgentStream` hook to manage `EventSource` connections.
* Process streaming AG-UI payloads and distribute updates to React state.

### 6.5 — Dynamic Agentic UI Views ⚠️ PARTIAL — depends on 6.2 fixes above
* Render live "thought bubbles" and reasoning logs during Critic-Actor executions — UI exists in `App.tsx` but only ever renders the single fixed reasoning line the server sends today.
* Stream response texts word-by-word with loading indicators — **not implemented**; server sends the full `responseText` in one `TEXT_MESSAGE_DELTA`, not chunked.
* Render calendar agenda views and source directories dynamically when state updates arrive.

---

## Phase 10 — Hardening Pass (Post-Feature-Complete) ✅ CERTIFICATION GATE — ALL HARD-1..9 DONE 2026-07-05

**Certification gate:** this entire list (HARD-1 through HARD-9) is a hard prerequisite for
App Store / Play Store submission — see the "Phase 2.5 — Hardening pass" gate in
`DEPLOYMENT_IOS.md` and `DEPLOYMENT_ANDROID.md`. Check items off here as they land; those two
docs reference this section rather than duplicating its content.

> **Provenance:** this plan ports a hardening *methodology*, not any code, from a sibling
> project (Oficio, a separate Borinquen Terrier product) that ran an "Ops Hardening"
> sprint once its feature set stabilized. Every finding below was independently
> re-derived against **this** codebase — file:line cited — not copied or assumed from
> that project. Nothing here references Oficio's actual code or data.

### Why this exists

CEF's roadmap above lists a long, genuinely-shipped feature set: sync hardening,
resilient reset, a reconciler, a fault-injectable test harness, a real OTEL pipeline.
That is unusual — most projects get to "feature complete" long before they get to "has
an immune system." The risk at this stage isn't missing features; it's that a handful
of currently-invisible or currently-silent bugs are sitting underneath a lot of working
functionality, waiting for scale (more users, more time, more documents) to surface
them as support tickets nobody can diagnose.

**The methodology, in one line:** convert invisible or silently-wrong behavior into
visible, bounded behavior — cheapest and currently-bleeding first — and only chase pure
scaling landmines once they're actually close to biting, not speculatively.

**Sequencing principle — six archetypes, in this order:**

| # | Archetype | Question it answers |
|---|---|---|
| 0 | Make failure visible | Can we even tell when something breaks? |
| 1 | Currently-bleeding, cheap, deterministic | What's silently wrong for real users *right now*, with a small fix? |
| 2 | Bound the silent-failure-to-churn class | What external-integration failure produces zero signal until a user quietly gives up? |
| 3 | The decision core (own timeline, highest stakes) | Is the thing the whole product depends on (the AI extraction pipeline) actually measured, and can a regression in it ship silently? |
| 4 | Scaling landmines | What's fine at today's usage but breaks as documents/time/users accumulate? |
| 5 | Structural / self-serve gaps | What feature has a "do" half but no "undo" half? |

Archetype 0 is a prerequisite for verifying everything after it. Archetypes 1–2 are
independent and cheap — do them in any order. Archetype 3 is its own multi-step
timeline (measure, then gate) and is the highest-leverage item in this plan. Archetypes
4–5 are real but lower urgency — CEF is a single-user, local-first app, so "scale" here
means *time and document volume on one install*, not tenant count.

**What this document is not:** a literal restatement of Oficio's task list. Several of
its categories (per-tenant cost caps, billing/dunning, multi-tenant migration
isolation, compliance opt-out) don't apply to CEF's actual shape — a BYOK,
single-user, local-first app with no monetization surface and no shared infrastructure
across users. Those are called out explicitly below as *ruled out*, not silently
dropped.

### Summary

| # | Task | Archetype | Status |
|---|---|---|---|
| HARD-1 | Fail loud (not silent) when telemetry degrades to a no-op tracer in a packaged build | 0 — visibility | ✅ |
| HARD-2 | Fix the hardcoded Fall-2024 default date range in `AddRoutineItemDialog` | 1 — bleeding now | ✅ |
| HARD-3 | Surface a persistent `LOCAL_ONLY` sync failure on event **update** instead of swallowing it | 2 — silent→churn | ✅ |
| HARD-4 | Add real pass/fail thresholds to `SyllabusEvaluationIntegrationTest` | 3 — decision core | ✅ |
| HARD-5 | Wire the real corpus evals into CI as an actual gate | 3 — decision core | ✅ |
| HARD-6 | Cap desktop `debug_logs.txt` growth in packaged release builds | 4 — scale landmine | ✅ |
| HARD-7 | Wire up the already-built override-log retention (`pruneOldLogs`) | 4 — scale landmine | ✅ |
| HARD-8 | Warn before a document is large enough to trigger extra Gemini cost | 4 — scale landmine | ✅ |
| HARD-9 | Full teardown on Google account disconnect, not just token clearing | 5 — structural | ✅ |

### Archetype 0 — Make failure visible at all

**Mostly already done — one residual gap, not a from-scratch build.** CEF already has
a real, wired OTLP exporter (`HttpOtelTracer.kt`, plus a heavier JVM-only
`OtelTracer.kt`), and the specific spans referenced in `RELIABILITY_PLAN.md`
(`calendar.self_heal`, `calendar.reconcile_apply`, `calendar.resilient_clear`,
`gemini.request`) are real, not aspirational. This is a materially stronger starting
position than most projects reach for this pass.

#### HARD-1 — Fail loud when telemetry silently degrades to Noop ✅ DONE 2026-07-04

**What:** `OtelTracer`'s and `HttpOtelTracer`'s `createTracer()` both fall back to
`NoopTracer` when the build-time OTLP env vars (`CEF_OTLP_ENDPOINT`/`USER`/`PASSWORD`,
baked into `BuildSecrets` at build time — see `composeApp/build.gradle.kts:33-35,77-108`)
are unset, with only a `println("[OTEL] Tracing DISABLED...")` as a trace. That println
is invisible in a packaged release binary — nobody sees it, and there is no other
signal that a given build shipped with zero tracing. If a release build is ever cut
without those secrets configured (a CI misconfiguration, a new build machine, a
forgotten env var), every hardening decision that depends on telemetry (including
HARD-4/HARD-5 below) goes blind for that release, and nobody would know until they
went looking for traces that don't exist.

**Acceptance criteria:**
- [x] Release build tooling (`release.sh` / `.github/workflows/release-desktop.yml`)
      fails the build, or at minimum emits a loud warning surfaced somewhere a human
      will actually see it (build log summary, not just a `println` swallowed by
      Gradle output), when the OTLP secrets are unset for a release-tagged build.
- [x] Local/dev builds keep working exactly as today (Noop is the correct dev default
      — this is about *release* builds only).
- [x] Unit test covers: `createTracer()` returns Noop when secrets are blank, and the
      new loud-failure path only fires under whatever condition marks a build as
      release (don't gate on `isDebug` — see HARD-6, that flag has its own bug).

**Resolution:** Added a standalone, never-cached `verifyReleaseTelemetrySecrets` Gradle task
(`composeApp/build.gradle.kts`) that `dependsOn` is attached to any `packageRelease*` task via
`tasks.matching { it.name.contains("packageRelease") }.configureEach { dependsOn(...) }`. It
re-resolves the three OTLP secrets from env/`local.properties`/`.env` on every invocation (never
UP-TO-DATE, so a stale cached success can't hide a later misconfiguration) and fails the build
with a clear message naming exactly which secret(s) are missing. Verified: `packageReleaseDmg`
fails with `.env`'s OTLP block temporarily removed, and succeeds with it restored;
`:composeApp:jvmTest`, `packageDmg` (debug/dev distributable), and the three build targets
(`assembleDebug`, `iosApp:assemble`, `server:assemble`) are all unaffected. Added a pure,
independently-testable `ReleaseTelemetryCheck` object in `OtelTracer.kt` (takes an explicit
`isReleaseBuild: Boolean`, not `isDebug`) with unit tests in `OtelTracerTest.kt` proving the
gating condition — not just the existing Noop-fallback behavior, which was already covered.

**Files:** `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/HttpOtelTracer.kt`,
`composeApp/src/jvmMain/kotlin/com/borinquenterrier/cef/OtelTracer.kt`,
`composeApp/src/jvmTest/kotlin/com/borinquenterrier/cef/OtelTracerTest.kt`,
`composeApp/build.gradle.kts`

### Archetype 1 — Currently-bleeding, cheap, deterministic

#### HARD-2 — Fix the hardcoded Fall-2024 default date range ✅ DONE 2026-07-04

**What:** `AddRoutineItemDialog.kt:52-53` defaults a new routine item's recurrence
window to `LocalDate(2024, 8, 26)`–`LocalDate(2024, 12, 13)` — a specific past
semester, hardcoded as UI initial state with no validation forcing the user to change
it before saving. The `Save` handler builds the `TimeEvent`/`Recurrence` directly from
these values with no guard. As of today this window is already well over a year in
the past — any student who adds a recurring routine (a weekly class, a standing study
block) without explicitly opening both date pickers gets a recurrence that will never
fire, silently, with zero error and zero telemetry event. This is exactly the "one
literal that was correct for a moment, now silently wrong for everyone" shape — it's
been shipping since whenever this dialog was last touched, and it bleeds on every
session where a user doesn't happen to open the date fields.

**Acceptance criteria:**
- [x] Default `startDate`/`endDate` derive from the current date (or the student's
      active-semester window, if that's already resolvable at this call site —
      `CalendarReconciler`'s semester-window logic may already have something
      reusable) instead of a hardcoded 2024 literal.
- [x] Add a save-time guard: reject (or warn on) a recurrence window that's already
      fully in the past, so this class of bug can't reappear via a different stale
      default later.
- [x] Test: creating a routine item with the dialog's untouched defaults produces a
      `Recurrence` whose `endDate` is in the future relative to a fixed clock.

**Resolution:** `AddRoutineItemDialog` now takes a `today: LocalDate` parameter
(defaulting to `Clock.System.todayIn(TimeZone.currentSystemDefault())`, same pattern
as `EventItemView`/`AcademicCalendar`) and seeds `startDate`/`endDate` from
`SemesterResolver.getSemesterRange(today)` instead of the hardcoded 2024 literals.
The Save button guards on a new `RoutineWindowValidator.isFullyInPast(endDate, today)`
check — a plain, directly-unit-tested function (`RoutineWindowValidatorTest.kt`) — and
shows an inline error instead of calling `onSave` when the window is already stale.
Covered by `AddRoutineItemDialogTest` (untouched-defaults-produce-future-endDate) and
`RoutineWindowValidatorTest` (3 cases: before/equal/after today).

**Files:** `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/AddRoutineItemDialog.kt`,
`composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/RoutineWindowValidator.kt` (new)

### Archetype 2 — Bound the silent-failure-to-churn class

**Scoped down from Oficio's version of this archetype.** Most of Oficio's
Archetype-2 shape (a paid external API declining, a webhook redelivery loop) doesn't
map onto CEF — there's no billing surface (confirmed absent — see "Ruled out," below)
and the Gemini integration already surfaces every failure path it hits (also
confirmed — see "Ruled out"). One real gap survives on the Google Calendar side.

#### HARD-3 — Surface a persistent `LOCAL_ONLY` sync failure on update ✅ DONE 2026-07-04

**What:** `RemoteFirstWriter.save()` (lines 11-22) correctly falls back to
local-only *and rethrows* `RemoteSyncFailedException` so a caller can react. Its
sibling, `RemoteFirstWriter.update()` (lines 27-38), does the identical local-only
fallback but **does not rethrow** — it only logs (`logger?.e(tag, "Remote update
failed, falling back to local-only update", e)`) and returns normally, as if the
update succeeded. The same catch-and-log-only shape appears in `SyncNegotiator.kt`'s
push (lines 66-71) and remote-delete (lines 51-62) paths. There is no UI surface
anywhere that reads `SyncStatus.LOCAL_ONLY` to tell a student "this edit didn't reach
Google Calendar" — `LocalOnlyRetrier` retries silently on the next sync, which is the
right behavior for a *transient* failure, but if the underlying cause is persistent
(the target calendar was deleted server-side, a scope was revoked in a way that
doesn't trip the already-handled 401 path), the event can sit in `LOCAL_ONLY`
indefinitely with a debug log as the only trace. A student who edits or moves a
deadline believes it's synced; it silently isn't.

**Acceptance criteria:**
- [x] `update()` either rethrows like `save()` does, or — if silently degrading to
      local-only on update really is the intended UX for transient failures — the app
      surfaces a persistent (not one-shot) indicator once an event has been
      `LOCAL_ONLY` for longer than some threshold (e.g. survives N sync cycles), not
      just a debug log line.
- [x] Same treatment for `SyncNegotiator`'s push/delete catch-and-log-only paths.
- [x] At minimum, a "N events haven't synced to Google Calendar" indicator exists
      somewhere reachable (Settings or the calendar view) — today grepping the whole
      UI layer for any reference to `SyncStatus` (including `LOCAL_ONLY`) returns
      nothing.
- [x] Test: an `update()` call whose remote write throws a non-`CalendarNotFoundException`
      error is caught by the existing reconciler/harness scenario tests and asserted
      to leave a durably-discoverable trace, not just a log line.

**Resolution:** Kept `RemoteFirstWriter.update()`'s existing no-rethrow behavior —
`update()` backs frequent, low-stakes call sites (check-in complete/skip,
reschedule, timestamp-repair stamping) where rethrowing on every transient network
blip would surface a scary error for what is otherwise a successful local action;
the existing `LocalOnlyRetrier`/`SyncNegotiator` retry-next-sync behavior is correct
for transient failures. Instead, went with the durable-indicator half of the "OR":
`SyncNegotiator` now takes a `Logger?` and logs both the push and delete
catch-and-continue paths in `pushLocalChanges()` (previously comment-only, zero
trace). `CalendarAgent` exposes a new `unsyncedCount: StateFlow<Int>` backed by
`localRepo.getEventsBySyncStatus(LOCAL_ONLY, calendarId)`, refreshed after
`updateEvent`, `saveEvent`, `synchronize`, `retryLocalOnly`, and `checkHealth` (the
last one runs at app startup, so the count is populated before the user ever opens
Settings). `SettingsScreen` shows "N events haven't synced to Google Calendar" when
Google is linked and the count is nonzero — the first UI surface anywhere reading
`SyncStatus`. Covered by new tests in `SyncNegotiatorTest.kt` (logger invoked on
push/delete failure) and `CalendarAgentTest.kt` (`unsyncedCount` reflects LOCAL_ONLY
events after retry and after a failed `updateEvent`, proving the LOCAL_ONLY row is a
durably-discoverable DB trace, not just a log line).

**Files:** `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/SyncNegotiator.kt`,
`composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/CalendarAgent.kt`,
`composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/SettingsScreen.kt`

### Archetype 3 — The decision core (own timeline, highest stakes)

**This is the single highest-leverage item in this plan** — the same role
Oficio's qualification-agent reliability gate played there. CEF's whole product
depends on the Gemini extraction pipeline actually getting syllabus dates right; today
that reliability is unmeasured in any way that can block a regression from shipping.

#### HARD-4 — Add real pass/fail thresholds to `SyllabusEvaluationIntegrationTest` ✅ DONE 2026-07-05

**What:** `SyllabusEvaluationIntegrationTest.kt` already does the hard part — it runs
2 real syllabi through the actual `RealAIService.generateCalendarEvents()` and
computes recall% and date-accuracy% against hand-labeled expected-JSON fixtures
(lines 121-146). But the test body only `println`s the results table; there is no
`shouldBe`/threshold assertion anywhere in it. A recall regression from 90% to 10%
would not fail this test — it would print a much worse number and pass anyway. By
contrast, two sibling tests in the same package, `ContributorPdfIntegrationTest.kt`
(`failures.size shouldBe 0`) and `StlccIntegrationTest.kt` (several `shouldBeGreaterThan`
assertions against the real `contributions/` corpus), already have exactly the
assertion shape this test is missing.

**Acceptance criteria:**
- [x] Replace the results-table `println` with real assertions — a minimum recall%
      and date-accuracy% threshold, chosen from the current measured baseline (run it
      once, record the number, set the bar at or slightly below today's actual
      performance so this change doesn't immediately fail CI on a pre-existing dip).
- [x] Keep the printed table for humans — assert *and* report, not one or the other.
- [x] Do not silently loosen `ContributorPdfIntegrationTest`/`StlccIntegrationTest`'s
      existing assertions while touching this file.

**Resolution:** Ran the suite once against the real Gemini API to measure the
baseline: both fixture syllabi (`syllabus_bdan250.pdf`, `syllabus_hist152.pdf`)
scored 100% recall and 100% date accuracy (8/8 expected events matched, 8/8 matched
dates correct). Added `totalExpected`/`totalMatched`/`totalDateCorrect` accumulators
across the `testCases.forEach` loop and two `withClue`-wrapped assertions after it —
aggregate recall and aggregate date accuracy must each be `shouldBeGreaterThanOrEqual`
80.0%, a threshold chosen below the 100% baseline so a single flaky miss across the
8-event corpus (87.5%) doesn't fail CI, while a real regression (e.g. the 90%→10%
scenario this task exists to catch) still fails loudly. The per-file `println` table
is unchanged — assert *and* report. `ContributorPdfIntegrationTest.kt` and
`StlccIntegrationTest.kt` were not touched.

**Files:** `composeApp/src/jvmTest/kotlin/com/borinquenterrier/cef/SyllabusEvaluationIntegrationTest.kt`

#### HARD-5 — Wire the real corpus evals into CI as an actual gate

**What:** The infrastructure for a real reliability gate already exists in the
codebase — real assertions (once HARD-4 lands), a real 20-file corpus across
`contributions/mo/...` and `contributions/tx/ut_austin/...`, and three eval-shaped
test classes. None of it runs in CI, ever, on any workflow:
`composeApp/build.gradle.kts:390-391` excludes every AI integration test from the
default `:composeApp:jvmTest` task unless `-PrunAITests=true` is passed, and neither
`pr-check.yml` nor `release-desktop.yml` ever passes that flag for anything except
the unrelated `GoogleOAuthIntegrationTest` — nor do they set `CEF_GEMINI_API_KEY`
anywhere, so even a manually-flagged run would resolve no API key and skip. The
`validate-contributions` CI job only checks path-namespace/poison-content
(`scripts/validate_contributions.py`) — it never runs the extraction pipeline or
measures quality. Net effect: a prompt change, a model swap, or a parser regression
that tanks real-world extraction accuracy can merge and ship with nothing in CI
noticing, exactly the gap Oficio's OPS-11/OPS-12 closed for its own AI pipeline.

**Sequencing note, matching the sibling project's own precedent:** that project
originally assumed it needed a from-scratch eval harness and, on inspection, found
the actual fix was cheaper than assumed (see its own OPS-13 sub-task breakdown for
the shape of "measure first, don't build before you've measured"). Do the same
here — HARD-4 first (make the existing eval assert something), then land this task,
then decide from real numbers whether a corpus eval belongs on every PR or on a
cheaper cadence (nightly/pre-release only), the same cost-vs-signal tradeoff
`AGENTS.md`'s Integration Test Naming Convention section already gestures at.

**Acceptance criteria:**
- [ ] Decide and document the cadence: every PR (if the corpus is cheap/fast enough),
      or gated to a scheduled/pre-release run (if a real Gemini API key's cost or
      latency makes per-PR runs impractical) — this is a real tradeoff to make
      explicitly, not default silently to "never," which is today's status quo.
- [ ] Whichever cadence is chosen, add the required secret
      (`CEF_GEMINI_API_KEY`/equivalent) to the relevant workflow and pass
      `-PrunAITests=true` for the eval test classes specifically (not blanket-enabling
      every `IntegrationTest`-suffixed class, which would also pull in the live
      Google OAuth test).
- [ ] A regression below the HARD-4 threshold, or a `ContributorPdfIntegrationTest`/
      `StlccIntegrationTest` assertion failure, fails that workflow run — confirm by
      deliberately breaking one assertion locally and running the wired CI step (or
      its local equivalent) to see it fail before considering this done.
- [ ] Document the eval-cost tradeoff (API cost per run × chosen cadence) somewhere
      discoverable (this section or `AGENTS.md`), matching the transparency Oficio's
      own `AGENTS.md` gives its eval-cost discipline — so a future contributor
      understands why the cadence was chosen, not just what it is.

**Files:** `.github/workflows/pr-check.yml` and/or a new scheduled workflow,
`composeApp/build.gradle.kts`, `AGENTS.md` (document the cadence decision)

**Resolution:** Added a new scheduled workflow, `.github/workflows/eval-corpus.yml`,
rather than adding these tests to `pr-check.yml`. Cadence chosen: **nightly**
(`cron: '0 8 * * *'`) plus manual `workflow_dispatch`, not every PR — a full run
makes ~20-60 real Gemini calls across the 16-file `contributions/` corpus + 3
STLCC-specific docs + 2 syllabus fixtures, using the BorinquenTerrier paid
(Tier 1) `CEF_GEMINI_API_KEY`, not a free-tier key — the free-tier "all models
share one RPM/RPD quota" failure mode described in this file's own "Observed
failure mode (June 2026)" note is about students' own per-user runtime keys,
not this CI secret. Gating on every PR would still contend for this key's
quota against concurrent PRs and the app's own runtime usage. The workflow runs exactly
the 3 eval-shaped classes via `--tests` (not a blanket `-PrunAITests=true` run,
which would also pull in `GoogleOAuthIntegrationTest`). `CEF_GEMINI_API_KEY` was
added as a GitHub Actions repo secret, used only by this workflow — confirmed it
is not wired into `generateBuildSecrets`, so it never ships inside a packaged
binary (a scoped exception to the Gemini key normally being a per-user runtime
`Settings` value, explicitly approved for this CI-test-only use). Since
`resolveApiKey()` returns `null` and skips (not fails) when no key is found, the
workflow adds a "Verify Gemini API key is configured" step that fails the job
outright if the secret is empty — so a revoked/deleted secret shows up as a red
job, not a suite of silently-skipped tests. Verified the gate actually fails:
locally ran `SyllabusEvaluationIntegrationTest` with `MIN_RECALL_PERCENT`
temporarily set to an unreachable 999.0 via the exact CI invocation
(`-PrunAITests=true --tests ...`) against the real Gemini API — the build failed
with the expected `AssertionFailedError`; reverted immediately after confirming.
Full tradeoff writeup lives in `AGENTS.md`'s new "AI Eval Corpus Gate" section.

### Archetype 4 — Scaling landmines: fine now, fatal later

CEF's version of "scale" is a single install accumulating state over months/years of
real use, or one unusually large document — not tenant count. Lower urgency than
Archetypes 1–3, but real.

#### HARD-6 — Cap desktop `debug_logs.txt` growth in release builds ✅ DONE 2026-07-04

**What:** `Platform.jvm.kt`'s `writeLogToFile()` appends to `debug_logs.txt` forever
with no size cap, gated only by `isDebug` — and `isDebug` (line 8) defaults to
**true** unless something explicitly sets the `debug` system property or `DEBUG` env
var to `"false"`. Neither `release-desktop.yml` nor `composeApp/build.gradle.kts` sets
that for packaged builds. Android (`Platform.android.kt`) and iOS (`Platform.ios.kt`)
both already cap this at `MAX_LOG_FILE_BYTES = 500_000` with `takeLast` trimming —
desktop is the one platform that shipped without the cap its siblings already have.
(Confirmed the leak was real: the local repo's gitignored `debug_logs.txt` had grown
to 36MB before this fix.)

**Acceptance criteria:**
- [x] Desktop `writeLogToFile()` gets the same size cap + trim behavior Android/iOS
      already implement (extract to shared code if it isn't already, rather than a
      third independent copy). Extracted `capLogContent()`/`MAX_LOG_FILE_BYTES` into
      commonMain's new `LogFileCap.kt`; Android and iOS now call the shared function
      too instead of each carrying its own copy of the same trim logic.
- [x] Packaged release builds explicitly set `isDebug = false` (or an equivalent
      release-vs-dev distinction that doesn't rely on an env var nobody sets) so this
      doesn't also silently regress via the `isDebug` default itself. Added a
      `generateJvmBuildFlags` Gradle task that bakes `IS_PACKAGED_DESKTOP_RELEASE`
      into a compiled constant from the actual requested task names (true only when
      a `packageRelease*` task is invoked), so a release build can't ship debug-on
      just because nobody remembered to set a flag — `isDebug`'s gating logic itself
      is the pure, unit-tested `computeIsDebug()` in `DesktopBuildFlags.kt`.
- [x] Test: `writeLogToFile()` called past the cap trims rather than growing
      unbounded. `capLogFile()` (the file-level wrapper `writeLogToFile()` calls) is
      covered directly in `LogFileCapJvmTest`, including a repeated-append case that
      asserts the file never exceeds the cap; `capLogContent()`'s pure trim logic is
      covered in commonTest's `LogFileCapTest`.

**Files:** `composeApp/src/jvmMain/kotlin/com/borinquenterrier/cef/Platform.jvm.kt`,
`DesktopBuildFlags.kt` (new); `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/LogFileCap.kt`
(new); `composeApp/src/androidMain/.../Platform.android.kt`, `composeApp/src/iosMain/.../Platform.ios.kt`;
`composeApp/build.gradle.kts` (`generateJvmBuildFlags` task).

#### HARD-7 — Wire up the already-built override-log retention ✅ DONE 2026-07-04

**What:** `UserPreferenceMemoryRepository.pruneOldLogs(olderThanMs)` is fully
implemented (`SqlDelightUserPreferenceMemoryRepository.kt:47`) but has exactly one
caller in the entire codebase: its own test. `logOverride()` writes a row every time a
user's manual edit diverges from an AI suggestion, and nothing ever prunes that table
— it grows for the life of the install, feeding `getDerivedConstraints()`'s preference
inference indefinitely. The fix here is wiring, not building — the retention logic
already exists and is already tested in isolation.

**Acceptance criteria:**
- [x] Call `pruneOldLogs()` from an existing recurring entry point — `AgentHarness`'s
      startup/daily poll (already referenced above) is the natural home, matching how
      this class of periodic-maintenance concern is handled elsewhere. `runHarness()`
      is already gated behind `PollScheduler.shouldPoll()` (once per 24h unless
      forced), so calling `pruneOldLogs()` unconditionally inside its success path
      (right after calendar sync, before `setLastPollTime`) gives it the same daily
      cadence without adding a second scheduling mechanism.
- [x] Choose and document a retention window (e.g. keep enough history for
      `getDerivedConstraints()` to still see meaningful patterns — this is a product
      judgment call, not a technical one; state the reasoning wherever the constant
      lives). Added `UserPreferenceMemoryRepository.OVERRIDE_LOG_RETENTION_MS` (30
      days) with the reasoning inline: long enough for `getDerivedConstraints()`'s
      4-occurrence threshold to still catch a recurring weekly pattern, short enough
      that a semester-old habit doesn't keep suppressing a schedule the student has
      since changed. `getDerivedConstraints()` previously hardcoded its own duplicate
      30-day literal for an opportunistic prune-on-read — refactored it to reference
      the same constant so the two prune paths (periodic + opportunistic) can't drift.
- [x] Test: `AgentHarness`'s periodic run actually invokes pruning (not just that
      `pruneOldLogs()` works in isolation, which is already covered). Added
      `AgentHarnessTest`: "prunes old override logs on a successful run" (verifies the
      call fires when the poll proceeds) and "does not prune override logs when the
      poll is skipped" (verifies it doesn't fire when `shouldPoll()` returns false).

**Resolution:** `AgentHarness` now takes a `UserPreferenceMemoryRepository` and calls
`pruneOldLogs(now - OVERRIDE_LOG_RETENTION_MS)` on every successful harness run,
wired through `DependencyContainer`'s existing `userPreferenceMemoryRepository`
instance. Verified: full JVM suite passes, CRAP.md regenerated (`AgentHarness.kt`
stays 🟢 LOW, coverage rose from 83.3% to 84.8%), `:composeApp:assembleDebug`,
`:server:assemble`, and `:iosApp:assemble` all pass.

**Files:** `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/AgentHarness.kt`,
`UserPreferenceMemoryRepository.kt`, `SqlDelightUserPreferenceMemoryRepository.kt`,
`DependencyContainer.kt`; `composeApp/src/jvmTest/kotlin/com/borinquenterrier/cef/AgentHarnessTest.kt`

#### HARD-8 — Warn before a document is large enough to trigger extra Gemini cost ✅ DONE 2026-07-05

**What:** Lower priority than HARD-6/7 given BYOK — it's the student's own key and
own money, not CEF's, so this isn't a cost-to-the-business risk the way Oficio's
per-tenant Claude cap was. But there's still zero signal to the user before a large
document (>20MB inline cap, routed through the Gemini Files API —
`GeminiAIService.kt:100,269`, `GeminiFileUploader.kt:16-17`) burns more of their quota
or takes noticeably longer. A confused student with no visibility into why a
particular upload is slow or ate their day's free-tier quota has no way to connect
that experience to "this one document was unusually large."

**Acceptance criteria:**
- [x] Before routing a document through the Files API path (i.e. once it's known the
      document exceeds the inline-request size), surface a lightweight, dismissible
      signal to the user — "this document is large, processing may take longer/use
      more of your API quota" — not a hard block. Extracted the existing inline-vs-
      Files-API size check into a public `GeminiAIService.exceedsInlineDocumentLimit(bytes)`,
      called from `SourceNormalizer.normalizePdf()` (the only path that reaches
      `extractTextFromDocument`, gated on the PDF being image-only) to fire a new
      `onLargeDocumentDetected` callback. `IngestionAgent` wires that callback to a
      `largeDocumentNotice: StateFlow<String?>`, cleared at the start of every
      `addLocalFile`/`addUrl` call. `IngestingProgressDialog` (already shown while
      ingesting) gained an optional `notice` line with its own "Dismiss" button —
      dismissing only hides the text; it doesn't touch the ingestion running
      underneath, which continues regardless.
- [x] No behavior change to the actual routing/upload logic — this is purely a
      user-facing signal added at the existing size-check branch point.
      `exceedsInlineDocumentLimit` replaced the inline `bytes.size > INLINE_DOCUMENT_LIMIT_BYTES`
      comparison at the Files-API branch with a call to the same shared function, so
      there's one size check, not two.

**Resolution:** Verified with new tests at every layer: `GeminiAIServiceTest`
(`exceedsInlineDocumentLimit` boundary), `PdfVisionFallbackTest` (the callback fires
only for a text-less PDF over the cap, not for a small scan or a large PDF that
already has extractable text), `IngestionAgentTest` (`largeDocumentNotice` sets and
clears correctly across ingest calls), and `IngestingProgressDialogTest` (the notice
renders and is dismissible without affecting the rest of the dialog). Full JVM suite,
CRAP regen, and all three build targets pass — `GeminiAIService.kt` was already
🔴 HIGH-CRAP pre-existing debt (35.21 before this change); the one-line pure
`exceedsInlineDocumentLimit` function nudged it to 36.22, not something this task
introduced or was scoped to fix.

**Files:** `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/GeminiAIService.kt`,
`SourceNormalizer.kt`, `IngestionAgent.kt`, `IngestingProgressDialog.kt`,
`CommonSourceProviders.kt`; tests in `composeApp/src/jvmTest/kotlin/com/borinquenterrier/cef/`
(`GeminiAIServiceTest.kt`, `PdfVisionFallbackTest.kt`, `IngestionAgentTest.kt`,
`IngestingProgressDialogTest.kt`)

### Archetype 5 — Structural / self-serve gaps

#### HARD-9 — Full teardown on Google account disconnect ✅ DONE 2026-07-05

**What:** `GoogleAccountFlow.disconnect()` (lines 73-78) only clears OAuth tokens and
flips the connection state to `Unlinked`:

```kotlin
fun disconnect() {
    println("[GoogleAccountFlow] Transition: * -> Unlinked")
    authService.logout()
    tokenRepository.clearTokens()
    _state.value = GoogleConnectionState.Unlinked
}
```

It does not clear locally-cached `SyncStatus.SYNCED` events, reset the selected
calendar-id/name preference, or touch the local event tables at all. The only UI entry
point is a bare "Disconnect Account" button (`GoogleCalendarPanel.kt:107-113`) with no
confirmation dialog and no messaging about what is or isn't retained. This is the same
shape as a self-serve cancellation flow that clears billing but forgets the data — the
"connect" half of this feature is well-built (it even rolls back cleanly on partial
failure — see "Ruled out," below); the "disconnect" half was never given the same
teardown treatment.

Note this is genuinely narrower than a first read of `disconnect()` suggests:
`CalendarIdResolver.getCEFCalendarId()` does partially self-heal the calendar-ID
consequence on a later reconnect (re-resolves by name, or recreates if the saved ID
no longer exists). What's missing is a deliberate, user-facing choice about the
previously-synced *event data* — today it just sits there, silently stale, associated
with a connection that no longer exists.

**Acceptance criteria:**
- [x] Add a confirmation step to "Disconnect Account" that states plainly what will
      happen to previously-synced events (kept locally as unsynced records? cleared
      entirely? — this is a product decision to make explicitly, not default
      silently).
- [x] Whichever choice is made, implement it: either purge the `SyncStatus.SYNCED`
      rows and reset the calendar-id preference, or explicitly flip them to a
      "disconnected, not synced" status the UI can show truthfully.
- [x] Test: after `disconnect()`, local event state matches whatever the chosen
      policy promises — today there is no test asserting anything about local state
      post-disconnect, only that tokens are cleared.

**Product decision (explicit, not a default):** neither of the two options above,
on reflection — "disconnect" is not meant to be a data-purge action, and bundling
one into it would be surprising. The chosen policy is a third path: disconnect
touches **zero** local event data. Previously-`SYNCED` rows are left exactly as
they are; nothing is purged, nothing is bulk-flipped to a new status. This avoids
two real risks the other options carried: (1) purging is destructive and
irreversible for a local-first app where CEF's DB, not Google, is the source of
truth; (2) bulk-flipping `SYNCED` → `LOCAL_ONLY` would lose the association with
the still-valid remote event, risking a duplicate push on reconnect (Google
Calendar's actual event isn't deleted by disconnecting, only CEF's link to it).
A dedicated "clear synced calendar data" action, if wanted later, deserves its own
explicit, separately-confirmed affordance — not something inherited for free by
"Disconnect Account." The gap this task actually closes is the missing
**confirmation + honest messaging**, not a change to what data disconnect touches.

**Resolution:** `GoogleCalendarPanel.kt`'s "Disconnect Account" button no longer
calls `disconnect()` directly — it opens a confirmation `AlertDialog` ("Disconnect
Google Calendar?") stating plainly that only the connection is removed, events
already in CEF stay on-device, and reconnecting resumes syncing. Only the dialog's
"Disconnect" button calls `container.googleAccountFlow.disconnect()`; "Cancel"
dismisses without side effects. `GoogleAccountFlow.disconnect()` itself is
unchanged — it structurally has no reference to local event storage, which is
itself evidence the "zero local data touched" policy was already true in practice,
just never confirmed or communicated to the user. New `GoogleCalendarPanelTest.kt`
covers all three paths (open confirmation without disconnecting, cancel without
disconnecting, confirm calls `disconnect()` exactly once). Verified: full JVM
suite, CRAP regen (no change — `GoogleCalendarPanel.kt` is `@UiOnly`, excluded from
CRAP tracking like other Compose files), and all three build targets pass.

**Files:** `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/GoogleCalendarPanel.kt`;
`composeApp/src/jvmTest/kotlin/com/borinquenterrier/cef/GoogleCalendarPanelTest.kt` (new)

### Build order

```
HARD-1                                          Archetype 0 — prerequisite (thin: signal-only fix)
HARD-2                                          Archetype 1 — standalone, do first (cheapest, bleeding now)
HARD-3                                          Archetype 2 — standalone
HARD-4 → HARD-5                                 Archetype 3 — own timeline, sequential (measure, then gate)
HARD-6  HARD-7  HARD-8                          Archetype 4 — any order, all independent
HARD-9                                          Archetype 5 — standalone
```

### Ruled out — investigated, not a gap

Listed explicitly so this section stays honest about what was actually checked,
rather than silently omitting categories that don't apply:

- **Per-user/per-tenant Gemini cost cap** — CEF is BYOK; the student's own key means
  there's no shared-infrastructure cost for CEF to bound the way Oficio bounded
  per-tenant Claude spend. HARD-8 covers the residual "no warning before higher cost"
  gap at a much lower severity.
- **Gemini error handling beyond quota/model-negotiation** — `GeminiRequestExecutor`
  categorizes every failure path (401/403/structural/quota/rate-limit/transient) and
  either retries, blacklists a model, or throws. No silently-swallowed Gemini failure
  was found.
- **Per-item fault isolation in batch operations** — `ResilientCalendarCleaner`,
  `SyncNegotiator`, `CalendarAgent.applyReconciliation()`, and `SourceAdder` all
  already isolate per-event/per-source failures so one bad item doesn't abort an
  entire sync/reconcile/reset/ingest batch. This is a real, already-shipped strength
  (matches `RELIABILITY_PLAN.md`'s own F3 claim) — no task needed.
- **Google account connect-flow rollback** — `GoogleAccountFlow.connect()` already
  clears tokens and transitions to `Error` if post-token validation fails, rather than
  leaving "tokens saved but unusable" state hanging. Only the disconnect half (HARD-9)
  has a gap.
- **Billing/subscription/payment system** — confirmed absent (`grep -rliE
  "stripe|billing|subscription|payment"` returns no real hits). Nothing to harden;
  Oficio's whole Archetype-2 billing-decline/dunning shape doesn't apply here.
- **Compliance opt-out (STOP/SMS-equivalent)** — no SMS/A2P surface exists in CEF at
  all; not applicable.
- **Per-tenant DB migration isolation** — CEF has one local SQLite DB per install, no
  multi-schema/multi-tenant concept; not applicable.

---

## Dependency Graph

```
Phase 1.1 (Multi-Source Chat)
    └── Phase 2.1 (Progress Tracking)  [benefits from full event set]
    └── Phase 2.3 (Weighted Deliverables) [same context aggregation pattern]

Phase 1.2 (.ics Export)           [standalone — no deps]

Phase 1.3 (Sync Hardening)        [standalone — no deps]

Phase 2.2 (Study Preferences)
    └── Phase 2.3 (Weighted Deliverables) [shares preferences infra]
    └── Phase 3.3 (Fragment Indexing) [builds on query infra]

Phase 3.1, 3.2 are standalone.

Phase 4.1 (Test Syllabi)
    └── Phase 4.2 (Offline Evals) [requires test syllabi]
    └── Phase 4.3 (Production Telemetry) [builds on test observations]
```

---

## Phase 11 — Supply-Chain Hardening 🔴 PROPOSED — prioritized above Phase 12, not started

**Full plan:** [`docs/ops/supply-chain-hardening.md`](docs/ops/supply-chain-hardening.md). Proactive
— written after reading a third-party report on obfuscated build-config payloads beaconing to a
blockchain C2 endpoint, plus tampered git history. **No compromise detected in this repo or on any
project developer machine** — this is prevention, not incident response. A twin plan exists for the
sibling `oficio` repo.

**Why this comes before Phase 12:** explicit priority call — hardening the application takes
precedence over shipping a new feature (Outlook Calendar) until the cheap, load-bearing items land.

**Confirmed gaps in this repo (checked 2026-07-09, not assumed):** `main` has **no branch
protection at all** (direct pushes and force-pushes both currently possible — the single largest
finding), `.github/CODEOWNERS` doesn't cover build-tooling config (`build.gradle.kts`,
`web/vite.config.ts`, `web/eslint.config.js`), no Dependabot/Renovate, no Gradle dependency
verification.

### Tasks

1. ✅ **DONE 2026-07-09** — **Enable a `main` ruleset — require PR + `pr-check.yml`'s
   `build-and-test` status check, with the owner (`RepositoryRole: admin`) on the bypass list.**
   Live: ruleset `Protect main` (id `18722247`), `enforcement: active`, targets `~DEFAULT_BRANCH`.
   Rules: `deletion` (restrict), `non_fast_forward` (block force pushes), `pull_request` (required,
   0 approvals — owner is sole reviewer via bypass), `required_status_checks` → `Build and run JVM
   tests`. Bypass: `RepositoryRole` id 5 (Repository admin), `bypass_mode: always` — confirmed via
   `gh api .../rulesets/18722247` returning `"current_user_can_bypass":"always"`. Use Repository
   Rulesets, not classic
   branch protection — confirmed available on this repo (`gh api repos/.../rulesets` → `[]`, not
   403). CEF is OSS-intended with a permanent sole owner and no planned internal contributors
   (external contributions only ever arrive as fork PRs, already naturally gated); `git log` shows
   zero merge commits in this repo's history, including `release.sh`'s automated version-bump
   commits, so a no-exceptions PR rule would break the existing workflow — the bypass list is what
   makes this safe to turn on. Highest priority; fixes the single largest gap found, independent of
   the supply-chain angle specifically. See the ops doc's Harden §1 for the explicit limitation
   (doesn't protect against a compromised owner machine — that's Detection's job).
2. ✅ **DONE 2026-07-09** — **Extended `.github/CODEOWNERS`** to cover all Gradle build files
   (`build.gradle.kts` and `settings.gradle.kts` at root, plus `androidApp/`, `composeApp/`,
   `iosApp/`, `server/`, `shared/` module-level `build.gradle.kts`), `web/vite.config.ts`,
   `web/eslint.config.js`, and iOS build settings (`iosApp/Configuration/` — holds
   `Config.xcconfig` — and `iosApp/iosApp.xcodeproj/`) — same required-review treatment the
   existing LLM-pipeline entries get. Verified with `gh api repos/:owner/:repo/codeowners/errors`
   returning `{"errors":[]}` (no syntax errors).
3. ✅ **DONE 2026-07-09** — Detection grep step, built as part of Task 4's weekly local job rather
   than a `pr-check.yml` step — flags `eval(`, `atob(`/`btoa(`, `new Function(`, and long
   base64-shaped literals in build-tooling config files. (Anomalous `setInterval`/`setTimeout`
   detection was scoped but not implemented — grep can't easily distinguish "anomalous" scheduling
   from legitimate use; flagged as a known gap, not silently dropped.)
4. ✅ **DONE 2026-07-09** — Scheduled re-scan, built as a **local `launchd` job**
   (`scripts/supply-chain-audit.sh` + `~/Library/LaunchAgents/com.borinquenterrier.supplychainaudit.plist`,
   weekly Monday 9am) instead of a GitHub Actions workflow — see the ops doc's Detection §5 for why
   (the reflog-divergence and process-audit checks bundled into the same run need local-machine
   access CI can't provide). Covers both this repo and the sibling `oficio` repo in one run. Does
   **not** close the CI-blind-spot half of the original framing (a payload landing via direct push
   still wouldn't trigger `pr-check.yml`) — that's now moot given Task 1's ruleset requires a PR
   for anyone without bypass, but worth noting this task's shape changed from the original plan,
   not just its status.
5. ✅ **DONE 2026-07-09** — **Enable Gradle dependency verification** (`gradle/verification-metadata.xml`).
   Generated checksum-only (sha256) metadata for all Gradle modules across two passes (Android/JVM/
   server, then iOS separately per the OOM-avoidance note) — 1297 components, including buildscript/
   plugin dependencies. Verified it actually enforces (not just present) by corrupting one checksum,
   forcing a re-download of that artifact, and confirming the build failed with Gradle's tamper
   warning, then restoring the correct file. See
   [`docs/ops/supply-chain-hardening.md`](docs/ops/supply-chain-hardening.md) Harden §2 item 3 for
   full detail.
**Priority order for remaining tasks (8, 10) — set 2026-07-09, re-ranked 2026-07-09.** The list
below is numbered by when it was drafted, not by risk/cost. Original ranking by directness against
the threat model (obfuscated build-config payloads + malicious npm lifecycle scripts beaconing to
C2) versus effort was **7 → 6 → 10 → 8** (7 and 6 now done); Walter then explicitly reprioritized
**Task 10 to the end of the stack — 8 → 10** — deferring the cross-repo secret-rotation-runbook
effort (bigger scope: 19 secrets across two repos, scripting, a timed dry-run rehearsal) in favor
of picking up equivalent-effort hardening elsewhere first, including Oficio's twin plan. Task 8
(`devSecrets` Gradle task) is next up in CEF; Task 10 stays last regardless of its
"higher-leverage investment" framing in its own scope note below — that reasoning is still true,
it's just no longer what determines order.

6. ✅ **DONE 2026-07-09** — **Added `.github/dependabot.yml`** covering both ecosystems named in
   this task: `gradle` (directory `/`, which Dependabot's gradle parser walks recursively to pick
   up every module's `build.gradle.kts` — root, `androidApp/`, `composeApp/`, `iosApp/`,
   `server/`, `shared/` — no per-module entries needed) and `npm` (directory `/web`). Both on a
   weekly schedule with minor/patch bumps grouped per ecosystem to keep PR volume manageable for a
   solo maintainer; major-version bumps stay ungrouped so each gets its own PR and changelog
   review. "Mandatory human review" (this task's original framing) is already satisfied by
   construction once Task 1's `main` ruleset is in effect — every PR needs `@borinquenkid`'s
   review — so this task was purely "turn on the updates," not a new review process. **Follow-up,
   same day:** also found (and then enabled) **Dependabot security alerts**, a distinct GitHub
   feature (vulnerability *alerting* + automated security-fix PRs, not the version-update PRs
   above) that had been disabled repo-wide — `gh api -X PUT repos/.../vulnerability-alerts` and
   `gh api -X PUT repos/.../automated-security-fixes`, verified via `security_and_analysis.
   dependabot_security_updates.status: "enabled"`.
7. ✅ **DONE 2026-07-09** — **Restrict `web/`'s `npm install` lifecycle scripts** (`--ignore-scripts`).
   Turned out `pr-check.yml` doesn't build `web/` at all — the only `npm ci` in the pipeline is
   `web/Dockerfile`, the actual production build path via `docker-compose.yml`'s `web` service.
   Added `--ignore-scripts` there after confirming all 150 resolved packages have zero install
   lifecycle scripts (no native-binary-download step to break — Vite 8/esbuild use
   `optionalDependencies`, not scripts). Re-ran `docker build ./web`: `npm ci --ignore-scripts`
   and `npm run build` both succeed unchanged. Full detail in
   [`docs/ops/supply-chain-hardening.md`](docs/ops/supply-chain-hardening.md) Harden §3 item 5.
8. ✅ **DONE 2026-07-10** — **`devSecrets` Gradle task.** **Stepping stone DONE 2026-07-09:**
   [`docs/ops/keychain-secrets-migration.md`](../docs/ops/keychain-secrets-migration.md) — the
   smaller, verified-safe migration is complete for both CEF (6 secrets) and Oficio (13 secrets):
   both `.env` files redacted, both verified against real integration tests, both projects now
   read secrets from macOS Keychain with zero application code changes. **Full task landed
   2026-07-10:** new `buildSrc` module (`DevSecretsResolver`, unit-tested with fakes, 6 Kotest
   cases) resolves the same six secrets from the OS keychain and injects them as env vars into
   `:composeApp:run`'s and `:server:run`'s child JVM process via a `devSecrets` task dependency +
   `doFirst { environment(...) }` — no plaintext file ever written, resolved values stay in-memory
   for the build's lifetime. Missing secrets prompt interactively when run from a real terminal
   (`ConsoleSecretPrompter`, needs `System.console()`); fail fast with a clear message otherwise
   (IDE-launched runs have no console). **Real-world deviation from the original java-keyring-only
   design, confirmed empirically, not assumed:** java-keyring's macOS backend reads via
   Security.framework under the Gradle daemon JVM's own process identity, which differs from the
   `security` CLI identity that originally wrote these entries — triggers a blocking SecurityAgent
   GUI prompt per secret with no non-interactive grant-all option, hangs forever in a headless
   context. Fixed by adding `SecurityCliSecretStore` (shells out to `/usr/bin/security`, same
   mechanism the already-proven-silent `scripts/load-secrets-from-keychain.sh` uses) and picking it
   on macOS via `defaultSecretStoreForOs()`; java-keyring stays in use for Windows/Linux. Verified
   against real Keychain: `:composeApp:devSecrets :server:devSecrets` resolved all 6 secrets
   silently (no prompts), then `:server:run` started and served `HTTP 200` with `devSecrets` wired
   as a real task dependency. Full 3-target build check green
   (`:composeApp:assembleDebug`, `:server:assemble`, `:iosApp:assemble`, run separately per the
   documented combined-build OOM issue).

9. **Manual spot-check of secrets without automated live-call coverage.** Context for a fresh
   session picking this up cold: on 2026-07-09, CEF's and Oficio's plaintext `.env` secrets were
   migrated to macOS Keychain (`docs/ops/keychain-secrets-migration.md`). Each migration was
   verified by re-running a real integration test after the move — but each project only has *one*
   secret-dependent test that makes a real external API call (CEF: `CEF_GEMINI_API_KEY` via
   `AiSchedulingIntegrationTest`; Oficio: `ANTHROPIC_API_KEY` via `ModelNegotiationIntegrationTest`).
   Every other migrated secret was only proven correct via a byte-for-byte diff against the old
   `.env` value (proving the migration mechanism didn't corrupt anything), never actually exercised
   against its real external service post-migration. That's a real, not-yet-closed gap:

   **CEF — all four spot-checked ✅ DONE 2026-07-09** (full detail in
   `docs/ops/keychain-secrets-migration.md`'s "Step 4" section):
   - `GOOGLE_CLIENT_SECRET` ✅ — real interactive Google sign-in via the running desktop app
     (`./gradlew :composeApp:run`); log confirmed token exchange + `Transition: Connecting ->
     Linked`. `GoogleOAuthIntegrationTest`'s missing-`GOOGLE_REFRESH_TOKEN` gap is unaffected by
     this (different code path) — still open, logged separately below.
   - `CEF_OTLP_PASSWORD` ✅ — confirmed transitively via real trace rows reaching OpenObserve.
   - `OOC_TOKEN` ✅ — real `_search?type=traces` API call returned actual trace data (`HTTP 200`).
   - `SONAR_TOKEN` ✅ — `./gradlew :composeApp:checkQualityGate` reached the local SonarQube
     instance, returned `Quality Gate OK`.

   **Oficio — not yet spot-checked** (per the delta in Oficio's own
   `docs/ops/keychain-secrets-migration.md`): Stripe (`STRIPE_SECRET_KEY`/`STRIPE_WEBHOOK_SECRET`),
   Twilio (`TWILIO_AUTH_TOKEN`/`TWILIO_WEBHOOK_SECRET`), `GOOGLE_CLIENT_SECRET`,
   `CLOUDFLARE_API_TOKEN`, `WP_APP_PASSWORD`, `NGROK_AUTHTOKEN`, `ADMIN_SECRET`. No automated test
   exercises any of these with a real call — spot-check each by actually exercising its real flow
   (an actual booking/SMS round-trip for Twilio, a real webhook delivery for Stripe, etc.), not
   just confirming the value loads.

   **Acceptance criteria:**
   - [x] Every CEF secret exercised against its real service post-Keychain-migration, with the
         result (pass/fail) recorded in `keychain-secrets-migration.md` — all 4 passed. Oficio's
         7 secrets remain unchecked (separate repo, out of scope for this pass).
   - [x] No failure to investigate on the CEF side — all 4 passed cleanly. (The stale Google
         session found on app startup was pre-existing and expected, not a migration-caused
         failure — see below.)
   - [x] `GOOGLE_REFRESH_TOKEN`'s missing-value gap explicitly logged as a separately-tracked
         pre-existing issue (not fixed, not conflated with this task) — see
         `keychain-secrets-migration.md`'s "Step 4" section.

   **Files:** `docs/ops/keychain-secrets-migration.md` (CEF and Oficio — record results in both)

10. **Secret rotation runbook.** Context for a fresh session: this was scoped out of the original
    hardening plan after a 2026-07-09 conversation concluded that local secret storage (even
    Keychain) cannot meaningfully defend against an attacker with interactive access past an
    unlocked laptop — hardening local storage further has diminishing returns for a solo developer.
    The higher-leverage investment is capping blast radius fast if a leak is ever suspected or
    confirmed. Today, the incident runbook (`docs/ops/supply-chain-hardening.md` §2) lists "rotate
    everything" as a bare checklist item — untested, with no known time-to-complete.

    **What this task builds:** for every secret across both repos (6 in CEF, 13 in Oficio — full
    list in each repo's `docs/ops/keychain-secrets-migration.md`), determine whether it can be
    rotated via API/script (Stripe, Twilio, GitHub, GCP service accounts are likely candidates) or
    requires manual dashboard action (Anthropic, Google OAuth client secret regeneration, SonarQube
    token likely require this). Build what's automatable. Document exact, step-by-step manual
    procedures (console URLs, required auth/MFA) for what isn't. Then **actually rehearse a full
    dry run once**, timed end to end — the goal is a runbook with a known, proven completion time,
    not an aspirational list that's never been tested under any conditions resembling real use.

    **Acceptance criteria:**
    - [ ] Per-secret table (both repos, all 19 migrated secrets) classifying API-rotatable vs.
          manual-only, with the exact command or console URL for each
    - [ ] Automatable rotations scripted (e.g. a `scripts/rotate-secret.sh <key>` per project, or
          per-provider scripts)
    - [ ] Manual-only rotations documented as exact, step-by-step procedures — not just "log into
          the dashboard," the specific navigation path
    - [ ] At least one full dry-run rehearsal completed and timed (do **not** actually rotate live
          production secrets for the rehearsal unless explicitly directed to — dry-run means
          confirming the mechanism/documentation works, e.g. generating a *new* key alongside the
          old one where the provider supports it, not necessarily invalidating what's currently in
          use)
    - [ ] Runbook referenced from both repos' `docs/ops/supply-chain-hardening.md` §2, replacing
          the current bare checklist with a link to the real thing

    **Files:** superseded 2026-07-10 — per direct instruction, the runbook does **not** live in
    either code repo. It lives in `~/second_brain/ops/runbooks/secret-rotation-runbook.md` (task
    brief for the agent that writes it: `~/second_brain/ops/inbox/2026-07-10-secret-rotation-
    runbook.md`). Both repos get a one-line pointer in their own `docs/ops/supply-chain-
    hardening.md` §2, replacing the current bare "rotate everything" bullet — a link, not a copy.
    Any automatable rotation scripts still belong in each repo's own `scripts/` (repo-specific
    Keychain service name, `.env` shape, and live-deploy update path) — only the runbook document
    itself moved out.

The incident-response runbook (kill processes, rebuild from clean history, rotate every credential,
re-image workstations) lives in the ops doc's §2 as a **documented plan for if Detection ever finds
something** — it is not a task list to execute now. Task 10 above is what turns its "rotate every
credential" line from aspirational into tested.

---

## Phase 12 — Outlook/Microsoft 365 Calendar Provider 🔵 PROPOSED — not started

**Design doc:** [ADR-004](docs/decisions/ADR-004-outlook-microsoft-365-calendar-provider.md). Read that
first — this section is the task breakdown, not the rationale.

> **Provenance:** ADR-004 reuses verified Microsoft Graph API research (`common` tenant
> endpoint, `offline_access` scope requirement, no-admin-consent delegated permissions)
> from a sibling project's ADR (Oficio, a separate Borinquen Terrier product, ADR-0023,
> parked for its own audience) — research only, not code. The architecture below is
> CEF-specific: a second `RemoteCalendarRepository` implementation mirroring
> `GoogleRemoteCalendarRepository` class-for-class, not Oficio's multi-tenant
> three-interface split. See ADR-004's Context section for why the two designs diverge.

### What this closes

CEF's own architecture doc (`AGENTS.md:271`) has named Microsoft Outlook as an intended
external-calendar source since before this phase existed; it was never built. A student on
a university-issued or personal Microsoft account cannot currently self-serve into CEF's
calendar sync without creating a Gmail account. This phase closes that gap with full
two-way sync — parity with what Google Calendar already gets, not a lesser read-only
experience (see ADR-004's Alternatives Considered for why read-only-via-`.ics` was
rejected).

### Tasks

#### MS-1 — Azure AD app registration + build-time secrets plumbing

**What:** Register a multi-tenant Azure AD app (`common` tenant, delegated
`Calendars.ReadWrite` + `offline_access` scopes). Full step-by-step runbook — tenant setup,
redirect URIs per platform, client secret, and the optional/deferred publisher-verification
path — lives in
[`docs/ops/microsoft-azure-app-registration.md`](../docs/ops/microsoft-azure-app-registration.md)
(Part A is required and free; Part B/publisher verification is optional polish, also free,
and should not block MS-2 onward). Extend `generateBuildSecrets`
(`composeApp/build.gradle.kts:29-116`) with `MICROSOFT_CLIENT_ID`/`MICROSOFT_CLIENT_SECRET`,
following the exact same env → local.properties → `.env` → obfuscated `BuildSecrets.kt`
priority chain already built for `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`.

**Acceptance criteria:**
- [ ] Azure AD app registration exists; client ID/secret captured
- [ ] `generateBuildSecrets` emits `MICROSOFT_CLIENT_ID`/`MICROSOFT_CLIENT_SECRET` into
      `BuildSecrets.kt` alongside the existing Google constants
- [ ] `./gradlew :composeApp:jvmTest` green (no behavior change to existing secrets)

**Files:** `composeApp/build.gradle.kts`, `androidApp/build.gradle.kts`

---

#### MS-2 — `MicrosoftCalendarSyncService` (Graph REST client)

**What:** Hand-rolled `ktor-client-cio` REST client against
`https://graph.microsoft.com/v1.0`, mirroring `GoogleCalendarSyncService.kt`'s structure —
no Graph SDK, no MSAL (ADR-004 §1). List/create calendars (`GET/POST /me/calendars`),
event CRUD (`GET/POST /me/calendars/{id}/events`, `PATCH/DELETE /me/events/{id}`), own
`MicrosoftEvent`/`MicrosoftCalendarItem` `kotlinx.serialization` DTOs, own
`toCalendarException` error mapping. Takes a raw bearer-token string for now — no
dependency on MS-3..MS-5's auth work, testable standalone via Ktor `MockEngine` against
fixed Graph JSON fixtures.

**Acceptance criteria:**
- [ ] Calendar list/create, event create/read/update/delete all covered against
      `MockEngine` fixtures
- [ ] Error mapping (401/403/404/429/5xx) produces the same domain exception shapes
      `GoogleCalendarSyncService.kt:66-78` does for Google
- [ ] `./gradlew :composeApp:jvmTest` green

**Files:** `MicrosoftCalendarSyncService.kt` (new), matching test file

---

#### MS-3 — `MicrosoftAuthService` expect declaration + JVM actual (+ Android/iOS stubs)

**What:** `expect class MicrosoftAuthService(settings, appEnv)` with the same
`login()`/`refreshAccessToken()`/`logout()` shape as `GoogleAuthService`. JVM actual
mirrors `GoogleAuthService.jvm.kt`'s `AuthorizationCodeInstalledApp`/`LocalServerReceiver`
local-server flow, retargeted at Microsoft's `/authorize`/`/token` endpoints with
`offline_access Calendars.ReadWrite` scopes. **Kotlin's expect/actual requires every
target to have an actual before any target compiles** — Android and iOS get minimal
stub actuals here (throw `NotImplementedError`) purely to keep the 3-target build green;
MS-4/MS-5 replace the stubs with real implementations. `MicrosoftTokenRepository` mirrors
`GoogleTokenRepository`, storing `MICROSOFT_ACCESS_TOKEN`/`MICROSOFT_REFRESH_TOKEN` in the
same `Settings` store (not SQLDelight, not a file).

**Acceptance criteria:**
- [ ] `expect class MicrosoftAuthService` compiles for all three targets (JVM real, Android/iOS stub)
- [ ] JVM local-server OAuth flow requests `offline_access` explicitly and fails distinctly
      (not silently) when no refresh token comes back — different mechanics from Google's
      `access_type=offline&prompt=consent`, per ADR-004
- [ ] `MicrosoftTokenRepository` passes the same test shape as `GoogleTokenRepositoryTest`
- [ ] `./gradlew :composeApp:jvmTest` green; Android and iOS build (not run) green per
      the project's separate-build-per-platform discipline

**Files:** `MicrosoftAuthService.kt` (new, `expect`), `MicrosoftAuthService.jvm.kt` (new),
`MicrosoftAuthService.android.kt` (new, stub), `MicrosoftAuthService.ios.kt` (new, stub),
`MicrosoftTokenRepository.kt` (new), matching tests

---

#### MS-4 — `MicrosoftAuthService` Android actual

**What:** Replace MS-3's Android stub with a real implementation. No Microsoft-native SDK
equivalent to `GoogleSignIn`/`GoogleAuthUtil` fits the no-SDK constraint, so this is a
browser-based authorization-code + PKCE flow via `CustomTabsIntent` capturing the redirect
URI — structurally closer to CEF's existing **iOS** Google implementation
(`GoogleAuthService.ios.kt`'s PKCE + manual `OAuthExchange`) than to CEF's existing
Android Google implementation. Flagged explicitly in ADR-004 so this isn't built by
copy-pasting the wrong platform file.

**Acceptance criteria:**
- [ ] `CustomTabsIntent` launches Microsoft's consent screen; redirect URI captured and
      exchanged for tokens via PKCE (no client secret on-device)
- [ ] Manually verified end-to-end on a real Android device/emulator with a real Microsoft
      account (per this project's manual-verification convention for OAuth flows, see
      Phase 9's Drive-picker verification)
- [ ] `./gradlew :androidApp:assembleDebug` green (run separately from iOS build per
      `feedback_ios_build_separate`)

**Files:** `MicrosoftAuthService.android.kt`

---

#### MS-5 — `MicrosoftAuthService` iOS actual

**What:** Replace MS-3's iOS stub with a real implementation — direct port of
`GoogleAuthService.ios.kt:46-130`'s `ASWebAuthenticationSession` + PKCE + manual
`OAuthExchange` pattern, retargeted at Microsoft's endpoints.

**Acceptance criteria:**
- [ ] `ASWebAuthenticationSession` flow completes and exchanges the code for tokens via
      PKCE
- [ ] Manually verified end-to-end on a real iOS device/simulator with a real Microsoft
      account
- [ ] iOS build green (run separately from Android per `feedback_ios_build_separate`)

**Files:** `MicrosoftAuthService.ios.kt`

---

#### MS-6 — `MicrosoftAccountFlow`

**What:** Mirrors `GoogleAccountFlow.kt`'s FSA (Unlinked/Connecting/Linked/Error),
constructor-injecting `MicrosoftAuthService`/`MicrosoftTokenRepository`/
`MicrosoftCalendarSyncService` directly, same as Google's does. Depends on MS-2 (sync
service) and MS-3..MS-5 (all three actuals must exist to compile).

**Acceptance criteria:**
- [ ] Same state transitions as `GoogleAccountFlow` (connect success/failure, refresh,
      disconnect) covered by tests
- [ ] `./gradlew :composeApp:jvmTest` green

**Files:** `MicrosoftAccountFlow.kt` (new), matching test file

---

#### MS-7 — `MicrosoftCalendarIdResolver` + `StudyPreferences` fields

**What:** Straight port of `CalendarIdResolver.kt`, retargeted at
`MicrosoftCalendarSyncService`. Adds `microsoftCalendarId`/`microsoftCalendarName` fields
to `StudyPreferences` following the existing `googleCalendarId`/`googleCalendarName`
pattern (ADR-004 explicitly rejects generalizing into a provider map at two providers).

**Acceptance criteria:**
- [ ] Find-or-create "CEF Academic" calendar logic matches `CalendarIdResolver`'s
      stale-ID self-healing behavior (re-resolve by name if the saved ID no longer exists)
- [ ] `./gradlew :composeApp:jvmTest` green

**Files:** `MicrosoftCalendarIdResolver.kt` (new), `StudyPreferences.kt`, matching test file

---

#### MS-8 — `MicrosoftRemoteCalendarRepository`

**What:** Facade implementing `RemoteCalendarRepository` (`CalendarInterfaces.kt:104-114`),
mirroring `GoogleRemoteCalendarRepository.kt`'s structure: delegates to
`MicrosoftCalendarSyncService` (MS-2) and `MicrosoftCalendarIdResolver` (MS-7), reuses
`EventQueryService`/`EventRangeFilter` unchanged (already provider-agnostic). **Does not**
carry forward `GoogleRemoteCalendarRepository`'s unused `EventConflictDetector` injection
(`GoogleRemoteCalendarRepository.kt:12`, confirmed dead — don't repeat it in new code; fixing
the existing Google instance is out of scope here).

**Acceptance criteria:**
- [ ] Implements all of `RemoteCalendarRepository`/`StudentCalendarRepository`'s methods
- [ ] `CalendarSyncTest`'s four mutation scenarios pass against the Microsoft
      implementation (new parallel test class, not a modification of the existing one)
- [ ] `./gradlew :composeApp:jvmTest` green

**Files:** `MicrosoftRemoteCalendarRepository.kt` (new), matching test file

---

#### MS-9 — `CalendarProvider` enum + `DependencyContainer` wiring

**What:** New `CalendarProvider` enum (`NONE`/`GOOGLE`/`MICROSOFT`), resolved once at
startup by checking `GoogleTokenRepository`/`MicrosoftTokenRepository` for a stored token
(single-install app — no per-request resolution needed, unlike Oficio's multi-tenant
resolver). `DependencyContainer.kt:65-81` constructs and injects whichever
`RemoteCalendarRepository` matches into `CalendarAgent`. `CalendarSyncManager`'s
`isGoogleLinked: Boolean` (`CalendarSyncManager.kt:14`) retypes to
`connectedProvider: CalendarProvider?` — mechanical rename/retype at its one call site.

**Acceptance criteria:**
- [ ] `CalendarProvider` resolved correctly for: Google-only connected, Microsoft-only
      connected, neither connected
- [ ] `CalendarSyncManager` and all call sites compile against the retyped parameter; no
      behavior change to the Google-only path (existing tests pass unmodified)
- [ ] `./gradlew :composeApp:jvmTest` green

**Files:** `CalendarProvider.kt` (new), `DependencyContainer.kt`, `CalendarSyncManager.kt`,
matching tests

---

#### MS-10 — Settings UI: provider picker + `MicrosoftCalendarPanel` / `MicrosoftCalendarSelector`

**What:** Per ADR-004 §5, "not connected" becomes a state owned by a new parent-level picker,
not by either provider panel — avoids showing two independent connect buttons simultaneously,
which would visually imply both providers could be connected at once (they can't; see MS-9).

- `connectedProvider == null`: one "Calendar & Drive" card shows both "Connect Google Account"
  and "Connect Outlook Account" buttons, stacked. Neither panel renders.
- `connectedProvider == GOOGLE`: only `GoogleCalendarPanel` renders (selector + disconnect,
  unchanged behavior) — no Outlook button visible anywhere.
- `connectedProvider == MICROSOFT`: only `MicrosoftCalendarPanel` renders (mirrored shape) — no
  Google button visible anywhere.

Trim `GoogleCalendarPanel.kt`'s existing "not linked" connect-button branch
(`GoogleCalendarPanel.kt:92-109`) out into the new picker — a small, deliberate change to
already-shipped code, not purely additive. New `MicrosoftCalendarPanel`/
`MicrosoftCalendarSelector` mirror `GoogleCalendarPanel`/`GoogleCalendarSelector`'s *linked-state*
shape only (they no longer need their own not-connected branch either).
`CalendarDisplayName.kt` needs no change (already provider-agnostic).

**Acceptance criteria:**
- [ ] With nothing connected, both connect buttons render in one card; each starts its own
      provider's OAuth flow
- [ ] Once a provider connects, the *other* provider's connect button is gone from the screen —
      not just disabled — until disconnect returns state to `null`
- [ ] `GoogleCalendarPanel`'s trimmed not-linked branch removal doesn't regress any existing
      Google-linked-state behavior (selector, disconnect confirmation, calendar creation)
- [ ] Compose UI test covers all three `CalendarProvider` states plus the disconnect-returns-
      to-picker transition, mirroring existing `SettingsScreen`/`GoogleCalendarPanelTest` coverage

**Files:** `MicrosoftCalendarPanel.kt` (new), `MicrosoftCalendarSelector.kt` (new),
`GoogleCalendarPanel.kt` (trim not-linked branch), `SettingsScreen.kt` (new picker), matching tests

---

#### MS-11 — Disconnect teardown parity (HARD-9 for Microsoft)

**What:** HARD-9 (Phase 10) gave Google's "Disconnect Account" button a confirmation
dialog with honest messaging ("only the connection is removed, events already in CEF stay
on-device") and the explicit product decision that disconnect touches zero local event
data. Apply the same policy and the same confirmation-dialog pattern to
`MicrosoftCalendarPanel`'s disconnect button — this should not ship as a silent gap the
way Google's disconnect flow was before HARD-9.

**Acceptance criteria:**
- [ ] `MicrosoftCalendarPanel`'s disconnect button opens the same style of confirmation
      dialog as `GoogleCalendarPanel`'s (post-HARD-9)
- [ ] `MicrosoftAccountFlow.disconnect()` touches zero local event data, matching HARD-9's
      policy
- [ ] Test mirrors `GoogleCalendarPanelTest`'s three paths (open without disconnecting,
      cancel without disconnecting, confirm disconnects exactly once)

**Files:** `MicrosoftCalendarPanel.kt`, `MicrosoftAccountFlow.kt`, matching test file

---

#### MS-12 — Integration test + regression check

**What:** New `MicrosoftCalendarSyncIntegrationTest` (per the project's `IntegrationTest`
naming convention — real OAuth/Graph calls, excluded from default `jvmTest` runs) covering
a full connect → sync → disconnect round trip against a real Microsoft account. Re-runs
existing Google-path tests as an explicit regression check that MS-1..MS-9's changes
(`StudyPreferences` growth, `CalendarSyncManager` retype, `DependencyContainer` wiring)
didn't change Google behavior.

**Acceptance criteria:**
- [ ] New `MicrosoftCalendarSyncIntegrationTest` green against a real Microsoft
      Outlook.com or M365 account, following the `-PrunAITests`-equivalent opt-in gating
      already used for `IntegrationTest`-suffixed classes
- [ ] Existing Google-path `CalendarSyncTest` and `GoogleOAuthIntegrationTest` still green,
      unmodified
- [ ] `./gradlew :composeApp:jvmTest` (default, no real-API tests) and the full
      integration run both green

**Files:** `MicrosoftCalendarSyncIntegrationTest.kt` (new)

### Build order

```
MS-1 (Azure AD + secrets)         standalone — start in parallel with MS-2, real lead time
MS-2 (Graph REST client)          standalone — MockEngine-testable, no auth dependency
MS-3 (auth: expect + JVM + stubs) needs MS-1 for live testing; compiles standalone
    ├── MS-4 (auth: Android)      replaces MS-3's Android stub
    └── MS-5 (auth: iOS)          replaces MS-3's iOS stub
MS-6 (MicrosoftAccountFlow)       needs MS-2 + MS-3..MS-5 (all actuals must exist)
MS-7 (CalendarIdResolver)         needs MS-2
MS-8 (RemoteCalendarRepository)   needs MS-2 + MS-7
MS-9 (provider enum + wiring)     needs MS-6 + MS-8
MS-10 (Settings UI)               needs MS-9
MS-11 (disconnect parity)         needs MS-10
MS-12 (integration test)          needs MS-1 (real secrets) + MS-9 + MS-10, last
```

### Ruled out (this phase)

Per ADR-004's Out of Scope — listed explicitly so this phase stays honest about what
was considered and deliberately excluded, not silently dropped:

- **Simultaneous dual-provider sync for one student** — one connected provider at a time;
  switching is disconnect-then-reconnect, not built.
- **Generalizing `RemoteCalendarRepository`'s auth/id-resolver dependencies into shared
  interfaces** — deferred until a third provider makes the seam obvious; two mirrored
  implementations is accepted duplication for now.
- **iCloud/CalDAV** — no modern OAuth story, not requested.
- **Token encryption at rest** — matches the existing (unencrypted, `Settings`-backed)
  Google posture; not raised or lowered by this phase for either provider.

---

## Phase 13 — Eval Baseline/Delta + Cross-Term Memory ✅ DONE 2026-07-10 — EB-1/EB-2/EB-3/XM-1..5 implemented, tested, fully wired end-to-end, and SonarQube Quality Gate passing

**Design doc:** [ADR 0004](docs/adr/0004-eval-baseline-delta-and-cross-term-memory.md). Read that
first — this section is the task breakdown, not the rationale.

### Clarify Protocol answers

**EB (eval baseline/delta):**
1. *Verification* — Automated: each of the 3 eval-shaped Kotest classes has a unit-testable
   metric-computation path independent of the assert step; `evals/baseline_*.json` round-trips
   through a plain serializer test with no live Gemini call.
2. *Edge cases* — (a) baseline file missing/malformed on a fresh checkout → delta step must skip
   with a warning, not fail the job; (b) live metric equals baseline exactly → zero delta, still
   reported (not suppressed) so the summary always shows current state.
3. *Quality Gate impact* — touches `SyllabusEvaluationIntegrationTest.kt`,
   `ContributorPdfIntegrationTest.kt`, `StlccIntegrationTest.kt` (adding a metric-capture branch,
   not new complexity in the assertion logic itself) and adds one new small class
   (`EvalBaselineComparator` or similar) kept under the 15-per-file / 5-per-method complexity
   budget by design — it's a pure diff, no branching beyond the tolerance-band check.
4. *Dependencies* — none; independent of Phase 13's XM- tasks and everything else in-flight.

**XM (cross-term memory):**
1. *Verification* — Automated: `TermProfileAggregator` is pure aggregation code, fully unit-testable
   against fixture `Event` lists with no live Gemini call. The min-2-terms floor and course-identity
   (category, not code) guardrails are each asserted by a dedicated test using the real
   `contributions/tx/ut_austin/2025-2026/{fall,spring}` two-term fixture.
2. *Edge cases* — (a) student with exactly 1 completed term → no profile block injected, verified by
   a test; (b) a course code that recurs across terms with an unrelated subject (the real `BIO337`
   case) → must not be merged into one course's aggregate; (c) concurrent term-boundary detection
   racing a live `compactHistory` call → both are serialized per-student the same way
   `compactionMutex` already serializes `compactHistory`.
3. *Quality Gate impact* — one new SQLDelight table (schema-only, no complexity budget), one new
   class `TermProfileAggregator` (new, budgeted under 15/file, 5/method), `ChatBudgetAllocator` gets
   one new parameter/field (small, additive), `ContextAgent` gets one new call site in
   `queryAllSources` (additive, not a new branch in `compactHistory`).
4. *Dependencies* — needs the real two-term fixture (done: `contributions/tx/ut_austin/2025-2026/spring/`,
   landed ahead of this phase) to write XM-2/XM-5's tests against real data rather than synthetic.

### Tasks

#### EB-1 — Metric-capture + baseline-record flag in the 3 eval classes

**What:** Add a `-PrecordEvalBaseline=true` Gradle system property, read via
`System.getProperty`/`System.getenv` the same way `-PrunAITests` already is. Each eval class
computes its metrics (recall/date-accuracy for `SyllabusEvaluationIntegrationTest`, per-file depth
scores for `ContributorPdfIntegrationTest`, per-doc dedup/stability for `StlccIntegrationTest`) into
a small `@Serializable` data class, and when the flag is set, writes it to
`evals/baseline_<name>.json` via `kotlinx.serialization.json.Json`. The existing threshold assertion
still always runs — this is additive, not a replacement.

**Acceptance criteria:**
- [x] Metric data classes have round-trip serder tests (per this repo's serder-tests-for-DTO-conversion
      convention) with no live API call — `EvalBaselineTest.kt`
- [x] Running with the flag unset behaves byte-for-byte as today — metric capture writes
      `evals/current_*.json` unconditionally (cheap, already-computed numbers) but never touches
      the threshold assertions; only `evals/baseline_*.json` is gated by the flag
- [x] `./gradlew :composeApp:jvmTest` green (flag-off path only; flag-on path requires
      `-PrunAITests=true` and is exercised manually / in the nightly workflow) — full suite green,
      2625 tests, 0 failures

**Files:** `SyllabusEvaluationIntegrationTest.kt`, `ContributorPdfIntegrationTest.kt`,
`StlccIntegrationTest.kt`, `EvalBaseline.kt` (new, holds the metric data classes + recorder)

---

#### EB-2 — Record the initial baselines — DONE (commit `9271731`, 2026-07-10)

**What:** Run each eval class once with `-PrecordEvalBaseline=true -PrunAITests=true` against live
Gemini, review the output numbers by hand, and commit the resulting
`evals/baseline_syllabus.json`, `evals/baseline_contributor_pdf.json`, `evals/baseline_stlcc.json`
in a standalone reviewed PR — never auto-generated by CI.

**Acceptance criteria:**
- [x] All three baseline files committed, human-reviewed — 100% recall/date-accuracy on the
      syllabus fixtures, 21/22 contributor PDFs passed depth assertions, 0 duplicates across all
      3 STLCC docs
- [x] PR description states the exact commit/model the baseline was recorded against — was
      unchecked as of `9271731` (commit message confirmed a live-Gemini run but not the exact
      model string). Fixed in a follow-up: all 3 eval classes now read the negotiated model back
      from the shared `preferred_gemini_model` DB cache after extraction — scoped to the HEAVY
      tier, since `generateEventsFromPrompt`/`analyzeDocument`/`extractTextFromDocument` are the
      only tier these eval classes ever exercise (see `GeminiAIService.TaskTier` call sites) —
      and record it as `modelUsed` in `evals/current_*.json` (`SyllabusEvalMetrics`,
      `ContributorPdfEvalMetrics`, per-doc on `StlccDocMetric`). The 3 files already committed in
      `9271731` still lack the field (nullable/defaulted, decodes fine) — the next
      `-PrecordEvalBaseline=true` run will populate it.

**Files:** `evals/baseline_syllabus.json`, `evals/baseline_contributor_pdf.json`,
`evals/baseline_stlcc.json` (new); `EvalBaseline.kt`, `SyllabusEvaluationIntegrationTest.kt`,
`ContributorPdfIntegrationTest.kt`, `StlccIntegrationTest.kt` (model-capture follow-up)

---

#### EB-3 — Delta reporting in the nightly workflow

**What:** After the existing test run in `eval-corpus.yml`, a small step (or Gradle task,
`EvalBaselineComparator`) reads the freshly-computed metrics alongside the checked-in baseline and
writes a delta table to `$GITHUB_STEP_SUMMARY`, using a tolerance band (not exact-match) per metric.
Does not fail the job on drift — that's still `maxAllowedFailures`'s job; this is visibility only.

**Acceptance criteria:**
- [x] A deliberately-regressed local run shows a non-zero delta in the summary — `EvalBaselineComparatorTest.kt`
- [x] A no-op local run shows zero delta — `EvalBaselineComparatorTest.kt`
- [x] Missing/malformed baseline file → step warns, does not fail the job — `EvalBaselineComparatorTest.kt`;
      workflow step also runs `if: always()`
- [x] A model change between baseline and current (see EB-2's `modelUsed` follow-up) is surfaced
      as its own warning line, separate from the metric delta table — a quality drop caused by
      Google swapping the negotiated model shouldn't be misread as a code regression, or vice
      versa — `EvalBaselineComparatorTest.kt`

**Files:** `.github/workflows/eval-corpus.yml`, `EvalBaselineComparator.kt` (new) + test. Note: the
delta step can't itself be exercised until EB-2 has run at least once (no baseline files exist yet)
— it correctly no-ops with a skip note in that state, verified by test rather than a live CI run.

---

#### XM-1 — `student_term_profile` table in the shared schema

**What:** Add a table to `AppDatabase.sq` (`commonMain`) — one row per student per completed term:
course load, `AcademicCategory` distribution, deadline cadence by weekday, study-plan constraints
exercised. Schema-only change; `IF NOT EXISTS`-guarded per this repo's existing migration
convention (`TenantDatabaseFactory`'s `AppDatabase.Schema.create` retry-on-exists pattern).

**Acceptance criteria:**
- [x] `DriverFactoryTest` and any `TenantDatabaseFactory` test still green — new table doesn't break
      fresh-create or existing-DB-open paths
- [x] `./gradlew :composeApp:jvmTest` green — full suite, 2625 tests, 0 failures

**Files:** `composeApp/src/commonMain/sqldelight/com/borinquenterrier/cef/db/AppDatabase.sq`.
No `studentId` column — this DB is already one-per-student (ADR 0002), so `termStart` alone is
the key within a given student's own database.

---

#### XM-2 — `TermProfileAggregator`

**What:** Pure aggregation class: takes a student's `Event` history for a completed term, produces
the structured record XM-1's table stores. No LLM call. **Implementation refinement over the
original plan:** course identity within a single term's aggregation uses `Event.sourceId` (the
source document an event was generated from — this app's existing notion of "a course") rather
than parsing course codes/categories from titles. This sidesteps the `BIO337` cross-term-identity
problem entirely for `courseLoad`, since `aggregate()` only ever sees one term's events at a time
— there is no code path that compares course identity across terms, so nothing can conflate two
different terms' same-numbered-different-subject courses (see `TermProfileAggregator.kt`'s
docstring and its dedicated `BIO337`-style test).

**Acceptance criteria:**
- [x] Unit tests using hand-authored fixtures modeled on the real
      `contributions/tx/ut_austin/2025-2026/{fall,spring}` two-term corpus (not the live
      extraction pipeline — that's what `ContributorPdfIntegrationTest` already covers, at the
      cost of a real Gemini call; this aggregator only needs representative `Event` shapes),
      asserting courseLoad/category/cadence aggregation logic — `TermProfileAggregatorTest.kt`
- [x] A dedicated `BIO337`-style test asserting fall and spring are aggregated independently and
      never merged into one course's count despite the shared course code
- [x] Unit test asserting an empty term produces no profile (the min-2-terms floor itself is
      enforced at the read side, XM-4 — this is the aggregator's own "nothing to summarize" contract)

**Files:** `TermProfileAggregator.kt` (new) + `TermProfileAggregatorTest.kt`, `TermProfileRepository.kt`
(new, persistence mapping to/from XM-1's table)

---

#### XM-3 — Term-boundary trigger

**What:** Apply the existing pure `SemesterResolver.getSemesterRange(date)` to a student's event
*max-date* (not `today` — that's `WarningClassifier.activeSemesterFrom`'s wall-clock usage, which
suits its own UI-warning purpose but is the wrong primitive for a data-driven batch trigger; see
ADR 0004's Alternatives Considered) to detect when the newest stored event has moved into a later
semester than the student's last-processed `student_term_profile` row. That crossing is the trigger
for running `TermProfileAggregator` and persisting its output via
`TenantDatabaseFactory`/`DriverFactory`'s shared schema. No new scheduler.

**Acceptance criteria:**
- [x] Test: crossing a detected term boundary triggers exactly one aggregation write, not one per
      event — `TermBoundaryTriggerTest.kt`
- [x] Test: re-processing the same term boundary is idempotent (no duplicate rows) — `TermBoundaryTriggerTest.kt`

- [x] Wired to a real invocation site (2026-07-10): `CalendarAgent.synchronize()` — the app's one
      actual "sync" concept, reached from `AgentHarness`'s poll loop, `EventAgent` post-push, and
      `SourceDeleter` alike — takes an optional `termProfileRepository`, and after `selfHeal()`
      calls `processNewlyCompletedTerms(getEvents(calendarId), repository)` with the same
      never-break-the-sync try/catch pattern `selfHeal` itself already used. `DependencyContainer`
      now constructs one shared `termProfileRepository` and wires it into both `calendarAgent`
      (write side) and `contextAgent` (read side, XM-4's already-built consumer, previously
      instantiated with the default `null` and therefore dead) — the read side was equally
      unwired and needed the same fix to make the feature actually do anything end-to-end.
      Tests: `CalendarAgentTest.kt` — records the completed term on a real in-memory
      `TermProfileRepository`, confirms `null` (the old default) still no-ops exactly as before,
      and confirms a term-boundary failure doesn't break sync (mirrors self-heal's own failure
      test).

**Files:** `TermBoundaryTrigger.kt` (new — `TermBoundaryTrigger.detectNewlyCompletedTerms` +
`processNewlyCompletedTerms`), using `SemesterResolver` directly (not `WarningClassifier`).
`CalendarAgent.kt`, `DependencyContainer.kt`, `CalendarAgentTest.kt` (wiring, 2026-07-10).

---

#### XM-4 — `ChatBudgetAllocator` + `ContextAgent` read path

**What:** Add a small fixed `profileTokens` budget line to `ChatBudgetAllocator.historyBudget`.
`ContextAgent.queryAllSources` reads the student's `student_term_profile` rows (if the min-2-terms
floor is met) and folds a summary into the prompt the same way the existing rolling summary is
folded in — not RAG-retrieved. **Implementation refinement over the original plan:** it's recomputed
every turn (like the existing rolling summary already is) rather than cached/injected only on a
conversation's first turn — the profile is small and fixed-size, so the per-turn cost is
negligible, and detecting "is this a brand-new conversation" would have needed extra session-state
tracking for no real benefit.

**Acceptance criteria:**
- [x] Test: profile block present in the prompt when ≥2 terms exist, absent below that floor
      (existing behavior unchanged), and absent when no repository is wired at all — `ContextAgentTest.kt`
- [x] Test: `ChatBudgetAllocator.historyBudget` correctly subtracts `profileTokens` (defaults to 0,
      unchanged for existing callers) and the existing "never negative" test still covers the
      pathological-budget case with the new line item included — `ChatBudgetAllocatorTest.kt`
- [x] `ContextAgentTest` (existing) still green — all 13 tests pass, including 3 new ones

**Files:** `ChatBudgetAllocator.kt`, `ContextAgent.kt`, `ChatBuilder.kt`/`AiPrompts.kt` (new
`studentProfile` prompt parameter) + tests

---

#### XM-5 — Key-source guardrail test

**What:** Explicit regression test asserting the aggregation/distillation path never references
`CEF_GEMINI_API_KEY` / any CI-only key — only the per-tenant/per-device `AIService` resolution
already used by `generateChatResponse` elsewhere in `ContextAgent`.

**Acceptance criteria:**
- [x] Test fails if `TermProfileAggregator`, `TermBoundaryTrigger`, or `TermProfileRepository` is
      changed to reference `CEF_GEMINI_API_KEY` or `AIService` at all — a structural source-text
      scan, since none of these files make any LLM call today (ADR 0004: pure aggregation only) —
      `TermProfileKeySourceGuardrailTest.kt`

**Files:** `TermProfileKeySourceGuardrailTest.kt` (new)

### Build order

```
EB-1 (metric capture + flag)      standalone
EB-2 (record baselines)           needs EB-1
EB-3 (CI delta reporting)         needs EB-1 + EB-2

XM-1 (schema)                     standalone
XM-2 (aggregator)                 needs XM-1; testable against tx/ut_austin fixture now
XM-3 (term-boundary trigger)      needs XM-1 + XM-2
XM-4 (budget + read path)         needs XM-1 (reads the table); independent of XM-3 for testing
                                   (can seed rows directly in tests)
XM-5 (key-source guardrail)       needs XM-2 + XM-4
```

### Ruled out (this phase)

- **LLM-based qualitative distillation** — deferred; XM-2 is pure aggregation only. An LLM
  distillation pass (the closer analogue to the workshop's "Dreaming") is explicitly future work,
  gated behind the same key-source guardrail (XM-5) whenever it lands.
- **Auto-committed CI baseline updates** — rejected in ADR 0004; baseline recording (EB-2) stays a
  manual, reviewed action.
- **A separate long-running memory-agent process** — rejected in ADR 0004; this phase's XM- tasks
  are a batch trigger (XM-3) + read path (XM-4), not a new agent architecture.

---

## Phase 14 — Accessibility Conformance (WCAG 2.1 AA + VPAT) 🔵 IN PROGRESS — AC-1, AC-2 done

See [ADR 0011](docs/adr/0011-accessibility-conformance-target-and-vpat.md). ADR 0009 fixed real
defects (keyboard operability, ARIA, focus management) and added static linting, but the project
cannot currently back a "WCAG conformant" or "ADA compliant" claim: no explicit target level, no
automated a11y regression tests beyond lint, no contrast audit, no assistive-tech pass, no VPAT.
This phase closes that gap for real, in the order a truthful VPAT actually requires (you can't
document conformance you haven't tested).

### Tasks

#### AC-1 — Adopt WCAG 2.1 AA as the documented conformance target

**What:** Docs-only — this ADR (0011) is the artifact. No code change.

**Acceptance criteria:**
- [x] ADR 0011 accepted and committed

**Files:** `docs/adr/0011-accessibility-conformance-target-and-vpat.md`

---

#### AC-2 — Frontend test infrastructure + automated axe coverage ✅ DONE (2026-07-24)

**What:** The web client has zero frontend tests today (ADR 0009 explicitly deferred this). Add
Vitest + React Testing Library, then `vitest-axe` (or `jest-axe` under Vitest's jest-compat layer)
run against the Calendar, Sources, Studio Panel, and Settings views plus both modals (task
decomposition, create-calendar). This catches runtime issues static `jsx-a11y` linting structurally
can't — dynamic ARIA state, color contrast, live regions.

Implemented as: the web client has no separable view components — one large `App.tsx` renders
"Calendar"/"Sources"/"Studio Panel"/"Settings" via a single `activeTab` string, and both modals as
inline JSX in the same file. Tests render the real `<App />` (fetch stubbed via a shared
`renderApp()` helper in `src/test/testUtils.tsx`) and drive tab switches / button clicks with
`@testing-library/user-event`, rather than importing views in isolation. `vitest-axe`'s own
`toHaveNoViolations` matcher type (v0.1.0) doesn't match Vitest 4's actual `Assertion` interface, so
assertions use a local `expectNoAxeViolations()` helper against the well-typed `results.violations`
array instead of fighting the stale library's types. `@testing-library/react`'s auto-cleanup only
self-registers under `globals: true`; since this config uses explicit imports instead, `setup.ts`
wires `afterEach(cleanup)` manually — its absence looked like real accessibility violations
(duplicate-id-style symptoms) until traced back to undismounted `<App/>` instances piling up in the
same jsdom document across tests in a file.

**A real, live violation surfaced and was fixed in this same pass**: `heading-order` (WCAG 1.3.1) —
the Calendar tab's stat cards and the Settings tab both jumped from `<h1>` straight to `<h3>`,
skipping `<h2>`; the Chronological Agenda's per-date group headers were `<h4>` directly under an
`<h2>`. Exactly the kind of runtime/structural issue static `jsx-a11y` linting can't catch (it
checks JSX shape, not document-wide heading hierarchy) — fixed by promoting the stat-card and
Settings-section headings to `<h2>` and the date-group headers to `<h3>` (visual size preserved via
inline `fontSize` where the tag change would otherwise have changed it).

**Acceptance criteria:**
- [x] `web/package.json` has a working `npm test` (Vitest) wired into CI alongside the existing
      `npm run lint` job — `.github/workflows/pr-check.yml`'s `build-web` job
- [x] Test: axe reports zero violations against each of the four main views in their default
      rendered state — `App.test.tsx` (after fixing the real `heading-order` violations found)
- [x] Test: axe reports zero violations against both modals in their open state (focus-trapped,
      per ADR 0009) — `App.test.tsx`
- [x] Regression test: `src/test/axeGuardrailSanityCheck.test.ts` proves the guardrail isn't
      silently inert — a synthetic unlabeled-icon-button snippet (the literal ADR-0009 defect
      shape) is asserted to fail axe, and the same snippet with an `aria-label` is asserted to pass

**Files:** `web/package.json`, `web/vitest.config.ts`, `web/src/test/setup.ts`,
`web/src/test/testUtils.tsx`, `web/src/App.test.tsx`, `web/src/test/axeGuardrailSanityCheck.test.ts`,
`web/src/App.tsx` (heading-level fixes), `.github/workflows/pr-check.yml`

---

#### AC-3 — Color contrast audit (dark theme) ✅ DONE (2026-07-24)

**What:** Audit every text/UI-component color pair against WCAG 1.4.3 (4.5:1 normal text, 3:1
large text/UI components), with particular attention to `var(--text-secondary)` and any other muted
tone used over the dark background — the common failure mode for a dark theme that looks fine to a
sighted engineer at full monitor brightness but fails the ratio. AC-2's axe integration catches most
of this automatically; anything it can't (e.g. text over a gradient/image background, like the
outro card) needs a manual check.

**A real, load-bearing gap in AC-2's own premise, found while working this task**: `color-contrast`
cannot run meaningfully in jsdom at all — not "disabled," but functionally broken. axe-core's rule
depends on real bounding-box geometry to decide if text is visible, and jsdom has **no layout engine
whatsoever**: `getBoundingClientRect()`/`offsetWidth`/`offsetHeight` return `0` for every element
regardless of CSS (verified directly: a 200×50px styled `<div>` reports a `0,0,0,0` rect). Every
element in jsdom looks invisible to axe, so `color-contrast` can never produce a real pass or
violation there — it either silently reports "incomplete" or throws internally
(`Cannot read properties of null (reading 'canvas')`, from an icon-ligature check that needs a real
`<canvas>` 2D context jsdom doesn't provide). Installing the `canvas` npm package (jsdom's optional
native canvas backend) silences that specific error but **does not fix the underlying problem** —
results stayed empty (0 violations, 0 incomplete, 0 passes) because the zero-size bounding boxes
still make every element look invisible. Confirmed, then reverted (`canvas` isn't a real fix, just
dead weight — a native-binary devDependency with no payoff). **This means AC-2's third acceptance
criterion as originally written is not achievable**: the Vitest suite cannot auto-catch future
contrast regressions; that requires a real browser-based test runner (Playwright/Cypress component
tests), which is new infrastructure out of scope for this pass. `color-contrast` is still on by
default in AC-2's suite (never explicitly disabled) — it's just a no-op there, always.

**This pass's actual audit** was done live: `axe-core`'s browser bundle
(`node_modules/axe-core/axe.min.js`, already a vetted local devDependency — not fetched from a CDN)
injected into the real running app via Vite's `/@fs/` dev-server path, logged in through the mock
LTI platform, `axe.run(document, {runOnly:['color-contrast']})` run against each of the 4 views.
This found one real, live violation axe caught automatically (`.btn-primary`/`.chat-msg.user`: white
text on `--color-primary` `#a855f7` = 3.95:1, below the 4.5:1 required for this 14px/normal-weight
text) and surfaced several backdrop-filter/semi-transparent-background pairs as "incomplete" (axe
correctly can't reliably composite `backdrop-filter: blur()` layers even in a real browser) that
were then hand-computed with the WCAG relative-luminance formula:

| Text token | Background | Ratio | Threshold | Result |
|---|---|---|---|---|
| `--text-primary` (#f3f4f6) | bg-app / composited bg-card / bg-card-hover / bg-input / sidebar | 15.1–17.5 | 4.5:1 | OK |
| `--text-secondary` (#9ca3af) | same set | 6.5–7.6 | 4.5:1 | OK |
| `--text-muted` (old: #6b7280) | same set | 3.4–4.0 | 4.5:1 | **FAIL** (all real usages are ≤14px normal-weight, not "large text") |
| `--text-muted` (new: #838ba0) | same set | 4.9–5.7 | 4.5:1 | OK — fixed |
| `--color-primary` (#a855f7) as text/icon color | bg-app / composited bg-card / sidebar | 4.6–4.9 | 4.5:1 | OK |
| `--color-primary` (#a855f7) as text/icon color | composited bg-card-hover / bg-input | 4.2–4.3 | 4.5:1 | FAIL, but not used as text color on these backgrounds today — not fixed, flagged for future caution |
| `--color-success` / `--color-warning` / `--color-danger` as text color | full background set | 4.4–9.0 | 4.5:1 | OK |
| white text on `--color-primary` solid bg (old, `.btn-primary`/`.chat-msg.user`) | — | 3.95 | 4.5:1 | **FAIL** |
| white text on `--color-primary-solid` (new: #9333ea) | — | 5.38 | 4.5:1 | OK — fixed |
| white text on `.btn-primary:hover` (new: #7e22ce, was #9333ea) | — | 6.98 | 4.5:1 | OK |
| event-chip / badge tinted colors (`#60a5fa`, `#f87171`, `#c084fc`, `#34d399`, `#fbbf24`, etc.) on their own 15-25%-alpha tinted backgrounds | composited over bg-card | 5.4–8.4 | 4.5:1 | OK |
| Modal content (`--text-primary/secondary`, `--color-warning/danger/primary`, `.btn-secondary`) | modal's solid `#131520` background | 4.6–16.5 | 4.5:1/3.0:1 | OK — both modals reuse already-audited tokens, no new pairs |

**Fixes applied** (`web/src/index.css`): `--text-muted` lightened `#6b7280` → `#838ba0`; new
`--color-primary-solid: #9333ea` token added for solid-button-background-under-white-text use
(`.btn-primary`, `.chat-msg.user`), keeping `--color-primary` itself unchanged (#a855f7) since it's
still correctly light for its many other uses as accent text/icon color; `.btn-primary:hover`
darkened from `#9333ea` to `#7e22ce` to preserve the "gets darker on hover" pattern one step further.
Verified live post-fix: the axe-caught `.btn-primary` violation cleared to 0; all 4 views re-checked
clean.

**Acceptance criteria:**
- [x] Every CSS custom property used for text-on-background pairs documented with its computed
      contrast ratio — table above
- [x] Any pair below the WCAG 1.4.3 threshold for its role fixed — `--text-muted`,
      `--color-primary-solid` (new), `.btn-primary:hover`
- [x] ~~AC-2's axe suite includes `color-contrast` as an enabled rule... so future regressions are
      caught automatically~~ — **not achievable in AC-2's jsdom suite** (see the jsdom finding
      above; the rule is enabled there, never disabled, it just can't produce results). Closed for
      real instead with a second, browser-backed layer: **Playwright + `@axe-core/playwright`**
      (`web/playwright.config.ts`, `web/e2e/`), added the same day after discussing the tradeoff
      with the user (a slower real-browser suite alongside, not instead of, the fast jsdom one).
      `page.route()` mocks every `/api/*` call before it reaches Vite's dev-server proxy, so no real
      backend is needed to run it. Verified the guardrail isn't inert the same way AC-2's own
      sanity-check test proves itself: reverted `--color-primary-solid` back to the real
      `.btn-primary` bug this task found, confirmed all 6 `test:e2e` specs failed with the exact
      3.95:1 contrast data, then restored the fix and confirmed green again. Wired into CI
      (`.github/workflows/pr-check.yml`) after the existing Vitest `Test` step.

**Files:** `web/src/index.css` (token/value fixes), `web/playwright.config.ts` (new),
`web/e2e/mockApi.ts` (new), `web/e2e/accessibility.spec.ts` (new, mirrors `App.test.tsx`'s 4
views + 2 modals), `.github/workflows/pr-check.yml`

---

#### AC-4 — Manual assistive-technology pass 🟡 PARTIALLY DONE (2026-07-24) — keyboard pass done, screen-reader passes open

**What:** A real human pass automated tooling can't replace: full keyboard-only walkthrough of
every primary flow (login via LTI launch, upload a source, view/sync the calendar, decompose a
task, chat in the Studio Panel, edit settings, staff console if applicable), plus a screen-reader
smoke test with VoiceOver (macOS — two of four platforms are Apple) and NVDA (Windows — the most
common combination in US higher-ed IT). Findings get logged even if they don't block this phase's
completion, so the VPAT (AC-6) can honestly note them.

**What actually happened**: the keyboard-only pass is a genuinely objective, mechanical check (can
every control be reached via Tab, does focus order make sense, do traps behave), so it was done
live against the real running app via keyboard-driven browser automation — see
`docs/ops/accessibility-manual-audit-findings.md` for the full writeup. It found and fixed one real
WCAG 2.1.1 failure (the Sources tab's file-upload dropzone — a `<label>` wrapping a hidden file
input — was completely unreachable by keyboard; rewritten as a real `<button>`, with a regression
test added since axe-core doesn't flag this pattern on its own).

The VoiceOver and NVDA passes are **not done** — these need a human actually listening to real
screen-reader speech output and judging whether it's clear/non-confusing, which isn't something
automated tooling (or an agent without ears) can substitute for. NVDA additionally needs a Windows
machine, unavailable in this environment. Both are open, tracked honestly rather than skipped
silently or faked — see the findings doc's "What's still open" section for the reasoning and
options going forward.

**Acceptance criteria:**
- [x] Written findings doc covering: keyboard-only pass (all flows reachable/operable, focus order
      sane, no traps outside the two intentional modal traps) — done
- [ ] Written findings doc covering: VoiceOver pass — **open, needs a human**
- [ ] Written findings doc covering: NVDA pass — **open, needs a human + a Windows machine**
- [x] Any finding that's a quick fix gets fixed before AC-6 — the dropzone keyboard-reachability bug
      was fixed in this same pass

**Files:** `docs/ops/accessibility-manual-audit-findings.md` (new), `web/src/App.tsx` (dropzone
fix), `web/src/App.test.tsx` (regression test)

---

#### AC-5 — Public in-app accessibility statement

**What:** A reachable page/section in the web client stating the conformance target (WCAG 2.1 AA),
known limitations (pulled honestly from AC-4's findings), and a contact path for reporting issues.
Standard expectation in institutional procurement, independent of the VPAT itself.

**Acceptance criteria:**
- [ ] Statement reachable from the app (e.g. Settings or a footer link), not just a repo file
- [ ] Content reflects AC-3/AC-4's actual findings, not aspirational language

**Files:** `web/src/App.tsx` (or a new `AccessibilityStatement` component/route)

---

#### AC-6 — Produce the VPAT

**What:** ITI's VPAT 2.5, WCAG Edition or the combined INT edition (covers Section 508/EN 301 549
too — worth the small extra effort given the US higher-ed audience). Filled in from AC-2/AC-3/AC-4's
actual results. Must include honest "Partially Supports" / "Does Not Support" rows where true — an
oversold VPAT is worse than none once a real disability-services reviewer starts testing.

**Acceptance criteria:**
- [ ] VPAT document committed to the repo (e.g. `docs/compliance/VPAT.md` or `.pdf`)
- [ ] Every WCAG 2.1 AA success criterion has a row backed by an actual AC-2/AC-3/AC-4 result, not
      a guess

**Files:** `docs/compliance/VPAT.md` (new)

---

#### AC-7 — Third-party accessibility audit (stretch, gated)

**What:** Deferred, not rejected (see ADR 0011's "Alternatives Considered"). Only pursue if a real
customer or procurement process asks for third-party attestation rather than a self-authored VPAT —
real cost, no point paying for it speculatively.

**Acceptance criteria:**
- [ ] Not started until a concrete external ask exists; tracked here so it isn't forgotten

**Files:** N/A yet

### Build order

```
AC-1 (adopt target)         standalone — done via this ADR
AC-2 (test infra + axe)     standalone — new frontend test infra
AC-3 (contrast audit)       needs AC-2's axe setup for the automated half; manual half standalone
AC-4 (manual AT pass)       standalone, can run in parallel with AC-2/AC-3
AC-5 (statement page)       standalone; content depends on AC-4's findings for accuracy
AC-6 (VPAT)                 needs AC-2 + AC-3 + AC-4 results — cannot be written truthfully before them
AC-7 (third-party audit)    needs AC-6; gated behind a real external ask, not scheduled
```

### Ruled out (this phase)

- **Targeting WCAG 2.2 AA instead of 2.1.** Rejected for now — 2.1 AA is the more universally
  referenced baseline in current ADA/Section 508 guidance; revisit once 2.2 sees wider adoption on
  the procurement side.
- **Self-declaring conformance without a VPAT.** Rejected — the target audience (disability-services
  / procurement offices) expects a VPAT as the standard artifact; showing up without one reads as
  not having done the work.
- **Commissioning a third-party audit now.** Deferred to AC-7, gated behind a real ask — see ADR
  0011.

---

## Phase 15 — Decouple Upload from Processing (Async Ingestion + Progress Streaming) ✅ DONE (2026-07-24)

See [ADR 0012](docs/adr/0012-decouple-upload-from-processing.md). `POST /api/sources` currently
does upload and AI processing as one blocking HTTP request (`WebSourceHandler.processFileIngestion`
awaits `SourceProcessingPipeline.processSource` in-line, temp file deleted before responding) — a
real syllabus holds the request open 20-30+ seconds with one undifferentiated spinner. This is a
web/HTTP-client-specific gap (Android/iOS/Desktop already get live phase text in-process via
`EventAgent._statusMessage`); it surfaced during the 2026-07-23 live demo rehearsal alongside the
(separately fixed) blocking-`alert()` bug. Reuses the existing `/api/agent/stream` SSE pattern
rather than inventing a new async mechanism.

AU-1..AU-5 all implemented and tested 2026-07-24, verified with a live manual smoke test (mock LTI
login → real file upload → `202` → SSE stream → toast → list refresh, confirmed via network logs)
and the full four-target build + Sonar Quality Gate (`new_coverage: 82.1 ≥ 80`,
`new_duplicated_lines_density: 0.0 ≤ 3`, `new_violations: 0`). A few real deviations from this
section's original text, found while implementing against the actual code rather than assumed —
see each task below for specifics: no separate `WRITING_CALENDAR` phase (merged into
`RESOLVING_CONFLICTS` — the two aren't observably distinguishable from `processSource`); durable
storage is a DB `BLOB` column, not a new file-storage scheme; phase reporting is `StateFlow`-based,
not the `CriticProgressListener` context-propagation pattern (needed to replay the current phase to
a late-subscribing SSE client, which a one-shot listener can't do).

### Tasks

#### AU-1 — Persist upload immediately, respond before processing starts ✅ DONE

**What:** Replace `WebSourceHandler.processFileIngestion`'s temp-dir-then-delete flow with durable
per-tenant storage, create the `SourceItem` with a pending status, and respond `202 Accepted` with
the source id as soon as the bytes are safely stored — no AI call has run yet at response time.

Implemented as: durable storage is a new `fileBytes BLOB` column on `SourceEntity` (not a new
file-storage scheme — reuses the per-tenant SQLite DB every other piece of durable state already
lives in, including its existing backup/vacuum machinery), and `SourceItem` gained `id`/`status`
fields (previously had neither — `id` was only implicit as `title` at the repo layer). Parsing +
categorization (`ingestionAgent.addUrl`/`addLocalFile`) stays synchronous — it's a quick single AI
call, not the slow multi-step chain the ADR actually measured; only `sourceProcessingPipeline
.processSource` moves to the background (AU-2).

**Acceptance criteria:**
- [x] Test: `POST /api/sources` returns `202` (not `200`) — `testPostSourceUrl`/`testPostSourceFile`
      in `WebIngestionIntegrationTest.kt`; a dedicated `testPostSourceFileRespondsBeforePipelineCompletes`
      proves the response lands while a gated fake pipeline call is still pending
- [x] Test: the uploaded file survives past the request — `SourceRepositoryTest`'s `saveSource`
      coverage plus the new durable-bytes path in `SqlDelightSourceRepository`

**Files:** `server/src/main/kotlin/com/borinquenterrier/cef/WebSourceHandler.kt`,
`composeApp/.../SourceItem.kt`, `SourceRepository.kt`, `SqlDelightSourceRepository.kt`,
`IngestionAgent.kt`, `db/AppDatabase.sq`, `db/DriverFactory.kt` (migration)

---

#### AU-2 — Background digestion job ✅ DONE

**What:** Launch `sourceProcessingPipeline.processSource(sourceItem)` on an application-scoped
`CoroutineScope`, decoupled from the request that returned in AU-1. Document the failure mode: an
in-flight job is lost if the server restarts (accepted for now — single non-clustered container,
see ADR 0012's "Alternatives Considered"); the UI must treat a vanished job as a failure requiring
re-upload, not an indefinite wait.

Implemented as: `DependencyContainer` already had an application-scoped `globalScope` (used
elsewhere, e.g. `sourceLoader`) — no new scope needed, just one new public
`launchInBackground(block)` wrapping it (kept `private` otherwise, matching the class's existing
encapsulation style). Containers are cached per-`studentId`, confirmed the scope survives past the
launching request.

**Acceptance criteria:**
- [x] Test: the HTTP response from AU-1 does not block on `processSource` completing (same
      `testPostSourceFileRespondsBeforePipelineCompletes` test as AU-1)
- [x] Test: a source's status eventually reaches `DONE` or `FAILED` — `SourceProcessingPipelineTest`

**Files:** `composeApp/.../DependencyContainer.kt`, `server/.../WebSourceHandler.kt`

---

#### AU-3 — Phase markers in the pipeline ✅ DONE

**What:** Add discrete phase events to `SourceProcessingPipeline.processSource`, alongside the
`eventAgent.updateStatus(...)` calls already there.

Implemented as: no separate `WRITING_CALENDAR` phase — it and `RESOLVING_CONFLICTS` both happen
inside one `eventAgent.pushToCalendar()` call with no observable boundary between them, so
`RESOLVING_CONFLICTS` covers the whole step (fabricating a phase transition that isn't real would
undersell what the status actually reflects). Reporting mechanism is a `StateFlow`-based registry
(`SourceRepository.statusFlow`) rather than the `CriticProgressListener` context-propagation pattern
that ADR 0012 assumed — a one-shot listener can't replay the current phase to an SSE client that
connects mid-digestion (an AU-4 requirement), a `StateFlow` does for free.

A real gap surfaced during manual verification and was closed in this same pass: `analyzeSource`/
`extractDeliverables` catch their own AI-call exceptions internally and never throw (a pre-existing,
deliberate contract elsewhere in the app), so the original "existing catch block already rethrows"
assumption was wrong for the common case (Gemini errors) — only `pushToCalendar` throwing reaches
the pipeline's own `catch`. Per product decision: a chunk's AI call failing is not itself a pipeline
failure (still reaches `DONE` if every chunk was attempted, even if nothing useful was found), but
it must be traceable regardless of what the UI shows. Both `analyzeSource` and `extractDeliverables`
now return `Boolean` (success/failure) instead of `Unit`; `SourceProcessingPipeline` logs an explicit
`"Chunk failure..."` line when either reports failure, on top of the OTEL error-span recording that
already happened via `AppTracer` (verified in `Tracer.kt`/`HttpOtelTracer.kt` — spans already record
the exception and export as errored before it's swallowed for UI purposes).

**Acceptance criteria:**
- [x] Test: each phase fires once, in order, for a successful run — `SourceProcessingPipelineTest`
- [x] Test: a mid-pipeline exception fires `FAILED` — same file; plus a new case for the
      swallowed-failure path (`DONE` + a logged chunk-failure line, not `FAILED`)
- [x] Android/iOS/Desktop's `_statusMessage` StateFlow is untouched — `eventAgent.updateStatus` calls
      are unchanged; the new phase reporting is additive, alongside them, not a replacement

**Files:** `composeApp/.../SourceProcessingPipeline.kt`, `ContextAgent.kt` (`analyzeSource` → `Boolean`),
`EventAgent.kt` (`runAgentAction`/`extractDeliverables` → `Boolean`), `SourceRepository.kt`/
`SqlDelightSourceRepository.kt` (`statusFlow` registry)

---

#### AU-4 — SSE streaming endpoint ✅ DONE

**What:** `GET /api/sources/{id}/stream`, built the same way as the existing `/api/agent/stream`
(`respondBytesWriter` + `ContentType.Text.EventStream` + `emit(type, dataJson)`), streaming AU-3's
phase transitions for the given source id.

**Acceptance criteria:**
- [x] Test: connecting mid-digestion receives the current phase immediately, not just future
      transitions — `SourceStreamTest.testStreamShowsCurrentPhaseImmediatelyThenClosesOnDone`
- [x] Test: stream closes cleanly on `DONE`/`FAILED` — same file, plus
      `testStreamClosesOnFailedTooNotJustDone` and `testStreamEmitsErrorWhenNoStatusEverRecorded`

**Files:** `server/src/main/kotlin/com/borinquenterrier/cef/Application.kt`

---

#### AU-5 — Web client: two-phase progress UI ✅ DONE

**What:** `uploadFile`/`addSourceUrl` in `web/src/App.tsx` change from one blocking `await fetch()`
to: `POST /api/sources` (now fast) → open a stream against `/api/sources/{id}/stream`, mirroring
`useAgentStream.ts`'s existing `EventSource` pattern → render "Uploaded" (done immediately) and a
live digestion-phase label as two distinct states, replacing the single "Uploading and parsing..."
spinner. Also surfaces `src.status` in the sources list itself, so a page reload mid-digestion still
shows something meaningful without needing to auto-reconnect the stream.

**Acceptance criteria:**
- [ ] ~~AC-2's (Phase 14) Vitest/RTL infra used to test~~ — **blocked on Phase 14 AC-2**, which is
      not started yet (the web client has zero automated frontend tests today). Substituted with
      `tsc -b`, `eslint .`, and `vite build` all passing, plus the manual verification below.
- [x] Manual dry run: real live smoke test via the mock LTI platform (`:server:runDemoLtiPlatform`)
      + Vite dev server + Chrome automation — uploaded real `.ics` fixtures, confirmed via network
      logs the exact designed sequence (`POST /api/sources` → `202` → `GET .../stream` → `GET
      /api/sources` refresh), toast and list state updated correctly

**Files:** `web/src/App.tsx`, `web/src/useSourceStream.ts` (new, mirrors `useAgentStream.ts`)

### Build order

```
AU-1 (fast persist + 202)      standalone
AU-2 (background job)          needs AU-1 (job runs against the persisted file, not a temp one)
AU-3 (phase markers)           needs AU-2 (only meaningful once the pipeline actually runs
                                in the background; the listener interface itself is standalone)
AU-4 (SSE endpoint)            needs AU-3 (streams the events AU-3 emits)
AU-5 (web client UI)           needs AU-4 (consumes the stream)
```

### Ruled out (this phase)

- **Polling instead of SSE.** Rejected — the project already has a working SSE mechanism and
  client-side hook shape (`useAgentStream.ts`); reusing it beats a second async pattern for a
  near-identical problem.
- **Persisted job queue surviving server restarts.** Deferred, not rejected — see ADR 0012;
  revisit if the server ever runs multiple replicas.
- **Client-side simulated progress bar.** Rejected — doesn't address the actual problem (still one
  blocking call under the hood, no real visibility, same robustness risk on slow networks or large
  documents).
