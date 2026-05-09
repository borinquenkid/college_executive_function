# Project Mandates

## Build Verification Protocol
Whenever the agent reports that a task or feature is "done," it MUST verify that all three primary build targets compile successfully by running the following command: `./gradlew :composeApp:assembleDebug :iosApp:assemble :server:assemble`. The agent must confirm these three builds pass before confirming completion to the user.

## Native Dependency Management
Manual modification of Xcode project files (.pbxproj) and adding external Swift packages is strictly prohibited due to their brittleness in KMP builds. All native features MUST be implemented using platform-native APIs already available in the system frameworks, accessible via pure Kotlin/Native interop, to ensure build stability.


The application uses a hybrid AI strategy to balance performance, privacy, and accessibility.

- **Primary Engine:** **Gemini 1.5 Flash** (via REST API). This is the default engine for syllabus parsing and event extraction due to its 1-million-token context window and stability.
- **Experimental Engine:** **Llamatik** (KMP wrapper for `llama.cpp`). Maintained as an optional "Offline Mode" for on-device, privacy-first inference using GGUF models.
- **Onboarding:** Students provide their own free Gemini API key via Google AI Studio for a frictionless, private setup.

## Testing Requirements

All business logic classes (Models, Repositories, Services, and Utilities) MUST have associated unit tests using the Kotest framework. Any new business logic introduced to the codebase must be accompanied by corresponding tests.

- **Mocking:** Use `MockK` for unit tests.
- **Network Testing:** Use Ktor `MockEngine` to verify API interactions (for Sync services).

UI components should have corresponding Compose tests where appropriate to verify state changes and user interactions.

# College Executive Function

An application designed to assist students with executive function challenges by providing a structured environment to manage academic sources, generate study materials, and maintain a comprehensive academic calendar.

## Run Profiles

The application supports two distinct run profiles to manage different execution environments:

*   **`local` Profile (Runtime):**
    *   Interacts with the **real** Google Calendar via the `RemoteCalendarRepository`.
    *   Uses your local `.env` or stored OAuth tokens.
    *   Uses **Llamatik** for local AI processing.
*   **`test` Profile (Mock):**
    *   Uses a **`MockCalendarRepository`** to provide deterministic data for automated testing.
    *   Skips the network and real authentication.

## Core Architecture

The application follows a strict data flow to consolidate academic data into a single, synchronized "Source of Truth."

*   **Inputs (Sources):**
    *   **External Calendars (High Priority):** Read-only feeds from Google Calendar, Microsoft Outlook, or iCal (.ics) URLs (e.g., University Academic Calendars).
    *   **Syllabus (High Priority):** Course documents containing deadlines and deliverables parsed via AI.
    *   **Class Documents:** Supporting materials belonging to specific courses.
*   **Logic Layer (The Flow):**
    *   **`StudioFlow`:** Decoupled business logic that manages AI negotiation, extraction, and sync independent of the UI.
*   **The Object (Event):** All inputs are parsed (via AI or direct API/ICS parsing) into a unified `Event` model.
*   **The Destination:** All generated events are synchronized into the **Student's Master Calendar** (the primary calendar they use daily).

## Features

*   **Sources Panel:** Manage inputs from Local Files, URLs (Public/Private), and Google Drive.
*   **Academic Calendar:** A central, editable dashboard that aggregates and synchronizes events from all sources.
*   **AI Integration:** Performs automated analysis of Syllabi and Documents to extract events and suggest proactive study blocks using local LLMs.
*   **Synchronization Service:** Handles the push/pull logic between the app's internal state and the Student's external calendar providers.

## AI Integration (Hybrid)

The application leverages AI to perform the following key tasks:

*   **Syllabus Analysis:** Automatically identifies all deliverables (Assignments, Exams, etc.) and suggests proactive study blocks.
*   **Large Context Handling:** Uses Gemini 1.5 Flash to process entire syllabi (up to 1M tokens) in a single request for maximum accuracy.
*   **Privacy-First Setup:** Students use their own API keys, keeping their data within their own Google ecosystem.
*   **Offline Capability (Experimental):** Support for Llamatik allows for on-device inference when a network is unavailable.

## Testing Requirements

All business logic classes (Models, Repositories, Services, and Utilities) must have associated unit tests using the Kotest framework.
*   **Integration Tests:** Headless IT tests (e.g., `AiExtractionIntegrationTest`) verify full AI pipelines using real dev keys and in-memory databases.

## Development Roadmap

### Completed Tasks

*   **UI Scaffolding:** Initial UI for all three panels is complete.
*   **General Styling:** A consistent theme, including colors, typography, and borders, has been applied.
*   **File Picker:** A functional file picker has been implemented for desktop and Android.
*   **Settings Screen:** A functional settings screen for entering and saving an API key has been implemented.
*   **Unified Event Model:** Refactored the entire application to use a unified `Event` data model, which supports recurrence, different event types, and color-coding by source.
*   **Routine Management:** A complete flow for creating, viewing, and persisting a recurring weekly schedule.
*   **Calendar View:** The academic calendar now displays events from all sources, grouped by date with sticky headers.
*   **Testing Framework:** Added Kotest and wrote unit tests for data models, repositories, and serializers.
*   **iCalendar Support:** Added `ical4j` library for handling `.ics` file generation and parsing.
*   **Google Calendar REST Integration:** Fully KMP-compatible synchronization using Ktor.
*   **OAuth2 Authentication:** Implemented `GoogleAuthService` with support for local server flow (JVM) and persistent token storage.
*   **Programmatic ICal Generation:** Implemented `ICalGenerator` using modern iCal4j 4.x syntax.
*   **AI Integration (Robust):** Real Gemini implementation with auto-negotiation, persistent model caching in SQLite, and exponential backoff for rate limits.
*   **Study Plan Generation:** Specialized AI prompts to suggest proactive "STUDY_BLOCK" events leading up to major deadlines.
*   **StudioFlow Separation:** Refactored the UI to use a decoupled logic layer for headless testing and cleaner state management.
*   **Debug Logging:** Integrated platform-aware logging that writes to `debug_logs.txt` at the project root in dev mode.
*   **Automatic Schema Migrations:** Updated `DriverFactory` to automatically detect and create missing tables (like `ModelCache`).

### Next Steps

The following tasks are planned for the next phase of development:

#### Core Functionality & AI
*   **iOS Feature Parity:** [TODO] Enable and implement native File Picker and Web Picker for the iOS target (currently disabled/unavailable).
*   **Multi-Format Support:** Robust text extraction for **DOCX** and **PDF** files using native libraries for Android/iOS.
*   **AI Task Decomposition:** Full UI flow for the "Break It Down" feature to split assignments into 1-2 hour sub-tasks.
*   **Syllabus-to-Study Schedule:** Further refine the logic that suggests study periods based on weighted deliverables.

#### Calendar & Sync
*   **Client Secrets Management:** Secure injection mechanism for `client_secret.json`.
*   **Native Mobile Auth:** Native Google Sign-In SDKs for Android and iOS.
*   **Two-Way Synchronization:** Complete full bi-directional sync with Google Calendar.

#### Web & Cloud AI Strategy (Future)
*   **Option A: Client-Side Inference:** Utilize **WebGPU** (WebLLM) to run GGUF models directly in the student's browser. This maintains privacy but requires a ~5GB download and modern hardware.
*   **Option B: Server-Side Inference:** Host a private `llama.cpp` or Ollama instance to provide a headless API for the web front-end. This is faster for users but shifts privacy responsibility to the server.
*   **Option C: Hybrid Approach:** Use small 2B-3B models locally in the browser for simple tasks, and server-side models only for heavy syllabus parsing.

#### UI & UX
*   **Vertical Layout Optimization:** [TODO] Shrink the vertical layout by approximately half to ensure buttons (like 'Accept') are reachable without tabbing; currently, users must use 'Tab' to reach certain off-screen elements.
*   **Visual Progress Tracking:** Progress bars and "Time Remaining" visuals for long-term projects.
*   **Export Support:** Implement `.ics` file export for the entire generated study plan.

