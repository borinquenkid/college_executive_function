# Execution Plan: Transitioning to an L4 Agentic Application (Phase 5)

This document outlines the phased execution plan to transition the College Executive Function (CEF) codebase from an L3 pipeline to an L4 Autonomous Agentic System. 

---

## 🗺️ Prioritized Phase Breakdown

### 🛑 [P0 - High Priority] Phase 5.1: Graph-Based Cycle Detection (`CriticActorAIService.kt`) ✅ **COMPLETED**
*   **Goal:** Enable iterative self-critique that runs until convergence or oscillation is detected.
*   **Execution Steps:**
    1. Introduce a `visitedStates: MutableSet<String>` state transition graph tracker inside the refinement loops of `generateCalendarEvents`, `generateStudyPlan`, and `decomposeTask`.
    2. At each iteration, check if the serialized output JSON matches any entry in `visitedStates`:
        - **Convergence (Cycle of length 1):** Terminate the loop and return.
        - **Oscillation (Cycle of length >= 2):** Log cycle warning, terminate the loop, and return the last state.
    3. Retain a safety iteration limit of `maxIterations = 3` as a guardrail.
    4. Implement `areTaskListsDifferent` to verify changes in task decomposition.
    5. Update unit tests in `CriticActorAIServiceTest.kt` to verify convergence, oscillation detection, and iteration limit guardrails.

### 🛑 [P0 - High Priority] Phase 5.2: Active Lifecycle Agent Harness (`AgentHarness.kt`) ✅ **COMPLETED**
*   **Goal:** Implement a background harness loop that polls Google Drive and local directories at startup and once daily.
*   **Execution Steps:**
    1. Create `AgentHarness.kt` under `commonMain`.
    2. Add polling logic to check for new files in GDrive folders and local watched directories.
    3. Process files **sequentially (one by one)** down the pipeline: Ingestion -> Deliverables -> Context analysis.
    4. Execute two-way calendar synchronization (`CalendarAgent.synchronize()`) once at the end of the loop.
    5. Persist the last execution timestamp as `cef_harness_last_poll_time` in Settings.
    6. Wire the startup trigger and a daily periodic timer using Kotlin Coroutines in the main application lifecycle.
    7. Write unit tests in `AgentHarnessTest.kt` verifying sequential processing and 24-hour interval logic.

### 🛑 [P0 - High Priority] Phase 5.3: Structural Refactoring — SourceRepository Abstraction ✅ **COMPLETED**
*   **Goal:** Eliminate direct raw database access inside `IngestionAgent` and `ContextAgent`, and shift database execution to background threads.
*   **Execution Steps:**
    1. Define a `SourceRepository` interface in `commonMain` containing:
       - `saveSource(sourceItem, originUri)`
       - `updateSourceMetadata(sourceId, metadata)`
       - `getSourceMetadata(sourceId)`
       - `getAllSources()`
       - `getSourceById(sourceId)`
       - `getFragmentsForSource(sourceId)`
    2. Implement `SqlDelightSourceRepository` using `AppDatabase`, wrapping all DB executions in `withContext(Dispatchers.Default)`.
    3. Refactor `IngestionAgent`, `ContextAgent`, and `AgentHarness` to take `SourceRepository` instead of `AppDatabase`.
    4. Register and inject `SqlDelightSourceRepository` in `DependencyContainer`.
    5. Write unit tests for `SqlDelightSourceRepository` in `SourceRepositoryTest.kt`.

### 🛑 [P0 - High Priority] Phase 5.4: Structural Refactoring — Off-Thread Settings Serialization ✅ **COMPLETED**
*   **Goal:** Prevent synchronous, thread-blocking serialization of settings data.
*   **Execution Steps:**
    1. Update `PreferencesRepository` and `RoutineRepository` retrieve/save methods to `suspend` functions.
    2. Shift settings reading/writing inside those repositories to `withContext(Dispatchers.Default)`.
    3. Update calling locations (UI, dialogs, controllers) to launch these requests inside coroutine scopes.

### 🛑 [P0 - High Priority] Phase 5.5: Structural Refactoring — Concurrent Directory Scans ✅ **COMPLETED**
*   **Goal:** Improve harness initialization performance by scanning multiple sources in parallel.
*   **Execution Steps:**
    1. Refactor `AgentHarness.runHarness` to list watched local files and GDrive files concurrently using coroutines `async`/`await`.
    2. Keep sequential (one by one) execution of the downstream AI pipeline to preserve model rate-limits.

### ⚠️ [P1 - Medium Priority] Phase 5.6: Startup Check-In Interview Loop ✅ **COMPLETED**
*   **Goal:** Prompt the student at startup/daily trigger to resolve missed/incomplete study blocks and sub-tasks.
*   **Execution Steps:**
    1. Implement a database query in `EventAgent` to fetch incomplete events/tasks with a due date prior to today.
    2. Design and implement a check-in dialog (`CheckInDialog` or UI panel) that presents these missed items to the user.
    3. Allow the user to mark items as:
        - **Completed:** Update DB status and trigger sync.
        - **Reschedule:** Trigger `CollisionResolver` to shift the task and its dependencies to new valid study hours.
        - **Skip:** Mark task as skipped to clear it from the check-in list.
    4. Integrate this check-in flow directly into the `AgentHarness` trigger sequence immediately after the files have been polled.

### 📉 [P2 - Low Priority] Phase 5.7: Stateful User Preference Memory ✅ **COMPLETED**
*   **Goal:** Learn implicit user scheduling constraints by tracking manual modifications and injecting them into future AI prompts.
*   **Execution Steps:**
    1. Create a `UserPreferenceMemoryRepository` to store user calendar edits (e.g., if a user consistently deletes study blocks on Friday evenings, log this pattern).
    2. Derive implicit constraints from these edit logs (e.g., "Friday evening is a negative constraint").
    3. Inject these derived constraints as explicit rules in the system prompt in `AiPrompts.getStudyPlanPrompt()`.

### 📉 [P2 - Low Priority] Phase 5.8: Sync Re-negotiation UI ✅ **COMPLETED**
*   **Goal:** Replace silent collision resolution during two-way sync with interactive user proposals.
*   **Execution Steps:**
    1. Update the synchronization loop to detect when a remote update creates conflicts or shifts local study blocks.
    2. Instead of resolving conflicts silently, compile a "Schedule Diff" proposal list.
    3. Present the proposal visually to the user: *"Your midterm was moved to Monday, so I propose shifting your weekend study blocks to Sunday afternoon. [Accept Proposal] [Adjust manually]"*.
