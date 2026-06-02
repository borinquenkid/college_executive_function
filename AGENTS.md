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
    *   **Syllabus:** Course documents containing deadlines and deliverables parsed via AI.
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

The application leverages high-context analysis models to perform the following key tasks:

*   **Syllabus Analysis:** Automatically identifies all deliverables (Assignments, Exams, etc.) and suggests proactive study blocks.
*   **Contextual Reasoning:** Processes entire syllabi (up to 1M tokens) in a single request for maximum accuracy.
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
*   **Calendar View:** The academic calendar now displays events from all sources.
*   **Testing Framework:** Added Kotest and wrote unit tests for data models, repositories, and serializers.
*   **iCalendar Support:** Added handling for `.ics` file generation and parsing.
*   **Google Calendar REST Integration:** Fully KMP-compatible synchronization using Ktor.
*   **OAuth2 Authentication:** Implemented support for local server flow (JVM) and persistent token storage.
*   **Programmatic ICal Generation:** Implemented `ICalGenerator` using modern iCal4j 4.x syntax.
*   **AI Integration (Robust):** Real intelligence implementation with auto-negotiation and persistent model caching.
*   **Study Plan Generation:** Specialized prompts to suggest proactive "STUDY_BLOCK" events leading up to major deadlines.
*   **Agentic Separation:** Refactored the UI to use a decoupled logic layer for headless testing and cleaner state management.
*   **Debug Logging:** Integrated platform-aware logging.
*   **Automatic Schema Migrations:** Updated database factory to automatically detect and create missing tables.
*   **Multi-Format Extraction:** Robust text extraction for DOCX and PDF files using native libraries for Android, iOS, and JVM.
*   **Native Mobile Auth:** Implemented native Google Sign-In using `GoogleSignInClient` (Android) and `ASWebAuthenticationSession` (iOS).
*   **Recursive Task Decomposition (Core Orchestration & AI Delegation):** Built `DecompositionOrchestrator` using FIFO queues and `WorkUnit` interfaces, wrapping `AIService` via delegation to handle recursive breakdown up to depth 3 with mathematical date projection and robust Kotest test specs.

### Next Steps

The following tasks are planned for the next phase of development:

#### Core Functionality & AI
*   **Automatic Source Categorization:** (Google Notebook style) Automatically tag sources as "Syllabus", "Reading Material", "Lab Manual", or "Lecture Notes" during ingestion to optimize AI retrieval.
*   **Multi-Source Chat Context:** Refactor `ContextAgent` to allow the Chat panel to reason across *all* stored sources simultaneously.
*   **AI Task Decomposition UI:** Implement the full UI flow for the "Break It Down" feature to split assignments into sub-tasks using the new orchestrator.
*   **Syllabus-to-Study Schedule:** Further refine the logic that suggests study periods based on weighted deliverables.

#### Calendar & Sync
*   **Client Secrets Management:** Secure injection mechanism for `client_secret.json`.
*   **Native Mobile Auth:** Native Google Sign-In SDKs for Android and iOS.
*   **Two-Way Synchronization:** Complete full bi-directional sync with Google Calendar.

#### UI & UX
*   **Vertical Layout Optimization:** [TODO] Shrink the vertical layout by approximately half to ensure buttons (like 'Accept') are reachable without tabbing.
*   **Visual Progress Tracking:** Progress bars and "Time Remaining" visuals for long-term projects.
*   **Export Support:** Implement `.ics` file export for the entire generated study plan.
