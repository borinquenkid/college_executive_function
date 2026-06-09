# College Executive Function — Development Roadmap

> **Source of truth for all planned work.** `AGENTS.md` provides mandates and architecture context.
> **CRITICAL PRIORITY**: High CRAP index files (complexity² × (1 - coverage)³) are the primary source of bugs.
> Phases are ordered: (1) CRAP Remediation, (2) User-Reported Issues, (3) New Features.
> Within phases, items are ordered by user impact × implementation readiness.

---

## ⚠️ Known Issues / Tech Debt

| Issue | Notes |
|---|---|
| `ModelNegotiationIntegrationTest` disabled | Fails with "Unauthorized" against the live Gemini API — `.env` lacks a valid `CEF_GEMINI_API_KEY`/`GOOGLE_ACCESS_TOKEN`. Disabled via `.config(enabled = false)` (2026-06-07) to unblock executable publishing. Re-enable in `composeApp/src/jvmTest/kotlin/com/borinquenterrier/cef/ModelNegotiationIntegrationTest.kt:13` once credentials are restored. |

---

## 🔴 PRIORITY: CRAP Index Remediation (Phases 0.1 – 0.8)

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
* **Gemini API Daily Quota Rate Limit Issue**
  * **Description**: Ingesting a calendar and two syllabi simultaneously frequently triggers a `QuotaExhausted: Rate limit reached` error due to multiple concurrent requests.
  * **Proposed Solution**: Improve the Critic-Actor and Event generation loops to stagger requests, run sequential retries with exponential backoff, and notify the user with a clearer countdown.
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
