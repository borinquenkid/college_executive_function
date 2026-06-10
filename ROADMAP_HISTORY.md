# College Executive Function — Historical Roadmap Details

This document preserves the detailed plans, deliverables, and summaries of completed features and refactoring passes. The active development roadmap is maintained in [ROADMAP.md](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/ROADMAP.md).

---

## 🔴 CRAP Index Remediation (Completed Details)

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

### Phase 0.22 — ContextAgent Decomposition ✅ **COMPLETED**

**Target:** Decompose context generation and fragment ranking (CRAP 31.03)

**Completed 2026-06-09.**

---

### Original CRAP Remediation Plan (Phases 0.1 – 0.8) — Completed Details

#### Phase 0.1 — GeminiAIService.kt (CRAP 67.50 → Target < 40)
- **Decomposition Plan:**
  1. Extract `RetryStrategy` + backoff logic → new `GeminiRetryService`
  2. Extract model negotiation → new `GeminiModelNegotiator`
  3. Extract JSON parsing → new `GeminiResponseParser` (partially done; complete it)
  4. Extract response builders → new `GeminiPromptBuilder`
  5. Leave `GeminiAIService` as thin facade coordinating calls

#### Phase 0.2 — SettingsScreen.kt (CRAP 57.03 → Target < 25)
- **Decomposition Plan:**
  1. Extract preference parsing → `SettingsPreferencesParser` (partially done; complete coverage)
  2. Extract API key validation → `ApiKeyValidator`
  3. Extract Google auth flow → `GoogleAuthSettingsFlow`
  4. Extract drive settings → `DriveSettingsPanel` (separate Composable)
  5. Leave `SettingsScreen` as pure layout + delegation

#### Phase 0.3 — AppController.kt (CRAP 30.51 → Target < 15)
- **Decomposition + Coverage Plan:**
  1. Extract sync logic → new `SyncOrchestrator`
  2. Extract agent polling → new `AgentPollingService`
  3. Add integration tests for state transitions
  4. Target 90%+ coverage on extracted modules

#### Phase 0.4 — AcademicCalendar.kt (CRAP 34.21 → Target < 20)
- **Decomposition Plan:**
  1. Extract event filtering → `EventFilterService`
  2. Extract layout logic → separate `CalendarListPanel`, `CalendarDetailsPanel` Composables
  3. Extract event mutations → `CalendarEventMutationHandler`
  4. Add Compose UI tests for key interactions

#### Phase 0.5 — ContextAgent.kt (CRAP 31.03 → Target < 20)
- **Refactoring Plan:**
  1. Extract TF-IDF ranking → `FragmentRanker`
  2. Extract context aggregation → `ContextAggregator`
  3. Extract prompt injection → `ContextualPromptBuilder`

#### Phase 0.6 — AiPrompts.kt (CRAP 41.30 → Target < 25)
- **Refactoring Plan:**
  1. Extract study plan constraints → `StudyPlanPromptBuilder`
  2. Extract categorization rules → `CategorizationPromptBuilder`
  3. Extract event extraction → `EventExtractionPromptBuilder`
  4. Extract chat system prompts → `ChatSystemPromptBuilder`
  5. Leave `AiPrompts` as coordinator

#### Phase 0.7 — CollisionResolver.kt (CRAP 41.09 → Target < 20)
- **Refactoring Plan:**
  1. Extract scheduling algorithm → `SchedulingAlgorithm`
  2. Extract constraint validation → `ScheduleConstraintValidator`
  3. Extract collision detection → `CollisionDetector`

#### Phase 0.8 — AgentHarness.kt (CRAP 38.41 → Target < 20)
- **Refactoring Plan:**
  1. Extract directory polling → `DirectoryPoller`
  2. Extract file ingestion orchestration → `FileIngestionOrchestrator`
  3. Extract sync coordination → `SyncCoordinator`

---

## 🛠️ Historical CRAP Risk Reduction Plan (Phases 1–9, COMPLETED)

### Strategy
Two complementary levers reduce CRAP:
1. **Coverage** — adding tests brings `(1 - coverage)^3` toward 0. Most effective when coverage is low.
2. **Refactoring** — splitting large methods reduces `complexity^2`. Most effective when a single method dominates complexity.

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

## 🚀 Completed Feature Deliverables Details (June 2026)

### Phase 1 — Custom Google Calendar Selection UI ✅ **COMPLETED**
* **Work items:**
  1. Wired `RemoteCalendarRepository.getAvailableCalendars()` and `syncService.listCalendars()` to fetch available calendars.
  2. Extended `StudyPreferences` / `PreferencesRepository` with `googleCalendarId` and `googleCalendarName` settings properties.
  3. Built Google Calendar dropdown selection menu in `SettingsScreen.kt`.
  4. Updated `GoogleRemoteCalendarRepository.kt` to target the custom `googleCalendarId` resolved from preferences settings.

### Phase 2 — Google Calendar, Gemini Quota, and OAuth Improvements ✅ **COMPLETED**
* **Work items:**
  1. Created target Google Calendar creation button/dialog in `SettingsScreen.kt` using `GoogleCalendarSyncService.createCalendar(name)`.
  2. Wrapped Gemini API invocations with rate limit backoff retry handler to stagger and throttle concurrent ingest streams.
  3. Hardened Google OAuth start-up status check to detect expired refresh tokens and transition the account connection status cleanly to `Unlinked`.

### Phase 1 — High Impact, Near-Term (Do First)

#### 1.1 — Multi-Source Chat Context
- **Work items:**
  1. Add `ContextAgent.queryAllSources(sources, conversationHistory, query)` — aggregates fragments from all stored `SourceItem`s + their SQLite `SourceFragment` rows into a single ranked context window.
  2. Thread `conversationHistory: List<ChatMessage>` into `GeminiAIService.generateChatResponse()` so the model has prior turns for follow-up questions.
  3. Update `ChatPanel` to use `queryAllSources` (with a "Scope: All Sources / This Source" toggle).
  4. Update `AppController` to persist `chatMessages` across sessions.
  5. Add Kotest unit tests for aggregation logic + MockK test for context builder.

#### 1.2 — .ics Export
- **Work items:**
  1. Refactor `ICalGenerator.buildAcademicCalendar(events: List<Event>)` to accept the real event list instead of the hardcoded sample.
  2. Add `expect fun writeIcsFile(content: String): String` / `actual` for JVM (write to `~/Downloads`), Android (MediaStore), iOS (share sheet).
  3. Add "Export to .ics" button in `StudioPanel`.
  4. Wire button → `ICalGenerator` → `writeIcsFile` → toast/snackbar with file path.
  5. Add Kotest unit test verifying round-trip: events → ICS string → parse back → same events.

#### 1.3 — Sync Hardening
- **Work items:**
  - **Token Refresh in Sync Loop:** Wrap each HTTP call in `GoogleCalendarSyncService` with a token-refresh retry: if a `401` is returned, call `GoogleAuthService.refreshToken()` and retry once, then throw.
  - **Pagination for `getEvents()`:** Implement `pageToken`-based pagination loop in `GoogleRemoteCalendarRepository.getEvents()` until `nextPageToken` is null.
  - **Update Conflict Resolution Strategy:** On conflict (local-modified event exists in remote with a newer `updated` timestamp), log a conflict warning and display a diff/merge prompt in the Calendar UI.

---

### Phase 2 — Medium Impact (Do Second)

#### 2.1 — Visual Progress Tracking
- **Work items:**
  1. Add a `timeUntilDue(event: Event): Duration` utility in `Event.kt`.
  2. In `EventItemView`, display a `LinearProgressIndicator` for DEADLINE/FINALS events showing completion of the window between `studyPlanStart` and `dueDate`.
  3. Add a "Due in X days" chip on deadline events in the calendar list.
  4. In `StudioPanel`, add a "Semester Health" summary card: events due in next 7 days, next 30 days.
  5. Add unit tests for `timeUntilDue` edge cases (past due, due today, future).

#### 2.2 — Scheduling Fine-Tuning (User-Configurable Study Parameters)
- **Work items:**
  1. Add `StudyPreferences` data class: `studyStartHour`, `studyEndHour`, `lunchStart`, `lunchEnd`, `dinnerStart`, `dinnerEnd`, `maxStudyBlockHours`, `preferredBreakMinutes`.
  2. Persist `StudyPreferences` via `RoutineRepository` (or a new `PreferencesRepository`).
  3. Inject `StudyPreferences` into `CollisionResolver` constructor and `AiPrompts.getStudyPlanPrompt()`.
  4. Add UI controls in `SettingsScreen` for all parameters.
  5. Add Kotest parameterized tests for `CollisionResolver` with custom preferences.

#### 2.3 — Syllabus-to-Study Schedule Fine-Tuning (Weighted Deliverables)
- **Work items:**
  1. Extend `GeminiAIService.generateCalendarEvents()` to extract `gradeWeight: Float?` from syllabus text alongside the event title/date.
  2. Store `gradeWeight` in the `Event` model.
  3. Update `AiPrompts.getStudyPlanPrompt()` to include grade weights per deliverable so Gemini can allocate proportional study time.
  4. Add `ConfabulationGuardTest`-style tests to verify weight extraction accuracy.

---

### Phase 6 — Web Client & Agent-User Interaction (AG-UI) Protocol Integration

Bring CEF to the web using a React frontend that dynamically communicates with the Ktor server via a real-time agentic stream, eliminating duplicated data models.

#### 6.1 — Gradle & Dependency Realignment
* Update `server/build.gradle.kts` to depend on `:composeApp` JVM compile target.
* Align library catalog dependencies (`kotlinx-datetime`, `multiplatform-settings`, Ktor JSON serialization) for server scope.
* Configure duplicatesStrategy for copy and zip archive tasks.

#### 6.2 — Ktor AG-UI SSE Stream Endpoint
* Implement manual Server-Sent Events (SSE) endpoint `/api/agent/stream` inside `Application.kt`.
* Map Critic-Actor refinement loops, database queries, and token generation into standard AG-UI events (`RUN_STARTED`, `REASONING_DELTA`, `TOOL_CALL_START`, `TEXT_MESSAGE_DELTA`, etc.).

#### 6.3 — React Frontend & Proxy Scaffolding
* Bootstrap a Vite-React-TypeScript application inside `/web`.
* Set up standard `/api` request redirection to Ktor backend in `vite.config.ts`.
* Establish custom typography (Space Grotesk, Outfit) and layouts in `index.css`.

#### 6.4 — Client useAgentStream Connection Hook
* Develop custom `useAgentStream` hook to manage `EventSource` connections.
* Process streaming AG-UI payloads and distribute updates to React state.

#### 6.5 — Dynamic Agentic UI Views
* Render live "thought bubbles" and reasoning logs during Critic-Actor executions.
* Stream response texts word-by-word with loading indicators.
* Render calendar agenda views and source directories dynamically when state updates arrive.
