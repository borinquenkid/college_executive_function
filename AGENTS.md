# College Executive Function

An application designed to assist students with executive function challenges by providing a structured environment to manage academic sources, generate study materials, and maintain a comprehensive academic calendar.

## Run Profiles

The application supports two distinct run profiles to manage different execution environments:

*   **`local` Profile (Runtime):** 
    *   Interacts with the **real** Google Calendar via the `RemoteCalendarRepository`.
    *   Uses your local `.env` or stored OAuth tokens.
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
*   **AI Integration:** Performs automated analysis of Syllabi and Documents to extract events and suggest proactive study blocks.
*   **Synchronization Service:** Handles the push/pull logic between the app's internal state and the Student's external calendar providers.

## AI Integration

The application leverages a generative AI model to perform the following key tasks:

*   **Syllabus Analysis:** Automatically identifies all deliverables (Assignments, Exams, etc.) and suggests proactive study blocks.
*   **Persistent Negotiation:** Intelligent model selection that prefers high-capability models (like Gemini 1.5 Flash) and caches the successful choice in SQLite to avoid redundant dialogs.
*   **Exhaustion Resilience:** Implements a 1-hour blacklist for models that return 429 (Rate Limit) or 503 (Overloaded) errors.

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
*   **AI Integration (Local):** Migrated from Gemini to **Llamatik** for privacy-first, on-device syllabus analysis using local GGUF models.
*   **Study Plan Generation:** Specialized AI prompts to suggest proactive "STUDY_BLOCK" events leading up to major deadlines.
*   **StudioFlow Separation:** Refactored the UI to use a decoupled logic layer for headless testing and cleaner state management.
*   **Debug Logging:** Integrated platform-aware logging that writes to `debug_logs.txt` at the project root in dev mode.
*   **Automatic Schema Migrations:** Updated `DriverFactory` to automatically detect and create missing tables (like `ModelCache`).

### Next Steps

The following tasks are planned for the next phase of development:

#### Core Functionality & AI
*   **Multi-Format Support:** Robust text extraction for **DOCX** and **PDF** files using native libraries for Android/iOS.
*   **AI Task Decomposition:** Full UI flow for the "Break It Down" feature to split assignments into 1-2 hour sub-tasks.
*   **Syllabus-to-Study Schedule:** Further refine the logic that suggests study periods based on weighted deliverables.

#### Calendar & Sync
*   **Client Secrets Management:** Secure injection mechanism for `client_secret.json`.
*   **Native Mobile Auth:** Native Google Sign-In SDKs for Android and iOS.
*   **Two-Way Synchronization:** Complete full bi-directional sync with Google Calendar.

#### UI & UX
*   **Visual Progress Tracking:** Progress bars and "Time Remaining" visuals for long-term projects.
*   **Export Support:** Implement `.ics` file export for the entire generated study plan.
