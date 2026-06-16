# College Executive Function — Development Roadmap

> **Source of truth for all planned work.** `AGENTS.md` provides mandates and architecture context.
> **CRITICAL PRIORITY**: High CRAP index files (complexity² × (1 - coverage)³) are the primary source of bugs.
> Phases are ordered: (1) CRAP Remediation, (2) User-Reported Issues, (3) New Features.
> Within phases, items are ordered by user impact × implementation readiness.

---

## 🎯 Current Status (June 2026)

**Current Phase: Phase 6 complete / Phase 1 next** — Token-efficient source processing (Phase 6) completed; Phase 1 (custom Google Calendar selection UI) is the next planned work

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
| `ModelNegotiationIntegrationTest` disabled | Fails with "Unauthorized" against the live Gemini API — `.env` lacks a valid `CEF_GEMINI_API_KEY`/`GOOGLE_ACCESS_TOKEN`. Disabled via `.config(enabled = false)` (2026-06-07) to unblock executable publishing. Re-enable in `composeApp/src/jvmTest/kotlin/com/borinquenterrier/cef/ModelNegotiationIntegrationTest.kt:13` once credentials are restored. |

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
| **0.23+** | **Continue CRAP remediation (TBD)** | ⏳ **NEXT** | — |

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

### Phase 1 — Custom Google Calendar Selection UI
Add ability to fetch available Google Calendars, save the selected calendar ID/name to preferences, and configure the synchronization pipeline to target the chosen calendar. This enables flexible desktop testing using specific test calendars instead of hardcoding target IDs.
* **Status**: ⏳ Planned
* **Tasks**:
  1. Fetch available calendars via `RemoteCalendarRepository.getAvailableCalendars()` (wired through `syncService.listCalendars()`).
  2. Add preference keys (`google_calendar_id`, `google_calendar_name`) to `PreferencesRepository` / `StudyPreferences`.
  3. Display a dropdown menu in `SettingsScreen.kt` listing all fetched Google Calendars and saving the selected choice.
  4. Refactor `GoogleRemoteCalendarRepository.kt` to query and use the selected calendar ID from settings rather than defaulting to `"CEF Academic"`.

---

### Phase 2 — Google Calendar, Gemini Quota, and OAuth Improvements
These are user-reported issues and feature requests targeted for the next development cycles:
* **Target Google Calendar Creation Capability** (Feature Request)
  * **Description**: The app currently only allows picking from existing calendars retrieved from the user's Google Account. There is no option in the settings UI to create a *new* calendar.
  * **Proposed Solution**: Add a "Create New Calendar" button/dialog in `SettingsScreen.kt` that calls `GoogleCalendarSyncService.createCalendar(name)` to instantiate a fresh calendar directly from the app.
* **Gemini API Daily Quota Rate Limit Issue** — ⚠️ **Superseded by Phase 6** (see below)
  * **Description**: Ingesting a calendar and two syllabi simultaneously frequently triggers a `QuotaExhausted: Rate limit reached` error due to multiple concurrent requests.
  * **Superseded solution**: Full plan in **Phase 6** — content-addressable cache (6.1), sequential processing queue (6.2), global rate-limit hold strategy (6.3).
* **Google OAuth Stale Connection / JSON Auth Error**
  * **Description**: On startup, if local session tokens are stale/expired, the connection shows a raw JSON authentication error. Disconnecting and reconnecting resolves it.
  * **Proposed Solution**: Auto-detect invalid refresh tokens at startup inside `GoogleAccountFlow` and transition the status cleanly to `Unlinked` instead of throwing raw JSON error messages.

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
| **Lenient Title Matching** | Refactored `SyllabusEvaluationSuite` matching logic with normalized containment check to eliminate false negatives |
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
- Create a test runner script/class (`SyllabusEvaluationSuite`) that parses the test syllabi.
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
