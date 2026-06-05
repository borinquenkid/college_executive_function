# College Executive Function — Development Roadmap

> **Source of truth for all planned work.** `GEMINI.md` provides mandates and architecture context.
> Priorities are ordered by user impact × implementation readiness. Items within a phase are listed
> highest-priority first. Each phase should be completed before beginning the next.

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
Implement visited-state graph tracking in `CriticActorAIService` to handle natural convergence and multi-step oscillation cycles. Guided by [PLAN.md](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/PLAN.md).

### 5.2 — Active Lifecycle Agent Harness (COMPLETED)
Implement `AgentHarness` to orchestrate startup and once-daily polling of local watched directories and Google Drive, sequentially processing new files and synchronizing calendar mutations. Guided by [PLAN.md](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/PLAN.md).

### 5.3 — Startup Check-In Interview Loop (COMPLETED)
Query incomplete past-due study blocks and tasks at startup/daily check-in, presenting an interactive interview dialog for completion confirmation or automated rescheduling. Guided by [PLAN.md](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/PLAN.md).

### 5.4 — Stateful User Preference Memory (COMPLETED)
Store user calendar edits and deletions to derive implicit scheduling constraints, injecting them into future AI prompt generations. Guided by [PLAN.md](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/PLAN.md).

### 5.5 — Sync Re-negotiation UI (COMPLETED)
Replace silent conflict resolution during two-way sync with interactive user proposal diffs. Guided by [PLAN.md](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/PLAN.md).

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

