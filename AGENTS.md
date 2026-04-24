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
*   **The Object (Event):** All inputs are parsed (via AI or direct API/ICS parsing) into a unified `Event` model.
*   **The Destination:** All generated events are synchronized into the **Student's Master Calendar** (the primary calendar they use daily).

## Features

*   **Sources Panel:** Manage inputs from Local Files, URLs (Public/Private), and Google Drive.
*   **Academic Calendar:** A central, editable dashboard that aggregates and synchronizes events from all sources.
*   **AI Integration:** Performs automated analysis of Syllabi and Documents to extract events and decompose complex tasks.
*   **Synchronization Service:** Handles the push/pull logic between the app's internal state and the Student's external calendar providers.

## AI Integration

The application will leverage a generative AI model to perform the following key tasks:

*   **Syllabus Analysis:** When a syllabus is added, the AI will identify all deliverables and add them to the Academic Calendar.
*   **Calendar Management:** The AI will automatically update the Academic Calendar when new sources are added.

## Testing Requirements

All business logic classes (Models, Repositories, Services, and Utilities) must have associated unit tests using the Kotest framework. UI components should have corresponding Compose tests where appropriate to verify state changes and user interactions.

## Development Roadmap

### Completed Tasks

*   **UI Scaffolding:** Initial UI for all three panels is complete.
*   **General Styling:** A consistent theme, including colors, typography, and borders, has been applied.
*   **File Picker:** A functional file picker has been implemented for desktop and Android.
*   **Settings Screen:** A functional settings screen for entering and saving an API key has been implemented.
*   **Unified Event Model:** Refactored the entire application to use a unified `Event` data model, which supports recurrence, different event types, and color-coding by source.
*   **Routine Management:** A complete flow for creating, viewing, and persisting a recurring weekly schedule. The UI includes robust date, time, and day-of-week pickers.
*   **Calendar View:** The academic calendar now displays events from all sources (currently Routine and AI-Generated), grouped by date with sticky headers.
*   **Testing Framework:** Added the Kotest testing framework and wrote unit tests for data models, repositories, and serializers.
*   **iCalendar Support:** Added `ical4j` library for handling `.ics` file generation and parsing.
*   **Google Calendar REST Integration:** Implemented a fully KMP-compatible `GoogleCalendarSyncService` using Ktor to synchronize iCal components with the Google Calendar API.
*   **OAuth2 Authentication:** Implemented `GoogleAuthService` using an `expect/actual` pattern. The JVM target uses the `google-oauth-client-jetty` for a local server flow, and `GoogleTokenRepository` persists credentials across all platforms.
*   **Programmatic ICal Generation:** Implemented `ICalGenerator` using modern iCal4j 4.x syntax and Java 8+ Temporal types.
*   **AI Integration:** Replaced the dummy AI service with a real **Gemini 1.5 Flash** model using the stored API key. It now handles event extraction from raw text and syllabi.
*   **Database Migration:** Integrated **SQLDelight** for robust, KMP-compatible local persistence of events, supporting overlap checks and sync-state tracking.
*   **Automatic Event Creation:** Automatically triggers AI analysis (Gemini) or programmatic parsing (ICal4j) for all sources added via **URL, Local Files, or Google Drive**.

### Next Steps

The following tasks are planned for the next phase of development:

#### Core Functionality & AI
*   **Multi-Format Support:** Implement text extraction for **DOCX** and **PDF** files to allow the AI to analyze syllabi in these common academic formats, using mobile-optimized libraries.
*   **AI Task Decomposition:** Implement a "Break It Down" feature to split large assignments into smaller, actionable sub-tasks with suggested deadlines.
*   **Interactive Chat Actions:** Enable the AI to modify the calendar directly (create/move events) based on chat conversations.

#### Calendar & Sync
*   **Client Secrets Management:** Provide a secure way to inject `client_secret.json` for Google OAuth.
*   **Native Mobile Auth:** Replace mock implementations in `GoogleAuthService.android.kt` and `GoogleAuthService.ios.kt` with native Google Sign-In SDKs.
*   **Two-Way Synchronization:** Complete full synchronization with Google Calendar.
*   **Conflict & Load Flagging:** Implement "Exam Season" detection to flag high-load weeks and suggest proactive study blocks.

#### Integration & Persistence
*   **LMS Integration:** Research and implement connectors for Canvas, Blackboard, or Moodle to pull assignments automatically.

#### UI & UX
*   **Visual Progress Tracking:** Add progress bars and "Time Remaining" visuals for long-term projects to help with time visualization.
*   **Export Support:** Implement `.ics` file export using `ICalGenerator`.
