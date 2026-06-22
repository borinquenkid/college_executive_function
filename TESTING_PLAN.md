# Testing Strategy Plan

## Goals
- **Floor**: 80% line coverage on `commonMain`, enforced by Kover CI gate (currently at 81.84% — gate locks in the floor)
- **Target**: 90%+ branch coverage on core domain logic (AI services, repositories, coordinators)
- **Exempt**: Compose UI composables, platform adapters, zero-line interfaces/stubs

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

### A1: Configure Kover CI gate [ ]
**Why**: Currently at 81.84% — this task locks the floor so it can never silently drop.  
**Target file**: `composeApp/build.gradle.kts`  
**What to do**:
- Read the existing Kover configuration block in `build.gradle.kts`
- Add a `koverReport { verify { bound { minValue = 80; coverageUnits = LINE } } }` rule scoped to the JVM variant
- Add a Kover class filter to exclude Compose-only files from the gate calculation. Exclude by name pattern: files ending in `Screen.kt`, `Panel.kt`, `Dialog.kt`, `View.kt`, `Header.kt`, `Content.kt`, `Item.kt`, `Prompt.kt`, `UI.kt`, `App.kt`
- Run `./gradlew :composeApp:koverVerifyJvm` to confirm the gate passes

**Success**: `koverVerifyJvm` passes with current tests; build fails if a future change drops below 80%

---

## Track B — Core Domain (ordered by CRAP score × coverage gap)

### B1: AcademicCalendar.kt — extract testable logic [ ]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/AcademicCalendar.kt`  
**Current**: 21.2% line, 20.8% branch, **CRAP 48.70 🔴** (only RED file in codebase)  
**Existing test**: None targeting this file directly  
**What to do**:
- Read `AcademicCalendar.kt` in full
- Identify all non-rendering logic: date grouping, event sorting, label generation, filter conditions, empty-state logic — anything that is pure data transformation
- Extract that logic into a plain class or object (e.g., `AcademicCalendarPresenter`)
- Write `AcademicCalendarPresenterTest.kt` in `commonTest` covering all extracted methods with invariant-based tests
- The Compose composable itself stays untested — only the extracted logic is tested
- Run: `./gradlew :composeApp:jvmTest -PunitTestsOnly=true --tests "com.borinquenterrier.cef.AcademicCalendarPresenterTest"`

**Success**: New class CRAP score < 15; at least 10 tests covering date grouping, sorting, and edge cases (empty list, single event, mixed categories)

---

### B2: AppController.kt — branch gap [ ]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/AppController.kt`  
**Current**: 75.0% line, 50.0% branch, CRAP 24.64  
**Existing test**: `jvmTest/.../AppControllerTest.kt`  
**What to do**:
- Read `AppController.kt` and `AppControllerTest.kt`
- The branch gap (50%) means half the conditional arms are untested — focus on: error paths in action handlers, guard conditions that short-circuit, state transitions that only trigger on specific input combinations
- Extend `AppControllerTest.kt` to cover each uncovered branch arm
- Run: `./gradlew :composeApp:jvmTest -PunitTestsOnly=true --tests "com.borinquenterrier.cef.AppControllerTest"`

**Success**: Branch coverage ≥ 85%; CRAP < 18

---

### B3: CalendarAgent.kt — line gap [ ]
**File**: `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/CalendarAgent.kt`  
**Current**: 74.4% line, CRAP not listed (coverage too low)  
**Existing test**: `jvmTest/.../CalendarAgentTest.kt`  
**What to do**:
- Read `CalendarAgent.kt` and `CalendarAgentTest.kt`
- Identify the 25.6% uncovered lines — typically: null/empty result handling, error propagation paths, delegation branches for remote vs local
- Extend `CalendarAgentTest.kt` with tests for those paths
- Use in-memory SQLite driver for any persistence-touching tests (pattern established in `StlccIntegrationTest`)
- Run: `./gradlew :composeApp:jvmTest -PunitTestsOnly=true --tests "com.borinquenterrier.cef.CalendarAgentTest"`

**Success**: Line coverage ≥ 90%

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

**Success**: Line coverage ≥ 95%; CRAP < 22

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

**Success**: Line coverage ≥ 96%; CRAP < 22

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

**Success**: Line coverage ≥ 96%; CRAP < 22

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

**Success**: Line coverage ≥ 92%; CRAP < 20

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
| A1: Kover CI gate | [ ] | N/A | N/A |
| B1: AcademicCalendar extract | [ ] | 21.2% / CRAP 48.70 | — |
| B2: AppController branches | [ ] | 75.0% / CRAP 24.64 | — |
| B3: CalendarAgent lines | [ ] | 74.4% | — |
| B4: GeminiRetryService branches | [ ] | 88.5% / CRAP 28.11 | — |
| B5: GeminiRequestExecutor branches | [ ] | 90.2% / CRAP 27.70 | — |
| B6: GoogleCalendarSyncService branches | [ ] | 91.3% / CRAP 27.48 | — |
| B7: SqlDelightLocalCalendar lines | [ ] | 82.9% / CRAP 26.87 | — |
| C1: PollScheduler branches | [ ] | 56.3% / 25% branch | — |
| C2: HarnessSourceProcessor lines | [ ] | 50.0% | — |
| C3: LocalFile + DriveFile processors | [ ] | 41.7% / 0% branch | — |
| C4: AppEnv branches | [ ] | 43.8% | — |
| C5: GoogleConnectionState branches | [ ] | 61.5% / 0% branch | — |
