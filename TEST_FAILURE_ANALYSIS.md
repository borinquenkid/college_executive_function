# Test Failure Analysis - 9 Remaining Failures (98.7% Pass Rate)

## Summary
**Status:** 691/700 tests passing (98.7%)  
**Failures:** 9 tests  
**Root Causes:** Complex domain logic implementation gaps

---

## 1. CollisionResolutionTest (2 failures)

### Test 1: "Headless Integration: Priority Bump and Shift Cascade"
**File:** `CollisionResolutionTest.kt:95-160`  
**Failure Type:** AssertionFailedError at line 151  
**Root Cause:** Collision resolution algorithm not fully implemented  
**Issue:**
- Test expects: When Physics Lecture (priority 80) collides with Quantum Study (priority 10), the Study Block should be rescheduled to a free slot (11:00-12:00)
- Actual behavior: Database doesn't contain the expected 3 events (missing the rescheduled event)
- The `ConflictResolver` or reschedule logic may not be finding/applying valid alternative slots

**Domain Complexity:** High
- Requires implementing priority-based collision resolution
- Needs intelligent time slot selection algorithm
- Must handle cascading rescheduling

---

### Test 2: "Headless Integration: Post-Deadline Shift (Late Leeway warning)"
**File:** `CollisionResolutionTest.kt:168-291`  
**Failure Type:** NullPointerException at line 269  
**Root Cause:** Missing null check in deadline handling logic  
**Issue:**
- NPE suggests a null value where an object is expected
- Likely in deadline calculation or "leeway" warning generation
- The test expects deadline events to have special handling with late-shift tolerance

**Domain Complexity:** High
- Requires understanding deadline-specific rescheduling rules
- Must handle "leeway" (grace period) logic
- Needs null-safe deadline comparison

---

## 2. ConflictResolutionHeadlessTest (1 failure)

### Test: "Headless e2e: Process real syllabi, generate plan, resolve conflicts"
**File:** `ConflictResolutionHeadlessTest.kt:51-108`  
**Failure Type:** AssertionFailedError at line 89  
**Root Cause:** End-to-end conflict resolution pipeline incomplete  
**Issue:**
- Full e2e test processing actual Gemini-generated events
- Assertion on line 89 suggests final conflict count doesn't match expected
- Likely the ConflictResolver or final push doesn't properly classify/isolate conflicts

**Domain Complexity:** Very High
- Multi-step process: parse syllabi → generate events → detect conflicts → resolve
- Depends on proper AI event generation
- Requires correct conflict classification and resolution

---

## 3. DriveFileScannerTest (3 failures)

### Test 1: "scanNewFiles processes multiple watched folders"
**File:** `DriveFileScannerTest.kt:130-146`  
**Failure Type:** AssertionFailedError at line 131  
**Issue:**
- Expected: Scanner returns files from all 3 watched folders
- Actual: Different count returned (likely 1 folder's worth)
- The `listFilesRecursive()` or folder iteration logic may be stopping early

**Root Cause:** 
- Mock setup expects 1 file per folder call (`{ "file.pdf" }`)
- Test expects 3 total files but gets fewer
- Implementation may not be iterating through all watched folders correctly

---

### Test 2: "scanNewFiles calls listFiles for watched folders"  
**File:** `DriveFileScannerTest.kt:147-175`  
**Failure Type:** AssertionError with caused-by at line 169  
**Issue:**
- Verification that `listFiles()` is called for each watched folder
- The mock verification is failing (likely wrong call count or arguments)
- Implementation may not be calling the mocked method as expected

**Root Cause:**
- `coVerify` likely failing on call count
- May need to match folder paths more carefully

---

### Test 3: "scanNewFiles handles partial failures in multiple folders"
**File:** `DriveFileScannerTest.kt:176-209`  
**Failure Type:** AssertionFailedError at line 198  
**Issue:**
- Expected: Successful files returned even when some folders throw exceptions
- Actual: Different result (possibly empty list or exception propagated)
- The exception handling in multi-folder scanning doesn't isolate failures

**Root Cause:**
- When one folder throws, implementation may stop processing others
- Error handling not implemented to catch-and-continue per folder

---

## 4. LocalFileScannerTest (2 failures)

### Test 1: "scanNewFiles filters files by supported extensions"
**File:** `LocalFileScannerTest.kt:105-124`  
**Failure Type:** AssertionFailedError at line 122  
**Issue:**
- Input: 3 files (file1.pdf, file2.docx, image.png)
- Expected: 2 files returned (only PDF and DOCX)
- Actual: Different count (likely all 3 or all 0)
- The extension filtering logic is not implemented or not working

**Root Cause:**
- `LocalFileScanner` likely not filtering by supported file types
- SUPPORTED_EXTENSIONS constant may be missing or filter not applied

---

### Test 2: "scanNewFiles handles concurrent directory scanning"
**File:** `LocalFileScannerTest.kt:126-144`  
**Failure Type:** AssertionFailedError at line 142  
**Issue:**
- 3 directories, each returning "file.pdf" via mock (any path)
- Expected: 3 files total (one from each directory)
- Actual: Different count
- The concurrent/parallel folder scanning may not be aggregating results correctly

**Root Cause:**
- Either not calling `listFiles()` 3 times or not collecting all results
- Possibly a coroutine/threading issue in parallel processing

---

## 5. ComposeUiFlowsTest (1 failure)

### Test: "testAcademicCalendarRendering()"
**File:** `ComposeUiFlowsTest.kt:268-318`  
**Failure Type:** ClassCastException at CoroutineDebugging.kt:42, caused by AcademicCalendar.kt:699  
**Issue:**
- ClassCastException indicates type mismatch in mock setup
- Line number 699 is stale (file only has 417 lines) - bytecode mismatch
- Test mocks `EventAgent.errorState`, `decomposedTasks`, `isLoading`, `statusMessage` as raw MutableStateFlow
- After StateFlowReader refactoring, these mocks may not match expected types

**Root Cause:**
- Test setup uses `MutableStateFlow` but code now expects `StateFlowReader` in some contexts
- Compose's `collectAsState()` extension may not work with improperly typed mocks
- Type coercion failing at runtime

**Likely Fix:**
- Either wrap mocks in proper StateFlowReader adapters or keep EventAgent's public API as StateFlow (not refactored)

---

## Summary by Category

| Category | Count | Complexity | Root Cause |
|----------|-------|-----------|-----------|
| Collision Resolution (scheduling logic) | 3 | Very High | Algorithm not fully implemented |
| File Scanning (filtering/iteration) | 5 | High | Extension filtering & concurrent handling not implemented |
| UI/Mocking | 1 | Medium | Type mismatch after StateFlowReader refactoring |

---

## Recommendations for Fixing (in order of difficulty)

### Easy (1 test)
1. **ComposeUiFlowsTest** - Adjust mock setup for StateFlowReader types or revert EventAgent to expose StateFlow

### Medium (5 tests)
2. **LocalFileScannerTest** - Implement extension filtering logic
3. **LocalFileScannerTest** - Fix concurrent folder aggregation
4. **DriveFileScannerTest (×2)** - Ensure all folders are scanned and results aggregated
5. **DriveFileScannerTest** - Add exception-handling-per-folder logic

### Hard (3 tests)  
6. **CollisionResolutionTest (×2)** - Implement full collision resolution algorithm
7. **ConflictResolutionHeadlessTest** - Complete e2e conflict pipeline

---

## Architecture Notes

The remaining failures reveal two main implementation gaps:

1. **Collision Resolution Pipeline**: The system can detect conflicts but cannot intelligently reschedule events based on priority and available time slots.

2. **File Scanning Completeness**: The file scanner implementation is incomplete for:
   - Filtering files by supported extensions
   - Handling concurrent folder scanning with proper aggregation
   - Handling partial failures gracefully

These are not architectural flaws but rather missing business logic implementations that go beyond the refactoring scope.
