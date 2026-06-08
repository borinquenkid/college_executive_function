# CRAP Risk Reduction Plan

> Generated 2026-06-08. Ordered by **risk-reduction impact** (CRAP score × feasibility).
> Tracks coverage improvements and refactoring work needed to bring every file below CRAP 30.
> See [CRAP.md](CRAP.md) for the baseline scores.

---

## Strategy

Two complementary levers reduce CRAP:
1. **Coverage** — adding tests brings `(1 - coverage)^3` toward 0. Most effective when coverage is low.
2. **Refactoring** — splitting large methods reduces `complexity^2`. Most effective when a single method dominates complexity.

Pure Compose UI files (`App.kt`, `AddRoutineItemDialog.kt`, `AcademicCalendar.kt`, `RoutineScreen.kt`) score high only because they have 0% coverage — not because of dangerous logic. They are **deferred** to a dedicated Compose UI testing pass (Phase 5) and skipped here.

---

## Phase 1 — `GoogleRemoteCalendarRepository.kt` (CRAP 220.94)
**Why first:** Highest absolute score. Low coverage (38.9%) with moderate complexity (29). Pure coverage win.

### Actions
- [x] Create `GoogleRemoteCalendarRepositoryTest.kt` in `jvmTest`
- [x] Mock `GoogleCalendarSyncService` with `MockK`
- [x] Add tests for all currently-uncovered paths:
  - `getCEFCalendarId` — calendar-not-found → create path
  - `deleteEvent` — 410 Gone swallow
  - `clearCalendar` — full sweep
  - `getEventsInRange` — date range filter
  - `getEventsBySyncStatus` with non-SYNCED status (returns empty)
  - `getIncompleteEventsBefore` — date filter
  - Overlap detection in `saveEvent`
  - `hardDeleteEvent` / `updateEvent` in isolation

---

## Phase 2 — `ModelManager.kt` (CRAP 41.20)
**Why second:** Worst coverage-to-complexity ratio of any logic file (26.5%). `downloadModel` is completely untested and has a latent progress-emission bug.

### Actions
- [x] Fix `downloadModel` progress emission bug — emit intermediate `DownloadProgress` inside chunk loop using `contentLength`
- [x] Add to `ModelManagerTest.kt`:
  - `downloadModel` — mock Ktor `MockEngine`, verify `DownloadProgress(1f, true)` emitted last
  - `isModelDownloaded = true` path
  - `downloadModel` with 404 — verify exception propagated
  - Intermediate progress values emitted during chunked download

---

## Phase 3 — `EventPresenter.kt` (CRAP 40.37)
**Why third:** Quick win — pure logic, zero I/O. Missing ~10 enum branch combinations.

### Actions
- [x] Add exhaustive `getEventBorderColor` tests for: `FINALS`, `SEMESTER_BOUND`, `STUDY_BLOCK`, `CLASS`, `REGULAR+MANUAL`, `REGULAR+STUDENT`, `REGULAR+SCHOOL`, `REGULAR+CLASS`
- [x] Add exhaustive `getCategoryLabel` tests for same missing combinations
- [x] Extract duplicated `when(source)` block into a shared private helper to remove duplication

---

## Phase 4 — `GoogleDriveService.kt` (CRAP 39.58)
**Why fourth:** Medium coverage (54.8%), no dedicated unit test file. Structural bugs documented.

### Actions
- [x] Fix `validateConnection` to use `withToken` instead of accepting a raw token parameter (Note: Decided to keep signature; verified usage in GoogleAccountFlow)
- [x] Fix `withToken` 401 detection — use `ResponseException.response.status` instead of string matching (Note: Completed in tests/implementation)
- [x] Create `GoogleDriveServiceTest.kt` in `jvmTest` with `MockEngine`:
  - `validateConnection` — success and 401 paths
  - `listFiles` — success and 401-with-refresh paths
  - `getFileContent` — Google Doc export path and binary path
  - `withToken` — verify refresh-and-retry behavior

---

## Phase 5 — `GeminiAIService.kt` (CRAP 153.53)
**Why fifth:** Highest complexity file (106). `executeWithRetry` is a 148-line God method. Coverage is decent (83.8%) so score is driven by complexity.

### Actions
- [x] Extract from `executeWithRetry` into focused private methods:
  - `handleRpdError(response)` — RPD/RPM quota handling
  - `handleStructuralError(response, modelName)` — 5xx blacklisting
  - `handleAuthError(response)` — 401/403 fatal throw
  - `handleClientError(response, modelName)` — 400/404 blacklisting
  - `applyExponentialBackoff(attempt, baseDelay)` — delay calculation
- [x] Extract `parseDecomposeTaskJson(raw: String): List<WorkUnit>` from `decomposeTask`
- [x] Extract `parseCategorizeSourceJson(raw: String): AcademicCategory` from `categorizeSource`
- [x] Add telemetry call (`logJsonError`) in `parseEventsJson` when fallback defaults are applied
- [x] Fix fragile time parsing — handle `HH:mm:ss` in addition to `HH:mm`

---

## Phase 6 — `CriticActorAIService.kt` (CRAP 110.11)
**Why sixth:** Score driven by complexity (67). `parseEvents` (15) and `parseTasks` (9) are biggest methods.

### Actions
- [x] Extract `parseEventFromJson(element: JsonElement): Event?` from `parseEvents` loop body
- [x] Extract `parseTaskFromJson(element: JsonElement): WorkUnit?` from `parseTasks` loop body
- [x] Verify existing tests still pass after refactor

---

## Phase 7 — `CalendarAgent.kt` + `EventAgent.kt` (CRAP 95.59 / 83.57)
**Why seventh:** Good coverage (83–86%) but high complexity. A few key methods lack direct tests.

### Actions — CalendarAgent
- [x] Add test for `synchronize` method (missed in original run due to Ollama timeout)

### Actions — EventAgent
- [x] Add tests for uncovered methods: `clearError`, `updateStatus`, `pushToCalendar`, `clear`,
  `clearDecomposition`, `loadIncompleteEvents`, `markEventCompleted`, `skipEvent`, `rescheduleEvent`

---

## Phase 8 — `AiPrompts.kt` (CRAP 42.06)
**Why eighth:** 91.4% coverage — only edge cases remain. Quick to close.

### Actions
- [x] Identify and add tests for the uncovered 8.6%
- [x] Specifically: test `formatHour` for edge cases (midnight=0, noon=12, PM hours)

---

## Phase 9 — Compose UI Files (Deferred) ⏳ FUTURE
**Files**: `App.kt` (210.00), `AddRoutineItemDialog.kt` (182.00), `AcademicCalendar.kt` (35.09), `RoutineScreen.kt` (20.00)

Require `createComposeRule()` test harness — deferred to a dedicated Compose UI testing sprint.

---

## Progress Tracker

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
