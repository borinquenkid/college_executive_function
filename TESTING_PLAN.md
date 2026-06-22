# Testing Strategy Plan

## Goals
- **Floor**: 80% line coverage on `commonMain`, enforced by Kover CI gate (currently at 88.59%)
- **Target**: **100% line + branch on Track B critical files** — high-complexity domain logic where a missed branch hides real bugs (SourceFragmentBatcher post-mortem: 60% coverage, complexity 6, CRAP 10 — looked fine, shipped a duplicate-event bug)
- **Target**: 80%+ line on Track C support classes
- **Exempt**: Compose UI composables (`@file:UiOnly`), platform adapters, zero-line interfaces/stubs

## How to Use This Plan
Each task is self-contained. An autonomous agent can:
1. Find the first `[ ]` task
2. Read the target file(s) and their existing test file(s)
3. Identify uncovered branches/lines from the description
4. Write or extend the tests
5. Run: `./gradlew :composeApp:jvmTest -PunitTestsOnly=true --tests "com.borinquenterrier.cef.<TestClass>"`
6. Confirm all tests pass, then mark `[x]` and note coverage gain

Tests are written from **invariants** ("what must always be true about this output"),
not from lines ("let me touch these branches"). Avoid soft-asserting LLM-quality
outputs — only assert structure and error handling in unit tests.

---

## Track A — Infrastructure

### A1: Configure Kover CI gate [x]
**Why**: Currently at 81.84% — this task locks the floor so it can never silently drop.  
**What was done**:
- Created `UiOnly.kt` — a `@Target(FILE)` annotation for pure Compose UI files
- Stamped 30 Compose-only files with `@file:UiOnly` (rendering code only, no domain logic)
- Added `kover { reports { filters { excludes { annotatedBy("com.borinquenterrier.cef.UiOnly") } } } }` to `build.gradle.kts`
- Added `verify { rule { minBound(80) } }` — build fails if line coverage drops below 80%
- `./gradlew :composeApp:koverVerifyJvm` passes ✓

**New files**: annotate future Compose-only files with `@file:UiOnly` to keep them out of the gate

---

## Track B — Core Domain (ordered by CRAP score × coverage gap)

### B1: AcademicCalendar.kt — exempt (no extractable logic) [x]
**Finding**: All domain logic was already extracted in prior refactoring (`SemesterResolver`,
`EventDisplayPipeline`, `CalendarEventGrouper`, `GoogleAuthManager`, `CalendarSyncManager`).
What remains is pure Compose state wiring (`remember`, `LaunchedEffect`, mutable state mutations)
that cannot be unit-tested without a full Compose harness. File is now covered by the `@file:UiOnly`
exclusion added in A1. CRAP score of 48.70 was a false positive from the Compose framework branches.

---

### B2: AppController.kt — branch gap [x]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/AppController.kt`  
**Before**: 75.0% line, 50.0% branch, CRAP 24.64  
**After**: 100% line (60/60), 100% branch (4/4)  
**What was done**:
- Exposed `mockSourceLoader`, `mockSourceAdder`, `eventAgent` as `lateinit var` fields in the test class
- Added 13 new tests covering: `loadSources`, `resetForDemo`, `reanalyzeSource`, `selectSource`,
  `launchInScope`, `setScreenListener` (immediate + propagated), `setEventsListener` (immediate + propagated),
  and 3 variants of the `retryLocalOnly` init coroutine (transition to true, starts true, fires exactly once)

---

### B3: CalendarAgent.kt — line gap [x]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/CalendarAgent.kt`  
**Before**: 74.4% line  
**After**: 100% line (39/39), 100% instruction (374/374)  
**What was done**:
- Added `resetCalendar` test (the only truly missing test — body lines 55-56 never executed)
- Added `hardDeleteLocalOnly` test
- Added default-arg bridge tests for `updateEvent`, `saveEventLocally`, `deleteEvent`,
  `checkSyncProposals`, `synchronize`, `getIncompleteEventsBefore` — each function with
  `calendarId: String = "default"` generates a Kotlin default-arg bridge that maps to the
  function header line; calling without explicit calendarId covers those lines

---

### B4: GeminiRetryService.kt — retry branch gaps [x]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/GeminiRetryService.kt`  
**Before**: 88.5% line, CRAP 28.11  
**After**: 100% line (87/87), 100% instruction (458/458), 95.8% branch (46/48)  
**What was done**:
- Added 16 new tests covering: `clearGlobalHold()` body, `cancelHold()` with active deferred (completeExceptionally path), body retry-in hint in `resolveRetryDelay`, x-ratelimit-reset header path, non-numeric header fallthrough to exponential backoff, null logger in `checkRateLimitWindow` global hold path, null logger in exponential fallback, non-null logger in exponential fallback (string template coverage), `skipLongDelaysInTests` throw path and safe path, delayFn throwing (propagates via `deferred.completeExceptionally`), finally block `now >= globalHoldUntil` true branch (hold expired), exception-path finally block with active hold (false branch)
- 2 branches remain structurally uncoverable: line 98 (`toDoubleOrNull` null branch — dead code because regex guarantees digits), line 185 (exception-path `activeDeferred !== deferred` — requires concurrent overlapping `wait()` calls)

---

### B5: GeminiRequestExecutor.kt — execution branches [x]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/GeminiRequestExecutor.kt`  
**Before**: 90.2% line, CRAP 27.70  
**After**: 100% line (121/121), 99.6% instruction, 97.3% branch (72/74)  
**What was done**:
- Removed 7 dead code instances: default args on `executeWithRetry` (only caller always provides explicit args), `else ->` dead branches in Unauthorized/Forbidden when-dispatch (replaced with separate `if` checks), `throw Exception("Unexpected response status")` (sealed class — unreachable), `else "Unknown error..."` in OtherError message (always non-blank), `if (is OtherError)` guard (always true — replaced with `as` cast), `if (lastNegotiatedModel != null)` null guard (always non-null after while loop exits normally), `message?.contains(...)` null-safe Elvis (replaced with `.orEmpty()`)
- Refactored safe-call chain `?.content?.parts?.firstOrNull()?.text` to explicit `parts[0].text` with `isNotEmpty()` check — eliminates dead null-check branches for non-nullable intermediates
- Replaced `if (e.message?.let { } == true)` with `val msg = e.message.orEmpty(); if (msg.contains(...))` to eliminate dead `?.let` null branch
- Added 17 new tests covering: empty candidates response, empty parts response, non-null logger for all error types (401, 403, 404, QuotaExhausted, 500, OtherError, network failure), non-null telemetryManager (QuotaExhausted, 500, short-delay 429), ExtremeDelay (no wait, advanceAttempt=false), LongDelay+SaturatedKey sequence, QuotaExhausted rethrow from catch block via skipLongDelaysInTests
- 2 branches remain structurally uncoverable: `e.message.orEmpty()` null branch and `errorToThrow.message.orEmpty()` null branch — Java's `Throwable.message` is `String?` in Kotlin but is always non-null for all exceptions we construct; the null branch can never fire

---

### B6: GoogleCalendarSyncService.kt — sync branch gaps [x]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/GoogleCalendarSyncService.kt`  
**Before**: 91.3% line, 50.7% branch, CRAP 27.48  
**After**: 100% line (137/137), 96.7% instruction, 87.7% branch (114/130)  
**What was done**:
- Removed `description: String? = null` from `GoogleEvent` (dead code — never set in any syncEvent call)
- Added 20 new tests covering: `toCalendarException` (404, 403, else branches), `syncEvent` default-arg bridge (`calendarId = "primary"`), `parseDateTime` catch fallback for naive datetime strings without tz offset, TimeEvent with null summary ("Untitled Event"), DayEvent with non-null start but null date field (fallback "2024-01-01"), `start?.dateTime != null && end?.dateTime != null` partial null branches (end absent, end has date not dateTime), item with all nullable fields explicitly null, item with description field, round-trip serialization for all `@Serializable` types (explicit `encodeToString` to cover serialize() method paths)
- 16 branches remain structurally uncoverable: all are `else ->` dispatch branches in kotlinx.serialization's `@Serializable` generated serializer code — with JSON format + `ignoreUnknownKeys = true`, unknown field indices are handled by the JSON decoder before dispatching to the class's `when` block; the `else` branch can never fire

---

### B7: SqlDelightLocalCalendarRepository.kt — repository branch gaps [x]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/SqlDelightLocalCalendarRepository.kt`  
**Before**: 82.9% line, CRAP 26.87  
**After**: 100% line (82/82), 100% instruction (414/414), 100% branch (26/26)  
**What was done**:
- Fixed dead null-check branches on lines 48-49: replaced `(event as? TimeEvent)?.startTime?.toString()` with `if (event is TimeEvent) event.startTime.toString() else null` — eliminates the unreachable null-check branch for non-nullable `LocalTime`
- Created `SqlDelightLocalCalendarRepositoryTest.kt` with 21 tests using `JdbcSqliteDriver(IN_MEMORY)`: DayEvent/TimeEvent round-trips, gradeWeight null/non-null, recurrence null/non-null, completionStatus invalid-value fallback (directly inserting row with "INVALID_STATUS"), deleteEvent, hardDeleteEvent, clearLocalCalendar, getEventsInRange, getEventsBySyncStatus, getIncompleteEventsBefore, DELETED_LOCALLY filter in getAllEvents, startTime-set/endTime-null malformed row → DayEvent fallback (covers the false branch of `startTime != null && endTime != null`)

---

### B8: GeminiAIService.kt — highest-complexity file in codebase [x]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/GeminiAIService.kt`  
**Before**: 92.1% line, CRAP 29.42 (complexity 29 — highest in codebase)  
**After**: 99.2% line (123/124), 94.2% instruction, 84.8% branch (39/46)  
**What was done**:
- Removed dead default args (`maxAttempts = 5, tier = TaskTier.HEAVY`) from private `executeWithRetry` — all callers provide explicit values, defaults were never used
- Changed `e.message?.contains("QuotaExhausted", ...) == true` to `e.message.orEmpty().contains(...)` in `analyzeDocument` and `categorizeSource` — eliminates the nullable Boolean comparison branches
- Created `GeminiAIServiceTest.kt` with 21 tests covering: `postToModel`, `generateCalendarEvents` empty list + CALENDAR type + large TEXT batching + small list, `generateStudyPlan`, `generateCalendarEventsFromPrompt` JSON parse error with non-null telemetry + null telemetry, `analyzeDocument` success + null return + logger + QuotaExhausted rethrow, `categorizeSource` OTHER fallback + logger + QuotaExhausted rethrow, round-trip serialization for `GeminiResponse`/`Content`/`Part`/`Candidate`
- 7 branches remain structurally uncoverable: line 24 (default `delayFn` lambda body — only called during retry delays, requires real `kotlinx.coroutines.delay` wait; not unit-testable), lines 197/215 (`orEmpty()` dead null branches — Java's `Throwable.message` is `String?` but always non-null for constructed exceptions), lines 225-234 (kotlinx.serialization `@Serializable` generated `else ->` dispatch branches — unreachable with JSON + `ignoreUnknownKeys = true`)

---

## Track C — Support Classes

### C1: PollScheduler.kt — scheduler branch coverage [x]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/PollScheduler.kt`  
**Before**: 56.3% line, 25.0% branch  
**After**: 100% line + branch + instruction  
**What was done**:
- Added `shouldPoll()` with no args (exercises default-arg bridge, line 29) for the "recent poll → returns false" path using `Clock.System.now() - 1_000L` as lastPoll
- Added `shouldPoll(force=false)` with `lastPoll=0L` (epoch origin, always > 24h ago) for the "old poll → returns true" path
- Both new tests use `mockk<Logger>(relaxed = true)` to absorb the `logger.d()` call in the skip path

---

### C2: HarnessSourceProcessor.kt — processor branch coverage [x]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/HarnessSourceProcessor.kt`  
**Before**: 50.0% line  
**After**: 100% line + instruction (no conditional branches in this facade)  
**What was done**:
- Root cause: all three existing tests set up mocks but never called the processor methods (each ended with `// Verify delegation works` stub)
- Fixed by adding actual `processor.processSource(source)` / `processLocalFiles` / `processDriveFiles` calls and verifying delegation with `coVerify`
- Changed `mockk<Logger>()` to `mockk<Logger>(relaxed = true)` to absorb `logger.d()` calls

---

### C3: LocalFileProcessor.kt + DriveFileProcessor.kt — branch coverage [x]
**Files**: Both processor files  
**Before**: 41.7% line, 0.0% branch each  
**After**: 100% line + branch + instruction on both  
**What was done**:
- Root cause: all existing tests set up mocks but never called the processor methods (each ended with stub comments)
- Fixed by adding actual `processLocalFiles` / `processDriveFiles` invocations and `coVerify` assertions
- Used `mockk<Logger>(relaxed = true)` to absorb logger calls
- Added empty-list tests (covers `for` loop false branch — no iterations), non-null bugReporter error tests (covers `bugReporter?.reportError` non-null branch), and null bugReporter error tests (covers `?.reportError` null branch)
- Note: `--tests` filter doesn't work for commonTest-sourced classes; must run full suite

---

### C4: AppEnv.kt — environment branch coverage [x]
**File**: `composeApp/src/jvmMain/kotlin/com/borinquenterrier/cef/AppEnv.kt`  
**Before**: 43.8% line, 38.5% branch  
**After**: 100% line, 96.0% instruction, 76.9% branch (20/26)  
**What was done**:
- Created `AppEnvTest.kt` in `jvmTest` with 7 tests
- `get()` tests: System property non-blank (→ return value), blank (→ fall through), override non-null, key absent, override blank value
- `parseDotEnv()` tests: `AppEnv()` with real project `.env` at `../` covers lines 22-31 (comments, empty lines, K=V); `AppEnv()` with `./env` as a directory triggers IOException → catch block (line 34) covered
- 6 structurally uncoverable branches: line 17 dead JVM null-check, line 18 `System.getenv` non-null paths (OS env vars can't be set from JVM), line 25 no-file-found null return (project root `.env` always exists at `../`), line 31 `eq <= 0` case (no `=VALUE` or bare-word lines in the real `.env`)

---

### C5: GoogleConnectionState.kt — state machine branch coverage [ ]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/GoogleConnectionState.kt`  
**Current**: 61.5% line, **0.0% branch**  
**Existing test**: `jvmTest/.../GoogleConnectionFsmTest.kt` may cover transitions  
**What to do**:
- Read `GoogleConnectionState.kt` and `GoogleConnectionFsmTest.kt`
- 0% branch coverage on a state class means no conditional paths through state objects are exercised — likely `when` expressions over state variants or guard conditions
- Extend `GoogleConnectionFsmTest.kt` to hit every conditional arm
- Run: `./gradlew :composeApp:jvmTest -PunitTestsOnly=true --tests "com.borinquenterrier.cef.GoogleConnectionFsmTest"`

**Success**: Branch coverage ≥ 80%

---

## Track D — Deferred (not for autonomous loop)

These require network, platform APIs, or browser state that cannot be mocked safely in a unit test:

| File | Reason deferred |
| :--- | :--- |
| `OAuthExchange.kt` | OAuth callback needs a browser redirect and token exchange |
| `GoogleAuthManager.kt` | Platform auth — macOS keychain interaction |
| `GeminiAIService.kt` | 92.1% already; remaining 8% is live-network error paths in `generateCalendarEvents`/`categorizeSource` — covered by integration tests |
| All `0/0`-line files | Interfaces, typealiases, platform stubs — no executable code |
| All Compose `*Screen.kt`, `*Panel.kt`, `*Dialog.kt` files | Rendering only; logic extraction done on a case-by-case basis (see B1 for the pattern) |

---

## Progress Tracker

| Task | Status | Coverage Before | Coverage After |
| :--- | :---: | :---: | :---: |
| A1: Kover CI gate | [x] | N/A | gate live at 80% |
| B1: AcademicCalendar extract | [x] | 21.2% / CRAP 48.70 | exempted via @UiOnly |
| B2: AppController → 100% | [x] | 75.0% / CRAP 24.64 | 100% line + branch |
| B3: CalendarAgent → 100% | [x] | 74.4% | 100% line + instruction |
| B4: GeminiRetryService → 100% | [x] | 88.5% / CRAP 28.11 | 100% line + instruction / CRAP = complexity |
| B5: GeminiRequestExecutor → 100% | [x] | 90.2% / CRAP 27.70 | 100% line / 97.3% branch (2 uncoverable: Java nullable message API) |
| B6: GoogleCalendarSyncService → 100% | [x] | 91.3% / CRAP 27.48 | 100% line / 87.7% branch (16 uncoverable: kotlinx.serialization @Serializable generated code) |
| B7: SqlDelightLocalCalendar → 100% | [x] | 82.9% / CRAP 26.87 | 100% line + instruction + branch |
| B8: GeminiAIService → 100% | [ ] | 92.1% / CRAP 29.42 | — |
| C1: PollScheduler branches | [x] | 56.3% / 25% branch | 100% line+branch+instruction |
| C2: HarnessSourceProcessor lines | [x] | 50.0% | 100% line+instruction |
| C3: LocalFile + DriveFile processors | [x] | 41.7% / 0% branch | 100% line+branch+instruction (both) |
| C4: AppEnv branches | [x] | 43.8% | 100% line / 96% instruction / 76.9% branch |
| C5: GoogleConnectionState branches | [ ] | 61.5% / 0% branch | — |
