# College Executive Function

An application designed to assist students with executive function challenges by providing a structured environment to manage academic sources, generate study materials, and maintain a comprehensive academic calendar.

---

## Agent Mandates

### Build Verification Protocol
Whenever a task or feature is reported as "done" (except when specifically running unit tests or CRAP index checks), verify that all three primary build targets compile successfully:
```bash
./gradlew :composeApp:assembleDebug :iosApp:assemble :server:assemble
```
Confirm these three builds pass before confirming completion.

### CRAP Index Verification Protocol
To regenerate and verify the CRAP index scores in `CRAP.md` after code changes or test additions:
```bash
# Step 1: Run all JVM tests to generate fresh coverage data
./gradlew :composeApp:jvmTest --no-daemon

# Step 2: Generate Kover code coverage XML report (JVM variant — Android variant needs a device)
./gradlew :composeApp:koverXmlReportJvm -q

# Step 3: Generate CRAP.md from coverage data
./gradlew generateCrapReport -q
```
All three commands must complete successfully. The updated `CRAP.md` will reflect current complexity and test coverage metrics. Commit the regenerated `CRAP.md` as part of the phase completion.

### UI Verification Protocol
For UI-related changes, verify the visual state by running the relevant module (e.g., `:composeApp:jvmRun`) and performing a screen capture (e.g., using macOS `screencapture`). Layout optimizations and visual features must be physically verified on-screen before being reported as complete.

### CRAP Remediation Protocol
When a file scores high on the CRAP index (`CRAP.md`), prefer **decomposing it into smaller, single-responsibility files before writing tests against it**. The formula (`complexity² × (1 - coverage)³ + complexity`) squares complexity, so splitting one high-complexity file into focused units shrinks the score sharply on its own — often more than coverage alone would. Testing a monolith first is a sunk cost: once it's split, those tests have to be rewritten or relocated against the new shape anyway. Decompose first, then write targeted tests against the smaller, stable units that result.

### Reactive State Testability Pattern: StateFlowReader/StateFlowWriter

**Foundational Principle:** If you can't test it, you can't ship it. Raw reactive types (StateFlow, Flow) block testing just like raw IOStreams do in Java.

**Mandatory Pattern for all StateFlow usage:**

Never expose `StateFlow<T>` directly. Always wrap in reader/writer interfaces:

```kotlin
// Read-only interface for observers
interface StateFlowReader<out T> {
    val value: T
    suspend fun collect(collector: suspend (T) -> Unit)
}

// Write-only interface for owners
interface StateFlowWriter<in T> {
    fun setValue(value: T)
}

// Private implementation (not exposed)
class MutableStateFlowWrapper<T>(initialValue: T) : StateFlowReader<T>, StateFlowWriter<T> {
    private val _flow = MutableStateFlow(initialValue)
    override val value: T get() = _flow.value
    override suspend fun collect(collector: suspend (T) -> Unit) = _flow.collect(collector)
    fun setValue(newValue: T) { _flow.value = newValue }
}
```

**Application Rules:**
1. Never expose `StateFlow<T>` as a public property. Always use `StateFlowReader<T>` or `StateFlowWriter<T>`.
2. Components that read state receive `StateFlowReader<T>` only (e.g., `AppController` gets `sourceItems: StateFlowReader<List<SourceItem>>`)
3. Components that write state receive `StateFlowWriter<T>` only (e.g., `SourceManager` gets write access internally)
4. Tests can mock readers/writers independently without requiring real implementations
5. This enforces **Principle of Least Privilege** at the type level

**Why:** Exposing raw StateFlow forces tests to either:
- Mock the entire reactive lifecycle (fragile, error-prone) → forces using real implementations → untestable architecture
- Use the real implementation (defeats unit test isolation)

Wrapping separates concerns and makes mocking trivial:
```kotlin
// Test setup becomes simple
val mockSourceItems = mockk<StateFlowReader<List<SourceItem>>>()
every { mockContainer.sourceItems } returns mockSourceItems
```

### Complexity & Decomposition Standards
Prevent high-CRAP code by designing for decomposition and testability **from the spec phase**, not by discovering problems during metrics review. Architectural requirements must enforce these limits:

1. **Per-Method Complexity Limit: 5**
   - Any public method exceeding 5 control flows (branches, loops, logical operators, safe calls, collection operators) must be refactored into smaller focused methods or delegated to extracted services.
   - Example: `GoogleRemoteCalendarRepository.clearCalendar()` had 6 branches → extracted loop logic to `EventRangeFilter`.

2. **Per-File Complexity Limit: 15**
   - No file shall exceed 15 total complexity across all methods. Files nearing this limit are candidates for service extraction.
   - Example: `GoogleRemoteCalendarRepository` at 30 → decomposed into three services (complexity 2–5 each).

3. **Thin Facade Pattern as Architectural Requirement**
   - Any class coordinating 3+ distinct responsibilities must delegate to separate, focused services rather than handle them inline.
   - Require explicit dependency injection of extracted services in constructors.
   - Example: Instead of "GoogleRemoteCalendarRepository handles calendar operations," spec: "GoogleRemoteCalendarRepository is a thin facade delegating to CalendarIdResolver, EventConflictDetector, and EventRangeFilter."

4. **Testability-First Requirements**
   - Every public method must be independently testable with focused unit tests (1–3 assertions, clear mocks).
   - Methods with 6+ control flows are flagged as "not testable cleanly" and must be split before merging.
   - Each extracted service must have its own unit test file with 5+ test cases covering happy paths, edge cases, and error conditions.

5. **Service Extraction Rules**
   - Extract a service when a method has 3+ distinct responsibilities (e.g., ID resolution + conflict detection + filtering).
   - Extracted services must be single-responsibility: handle exactly one concern (e.g., "detect overlaps," "filter by date," "resolve calendar ID").
   - Use dependency injection to wire extracted services; document the dependency graph in class comments.

6. **Coverage-by-Design Targets**
   - Business logic services (not UI, not Compose) target **100% line coverage** to catch missing branches early.
   - No file shall have coverage below the threshold determined at spec time (e.g., "no business logic below 80% coverage").
   - Coverage gaps are blockers: code with 0% coverage cannot be merged, even if "it's new."

7. **CRAP Index as Non-Functional Requirement**
   - Include CRAP acceptance criteria in the spec: "No file shall exceed CRAP index of 20."
   - Files exceeding the threshold at PR time are rejected; author must decompose before re-review.
   - Use `generateCrapReport` as part of CI/CD validation, not post-hoc analysis.

**Pattern:** The three-service structure that emerged in Phase 0.23 (`CalendarIdResolver`, `EventConflictDetector`, `EventRangeFilter`) is what should have been in the original spec — not derived through refactoring. Spec decomposition prevents CRAP; metrics review only measures what you missed.

### Confabulation Gate Protocol

Every method on `AIService` that returns **structured output** (events, metadata, tasks, or any typed result persisted or displayed to the user) **must have explicit gate coverage** in `GroundingGuardAIService`. Delegation-by-default through the `by delegate` interface is not a gate — it is the absence of one.

**Gate levels (apply the strictest level that the output type supports):**

1. **Year-level grounding** — required for all event-producing methods (`generateCalendarEvents`, `generateStudyPlan`). After extraction, filter events to **years explicitly mentioned in the source text**. An event dated 2099 from a source that only mentions 2026 is a confabulation and is dropped. Students may load syllabi from any semester (past, current, or future) and all events grounded in the source's years are kept. Do not filter by today's date — a Fall syllabus loaded in June should return Fall events.

2. **Source-fact grounding** — required for free-text output that will be persisted or injected into chat context (`analyzeDocument`, `generateChatResponse`). A gate here only has value when the loaded corpus contains concrete factual anchors — specific due dates, assignment weights, course policies. Without those anchors there is no ground truth to check against, and the gate would be checking form, not fact. Apply this level once students are loading syllabi with structured deadlines.

3. **Critic-only** — acceptable for generative outputs where grounding to source dates is not meaningful (`decomposeTask`, `categorizeSource`). The Critic-Actor loop is still required.

**Checklist for any new `AIService` method:**
- [ ] Method is overridden in `GroundingGuardAIService` (not delegated silently)
- [ ] Gate level is explicitly chosen and justified in a comment
- [ ] Unit test covers the gate: a confabulated value that should be dropped is dropped

**Known gaps and their readiness conditions:**

1. ~~`generateCalendarEvents` / `generateStudyPlan` — year-level grounding~~ ✅ Closed. Events whose year does not appear in the source text are dropped. Semester-level filtering was attempted but reverted: filtering by today's date is wrong because students load past and future syllabi intentionally.

2. ~~`analyzeDocument` / `generateChatResponse` — defer until the corpus has factual anchors.~~ ✅ Closed via `SourceFactGrounder`. Gate extracts date and grade-weight claims from free-text AI output and cross-checks each against the source text provided to the model. Ungrounded claims trigger a structured warning appended to the response; the response is not dropped (sentence-level surgery is out of scope). Corpus: 13 real UT Austin Fall 2025 syllabi in `contributions/tx/ut_austin/2025-2026/fall/` provided the factual-anchor readiness condition.

### Native Dependency Management
Manual modification of Xcode project files (`.pbxproj`) and adding external Swift packages is strictly prohibited due to their brittleness in KMP builds. All native features MUST be implemented using platform-native APIs already available in the system frameworks, accessible via pure Kotlin/Native interop, to ensure build stability.

### Codebase Intelligence (MCP)
This project uses `repowise` as an MCP server for codebase intelligence (docs, graph, git signals). Install it with:
```bash
uv tool install repowise
```
The MCP server is configured in `.gemini/settings.json` to start automatically.

---

## Run Profiles

The application supports two distinct run profiles to manage different execution environments:

*   **`local` Profile (Runtime):**
    *   Interacts with the **real** Google Calendar.
    *   Uses your local `.env` or stored OAuth tokens.
*   **`test` Profile (Mock):**
    *   Uses a **Mock Calendar** to provide deterministic data for automated testing.
    *   Skips the network and real authentication.

---

## Core Architecture

The application follows a strict data flow to consolidate academic data into a single, synchronized "Source of Truth."

*   **Inputs (Sources):**
    *   **External Calendars:** Read-only feeds from Google Calendar, Microsoft Outlook, or iCal (.ics) URLs.
    *   **Syllabus:** Course documents containing deadlines and deliverables parsed via AI.
    *   **Class Documents:** Supporting materials belonging to specific courses.
*   **Logic Layer (The Agents):**
    *   **`IngestionAgent`:** Manages intelligent extraction and structuring of raw content into high-fidelity `SourceFragments`.
    *   **`EventAgent`:** Consumes structured content to generate both direct Deliverables and proactive Study Plans using high-context reasoning.
    *   **`NormalizationService`:** Provides programmatic safeguards to standardize event categories and deduplicate entries.
    *   **`CalendarAgent`:** Intelligent gateway to the student's schedule, managing synchronization and conflict resolution across providers.
*   **The Object (Event):** All inputs are parsed into a unified `Event` model.
*   **The Destination:** All generated events are synchronized into the **Student's Master Calendar**.

---

## Features

*   **Sources Panel:** Manage inputs from Local Files, URLs (Public/Private), and Google Drive.
*   **Academic Calendar:** A central, editable dashboard that aggregates and synchronizes events from all sources.
*   **AI Integration:** Performs automated analysis of Syllabi and Documents to extract events and suggest proactive study blocks.
*   **Synchronization:** Handles the push/pull logic between the app's internal state and external calendar providers.

---

## AI Strategy

*   **Primary Engine:** **Gemini 1.5 Flash** (via REST API) — default engine for syllabus parsing and event extraction due to its 1-million-token context window and stability.
*   **Model Auto-Negotiation:** `ModelManager` caches the best available model in SQLite and retries on quota exhaustion with a fallback preference list.
*   **Privacy-First Setup:** Students provide their own free Gemini API key via Google AI Studio, keeping data within their own Google ecosystem.

---

## Testing Requirements

All business logic classes (Models, Agents, Services, and Utilities) MUST have associated unit tests using the Kotest framework. Any new business logic introduced must be accompanied by corresponding tests.

*   **Mocking:** Use `MockK` for unit tests.
*   **Network Testing:** Use Ktor `MockEngine` to verify API interactions (for Sync services).
*   **Integration Tests:** Headless IT tests verify full analysis pipelines using real dev keys and in-memory databases.
*   **UI Tests:** Compose tests for key screens and dialogs verifying state changes and user interactions.

### Integration Test Naming Convention

Any test class that makes real external calls (Gemini API, Google Calendar, live databases) **must** include `IntegrationTest` in its class name (e.g. `StlccIntegrationTest`, `CalendarSyncIntegrationTest`). This is the project's equivalent of the JUnit `*IT` suffix convention.

**Why naming, not tags:** Kotest 6's tag-filtering system property (`kotest.filter.tags`) is not reliably forwarded to the test JVM when Gradle reuses a daemon. Both `tags(Flaky)` in the spec body and `@Tags("Flaky")` class annotations were tried and failed to skip tests in practice.

**Integration tests are excluded by default.** Plain `./gradlew :composeApp:jvmTest` runs unit tests only.

**To include AI/integration tests** (CI or explicit manual run):
```bash
./gradlew :composeApp:jvmTest -PrunAITests=true
```

**Running a single integration test class:**
```bash
./gradlew :composeApp:jvmTest --tests "com.borinquenterrier.cef.StlccIntegrationTest"
```

Never use Kotest tags (`object Flaky : Tag()`, `tags(Flaky)`, `@io.kotest.core.annotation.Tags`) to gate integration tests in this project.

---

## Development Roadmap

> See [ROADMAP.md](ROADMAP.md) for the full prioritized plan with dependency graph and implementation details.

### Completed

*   UI Scaffolding, General Styling, File Picker (Desktop, Android, iOS)
*   Settings Screen (API key, Google auth, drive settings)
*   Unified Event Model (`TimeEvent`, `DayEvent`, `SyncStatus`, `AcademicCategory`)
*   Routine Management — full create/view/persist recurring schedule
*   Calendar View — events from all sources, grouped by date
*   Testing Framework — Kotest, MockK, ~33 test files (unit + integration)
*   iCalendar Parsing — `.ics` → `SourceFragments` via `IcsCalendarSource`
*   Google Calendar REST Integration — fully KMP-compatible sync via Ktor
*   OAuth2 Authentication — JVM local-server flow + persistent token storage
*   AI Integration — Gemini REST, model auto-negotiation, SQLite model cache
*   Agentic Architecture — `IngestionAgent`, `EventAgent`, `CalendarAgent`, `NormalizationService`, `ContextAgent`
*   Multi-Format Extraction — PDF/DOCX native on Android, iOS, JVM
*   Native Mobile Auth — `GoogleSignInClient` (Android), `ASWebAuthenticationSession` (iOS)
*   AI Study Plan Constraints — 9–21 hr limits, lunch/dinner breaks, collision resolution
*   Debug Logging, Automatic Schema Migrations
*   Recursive Task Decomposition — `DecompositionOrchestrator` (depth-3 FIFO), full Kotest specs
*   Automatic Source Categorization — `IngestionAgent` calls `categorizeSource()` on all non-ICS content
*   "Break It Down" UI — `TaskDecompositionDialog` wired end-to-end for DEADLINE/FINALS events
*   Two-Way Sync — all four mutation scenarios verified by `CalendarSyncIntegrationTest`
*   Multi-Source Chat Context — `ContextAgent.queryAllSources()`, conversation history, scope toggle
*   Critic-Actor Loop — `CriticActorAIService` with graph-based cycle/oscillation detection
*   .ics Export — `ICalGenerator` + expect/actual `writeIcsFile` (Downloads on JVM/Android, Share Sheet on iOS)
*   Sync Hardening — token refresh on 401, `pageToken` pagination, conflict resolution warnings
*   Visual Progress Tracking — `timeUntilDue`, `studyProgress`, countdown chips, Semester Health card
*   Scheduling Fine-Tuning — user-configurable study hours/breaks in Settings, injected into AI and resolver
*   Weighted Deliverables — grade weights extracted by AI, stored in `Event`, used for proportional study block allocation
*   Observability & Telemetry — `TelemetryManager` logging rate limits, parse exceptions, Critic-Actor outcomes
*   Client Secrets Management — automated build-time injection via custom Gradle task
*   Stateful User Preference Memory — track manual edits, derive implicit constraints, inject as prompt rules
*   Sync Re-negotiation UI — interactive proposal diff dialog replacing silent conflict resolution
*   Active Lifecycle Agent Harness — `AgentHarness` polling at startup and once daily
*   Token-Efficient Source Processing — analysis cache, mutex sequential queue, and global hold strategy
*   Custom Google Calendar Selection UI — fetch available calendars, save selection to preferences, and target chosen calendar during sync
*   Stale OAuth Connection Resolution — automatically detect invalid refresh tokens at startup in GoogleAccountFlow and transition cleanly to Unlinked
