# Project Mandates

## Build Verification Protocol
Whenever the agent reports that a task or feature is "done," it MUST verify that all three primary build targets compile successfully by running the following command: `./gradlew :composeApp:assembleDebug :iosApp:assemble :server:assemble`. The agent must confirm these three builds pass before confirming completion to the user.

## Native Dependency Management
Manual modification of Xcode project files (.pbxproj) and adding external Swift packages is strictly prohibited due to their brittleness in KMP builds. All native features MUST be implemented using platform-native APIs already available in the system frameworks, accessible via pure Kotlin/Native interop, to ensure build stability.

## AI Strategy
The application uses a high-context strategy for performance and accuracy.

- **Primary Engine:** **Gemini 1.5 Flash** (via REST API). This is the default engine for syllabus parsing and event extraction due to its 1-million-token context window and stability.
- **Onboarding:** Students provide their own free Gemini API key via Google AI Studio for a frictionless, private setup.

## Testing Requirements

All business logic classes (Models, Agents, Services, and Utilities) MUST have associated unit tests using the Kotest framework. Any new business logic introduced to the codebase must be accompanied by corresponding tests.

- **Mocking:** Use `MockK` for unit tests.
- **Network Testing:** Use Ktor `MockEngine` to verify API interactions (for Sync services).

UI components should have corresponding Compose tests where appropriate to verify state changes and user interactions.

# College Executive Function

An application designed to assist students with executive function challenges by providing a structured environment to manage academic sources, generate study materials, and maintain a comprehensive academic calendar.

## Run Profiles

The application supports two distinct run profiles to manage different execution environments:

*   **`local` Profile (Runtime):**
    *   Interacts with the **real** Google Calendar.
    *   Uses your local `.env` or stored OAuth tokens.
*   **`test` Profile (Mock):**
    *   Uses a **Mock Calendar** to provide deterministic data for automated testing.
    *   Skips the network and real authentication.

## Core Architecture

The application follows a strict data flow to consolidate academic data into a single, synchronized "Source of Truth."

*   **Inputs (Sources):**
    *   **External Calendars:** Read-only feeds from Google Calendar, Microsoft Outlook, or iCal (.ics) URLs.
    *   **Syllabus:** Course documents containing deadlines and deliverables.
    *   **Class Documents:** Supporting materials belonging to specific courses.
*   **Logic Layer (The Agents):**
    *   **`IngestionAgent`:** Manages intelligent extraction and structuring of raw content into high-fidelity `SourceFragments`.
    *   **`EventAgent`:** Consumes structured content to generate both direct Deliverables and proactive Study Plans using high-context reasoning.
    *   **`NormalizationService`:** Provides programmatic safeguards to standardize event categories and deduplicate entries.
    *   **`CalendarAgent`:** Intelligent gateway to the student's schedule, managing synchronization and conflict resolution across providers.
*   **The Object (Event):** All inputs are parsed into a unified `Event` model.
*   **The Destination:** All generated events are synchronized into the **Student's Master Calendar**.

## Features

*   **Sources Panel:** Manage inputs from Local Files, URLs (Public/Private), and Google Drive.
*   **Academic Calendar:** A central, editable dashboard that aggregates and synchronizes events from all sources.
*   **AI Integration:** Performs automated analysis of Syllabi and Documents to extract events and suggest proactive study blocks.
*   **Synchronization:** Handles the push/pull logic between the app's internal state and external calendar providers.

## AI Integration

The application leverages intelligent analysis to perform the following key tasks:

*   **Syllabus Analysis:** Automatically identifies all deliverables (Assignments, Exams, etc.) and suggests proactive study blocks.
*   **Large Context Handling:** Uses high-capacity engines to process entire syllabi in a single request for maximum accuracy.
*   **Privacy-First Setup:** Students use their own API keys, keeping their data within their own Google ecosystem.

## Testing Requirements

All business logic classes (Models, Agents, Services, and Utilities) must have associated unit tests using the Kotest framework.
*   **Integration Tests:** Headless IT tests verify full analysis pipelines using real dev keys and in-memory databases.

## Development Roadmap

### Completed Tasks

*   **UI Scaffolding:** Initial UI for all three panels is complete.
*   **General Styling:** A consistent theme, including colors, typography, and borders, has been applied.
*   **File Picker:** A functional file picker has been implemented for desktop and Android.
*   **Settings Screen:** A functional settings screen for entering and saving an API key has been implemented.
*   **Unified Event Model:** Refactored the entire application to use a unified `Event` data model.
*   **Routine Management:** A complete flow for creating, viewing, and persisting a recurring weekly schedule.
*   **Calendar View:** The academic calendar now displays events from all sources, grouped by date.
*   **Testing Framework:** Added Kotest and wrote unit tests for data models, repositories, and serializers.
*   **iCalendar Support:** Added handling for `.ics` file generation and parsing.
*   **Google Calendar REST Integration:** Fully KMP-compatible synchronization using Ktor.
*   **OAuth2 Authentication:** Implemented support for local server flow (JVM) and persistent token storage.
*   **AI Integration (Robust):** Real intelligence implementation with auto-negotiation, persistent model caching in SQLite.
*   **Agentic Separation:** Refactored the UI to use a decoupled logic layer for headless testing and cleaner state management.
*   **Debug Logging:** Integrated platform-aware logging.
*   **Automatic Schema Migrations:** Updated database factory to automatically detect and create missing tables.

### Next Steps

The following tasks are planned for the next phase of development:

#### Core Refactoring (Agentic Transition)
*   **Refactor Logic Layer:** [TODO] Rename and refactor core components to align with the agentic architecture:
    *   `SourceFlow` ➔ **`IngestionAgent`**
    *   `StudioFlow` ➔ **`EventAgent`**
    *   `UnifiedCalendarRepository` ➔ **`CalendarAgent`**
    *   `KeywordEventExtractor` ➔ **`NormalizationService`**
    *   `SourcePart` ➔ **`SourceFragment`**

#### Core Functionality & AI
*   **iOS Feature Parity:** [TODO] Enable and implement native File Picker and Web Picker for the iOS target (currently disabled/unavailable).
*   **Multi-Format Support:** Robust text extraction for **DOCX** and **PDF** files using native libraries for Android/iOS.
*   **AI Task Decomposition:** Full UI flow for the "Break It Down" feature to split assignments into 1-2 hour sub-tasks.
*   **Syllabus-to-Study Schedule:** Further refine the logic that suggests study periods based on weighted deliverables.

#### Calendar & Sync
*   **Client Secrets Management:** Secure injection mechanism for `client_secret.json`.
*   **Native Mobile Auth:** Native Google Sign-In SDKs for Android and iOS.
*   **Two-Way Synchronization:** Complete full bi-directional sync with Google Calendar.

#### UI & UX
*   **Vertical Layout Optimization:** [TODO] Shrink the vertical layout by approximately half to ensure buttons (like 'Accept') are reachable without tabbing.
*   **Visual Progress Tracking:** Progress bars and "Time Remaining" visuals for long-term projects.
*   **Export Support:** Implement `.ics` file export for the entire generated study plan.
