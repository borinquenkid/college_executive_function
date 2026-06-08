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

### UI Verification Protocol
For UI-related changes, verify the visual state by running the relevant module (e.g., `:composeApp:jvmRun`) and performing a screen capture (e.g., using macOS `screencapture`). Layout optimizations and visual features must be physically verified on-screen before being reported as complete.

### CRAP Remediation Protocol
When a file scores high on the CRAP index (`CRAP.md`), prefer **decomposing it into smaller, single-responsibility files before writing tests against it**. The formula (`complexity² × (1 - coverage)³ + complexity`) squares complexity, so splitting one high-complexity file into focused units shrinks the score sharply on its own — often more than coverage alone would. Testing a monolith first is a sunk cost: once it's split, those tests have to be rewritten or relocated against the new shape anyway. Decompose first, then write targeted tests against the smaller, stable units that result.

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
