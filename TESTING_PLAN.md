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

### B4: GeminiRetryService.kt — retry branch gaps [ ]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/GeminiRetryService.kt`  
**Current**: 88.5% line, CRAP 28.11  
**Existing test**: `commonTest/.../GeminiRetryServiceTest.kt`  
**What to do**:
- Read both files
- `wait` (complexity 7) and `checkRateLimitWindow` (complexity 3) are the high-complexity methods — find which branches are uncovered in each
- Missing branches are typically: rate-limit window already active when `wait` is called, `wait` called with zero retries remaining, `checkRateLimitWindow` with a reset time in the past vs future
- Extend `GeminiRetryServiceTest.kt`
- Run: `./gradlew :composeApp:jvmTest -PunitTestsOnly=true --tests "com.borinquenterrier.cef.GeminiRetryServiceTest"`

**Success**: 100% line + branch coverage; CRAP score equals complexity only

---

### B5: GeminiRequestExecutor.kt — execution branches [ ]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/GeminiRequestExecutor.kt`  
**Current**: 90.2% line, CRAP 27.70  
**Existing test**: `jvmTest/.../GeminiRequestExecutorTest.kt`  
**What to do**:
- Read both files
- `executeWithRetry` and `executeWithRetryInternal` (the core retry loop) are where the uncovered 10% lives — find the specific branch arms not yet exercised (e.g., retry exhaustion, non-retryable errors that short-circuit the loop)
- Extend `GeminiRequestExecutorTest.kt`
- Run: `./gradlew :composeApp:jvmTest -PunitTestsOnly=true --tests "com.borinquenterrier.cef.GeminiRequestExecutorTest"`

**Success**: 100% line + branch coverage; CRAP score equals complexity only

---

### B6: GoogleCalendarSyncService.kt — sync branch gaps [ ]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/GoogleCalendarSyncService.kt`  
**Current**: 91.3% line, CRAP 27.48  
**Existing test**: `jvmTest/.../GoogleCalendarSyncServiceTest.kt`  
**What to do**:
- Read both files
- `getEvents` (complexity 11) is the most complex method and likely where the uncovered 8.7% lives — focus on pagination edge cases (empty page, single-page response, last page with no nextPageToken), and error paths in `toCalendarException`
- Extend `GoogleCalendarSyncServiceTest.kt`
- Run: `./gradlew :composeApp:jvmTest -PunitTestsOnly=true --tests "com.borinquenterrier.cef.GoogleCalendarSyncServiceTest"`

**Success**: 100% line + branch coverage; CRAP score equals complexity only

---

### B7: SqlDelightLocalCalendarRepository.kt — repository branch gaps [ ]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/SqlDelightLocalCalendarRepository.kt`  
**Current**: 82.9% line, CRAP 26.87  
**Existing test**: Coverage comes from multiple integration tests — search for direct tests in `jvmTest`  
**What to do**:
- Read the source file to understand which methods exist (complexity 24 total)
- Run `grep -rn "SqlDelightLocalCalendarRepository" composeApp/src/jvmTest` to find where it's tested
- Identify the 17% uncovered lines — typically: methods that return null when no record exists, update-when-not-found paths, delete-cascade behavior
- Write or extend a dedicated `SqlDelightLocalCalendarRepositoryTest.kt` in `jvmTest` using an in-memory JDBC driver
- Run: `./gradlew :composeApp:jvmTest -PunitTestsOnly=true --tests "com.borinquenterrier.cef.SqlDelightLocalCalendarRepositoryTest"`

**Success**: 100% line + branch coverage; CRAP score equals complexity only

---

### B8: GeminiAIService.kt — highest-complexity file in codebase [ ]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/GeminiAIService.kt`  
**Current**: 92.1% line, CRAP 29.42 (complexity 29 — highest in codebase)  
**Existing test**: `jvmTest/.../GeminiAIServiceTest.kt` (or similar — run `grep -rn "GeminiAIService" composeApp/src/jvmTest`)  
**What to do**:
- Read `GeminiAIService.kt` and the existing test file
- `generateCalendarEvents` (complexity 5) and `categorizeSource` (complexity 4) are highest-complexity methods — find uncovered arms
- Some branches may involve specific HTTP status codes, malformed JSON responses, or empty model lists — all mockable without a real API
- Any branch that genuinely requires a live Gemini call cannot be unit-tested; note it explicitly and cover it with a comment explaining why 100% is not achievable for that specific line
- Extend the test file; do NOT write integration tests here
- Run: `./gradlew :composeApp:jvmTest -PunitTestsOnly=true --tests "com.borinquenterrier.cef.GeminiAIServiceTest"`

**Success**: 100% line + branch on all mockable paths; any genuinely network-only branch documented with a `// not unit-testable: requires live Gemini response` comment; CRAP as close to 29 as achievable

---

## Track C — Support Classes

### C1: PollScheduler.kt — scheduler branch coverage [ ]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/PollScheduler.kt`  
**Current**: 56.3% line, 25.0% branch  
**Existing test**: `commonTest/.../PollSchedulerTest.kt`  
**What to do**:
- Read both files
- Scheduler logic typically has uncovered branches for: already-running guard (second call while first is active), cancellation mid-poll, error thrown during poll callback, zero-interval edge case
- Extend `PollSchedulerTest.kt`
- Run: `./gradlew :composeApp:jvmTest -PunitTestsOnly=true --tests "com.borinquenterrier.cef.PollSchedulerTest"`

**Success**: Branch coverage ≥ 75%; line ≥ 80%

---

### C2: HarnessSourceProcessor.kt — processor branch coverage [ ]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/HarnessSourceProcessor.kt`  
**Current**: 50.0% line  
**Existing test**: `commonTest/.../HarnessSourceProcessorTest.kt`  
**What to do**:
- Read both files
- Identify the untested 50% — typically unsupported source type handling, empty fragment list, error propagation from the underlying reader
- Extend `HarnessSourceProcessorTest.kt`
- Run: `./gradlew :composeApp:jvmTest -PunitTestsOnly=true --tests "com.borinquenterrier.cef.HarnessSourceProcessorTest"`

**Success**: Line coverage ≥ 80%

---

### C3: LocalFileProcessor.kt + DriveFileProcessor.kt — branch coverage [ ]
**Files**: Both processor files  
**Current**: 41.7% line, 0.0% branch each  
**Existing tests**: `commonTest/.../LocalFileProcessorTest.kt`, `commonTest/.../DriveFileProcessorTest.kt`  
**What to do**:
- Read both source files and both test files
- The 0% branch coverage is the red flag — there are conditional arms that have never been exercised
- Focus on: unsupported file type returns null/error, null metadata handling, empty file handling
- Extend both test files
- Run: `./gradlew :composeApp:jvmTest -PunitTestsOnly=true --tests "com.borinquenterrier.cef.LocalFileProcessorTest,com.borinquenterrier.cef.DriveFileProcessorTest"`

**Success**: Branch coverage ≥ 60% on each; line ≥ 70%

---

### C4: AppEnv.kt — environment branch coverage [ ]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/AppEnv.kt`  
**Current**: 43.8% line, 38.5% branch  
**Existing test**: Search `jvmTest` for `AppEnvTest` or `AppDirectoryTest` — `AppDirectoryTest.kt` exists  
**What to do**:
- Read `AppEnv.kt` — this handles build environment detection (CI vs local vs production)
- Read `AppDirectoryTest.kt` to see what's already covered
- Write `AppEnvTest.kt` in `jvmTest` if it doesn't exist, or extend `AppDirectoryTest.kt`
- Test all `when`/`if` branches for environment variants; mock or override system properties where needed
- Run: `./gradlew :composeApp:jvmTest -PunitTestsOnly=true --tests "com.borinquenterrier.cef.AppEnvTest"`

**Success**: Line coverage ≥ 80%; branch ≥ 70%

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
| B4: GeminiRetryService → 100% | [ ] | 88.5% / CRAP 28.11 | — |
| B5: GeminiRequestExecutor → 100% | [ ] | 90.2% / CRAP 27.70 | — |
| B6: GoogleCalendarSyncService → 100% | [ ] | 91.3% / CRAP 27.48 | — |
| B7: SqlDelightLocalCalendar → 100% | [ ] | 82.9% / CRAP 26.87 | — |
| B8: GeminiAIService → 100% | [ ] | 92.1% / CRAP 29.42 | — |
| C1: PollScheduler branches | [ ] | 56.3% / 25% branch | — |
| C2: HarnessSourceProcessor lines | [ ] | 50.0% | — |
| C3: LocalFile + DriveFile processors | [ ] | 41.7% / 0% branch | — |
| C4: AppEnv branches | [ ] | 43.8% | — |
| C5: GoogleConnectionState branches | [ ] | 61.5% / 0% branch | — |
