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
| **.ics Export** | Refactored `ICalGenerator` + expect/actual `writeIcsFile` with iOS Share Sheet & Android MediaStore actuals |

---

## Phase 1 — High Impact, Near-Term (Do First)

These items have clear scope, high user value, and sufficient existing infrastructure to build on.

### 1.1 — Multi-Source Chat Context ⚡ **HIGHEST PRIORITY**

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

### 1.3 — Sync Hardening

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

### 2.1 — Visual Progress Tracking

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

### 2.2 — Scheduling Fine-Tuning (User-Configurable Study Parameters)

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

### 2.3 — Syllabus-to-Study Schedule Fine-Tuning (Weighted Deliverables)

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

## Phase 3 — Infrastructure & Polish (Do Last)

Low user-facing impact but important for production readiness and maintainability.

### 3.1 — Client Secrets Management

Secure injection mechanism for `client_secret.json` rather than shipping it in the repo or
requiring manual placement. Options: environment variable injection at build time, encrypted
local keystore, or a server-side token proxy.

### 3.2 — Compose UI Tests for Key Flows

Add `@Composable` tests for:
- `TaskDecompositionDialog` state progression (idle → loading → results → accepted)
- `ChatPanel` message submission and response rendering
- `SettingsScreen` API key save/clear round-trip

### 3.3 — Performance: Source Fragment Indexing

For large syllabi, `ContextAgent.queryAllSources()` (Phase 1.1) will concatenate many fragments
into a single prompt. Add a simple relevance ranking (TF-IDF or embedding cosine similarity)
to select only the top-K fragments before sending to Gemini.

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
```
