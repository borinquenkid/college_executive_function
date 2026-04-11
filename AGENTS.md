# College Executive Function

An application designed to assist students with executive function challenges by providing a structured environment to manage academic sources, generate study materials, and maintain a comprehensive academic calendar.

## Core Concepts

*   **Source Document:** A syllabus, web page, or text file that a user can upload and analyze.
*   **Event:** A time-sensitive item that can be a single occurrence or recur weekly. Events have a source (Routine, AI-Generated, or Manual) which determines their appearance.
*   **Academic Calendar:** A central, editable calendar (similar to Outlook) that aggregates all events from all sources.

## Features

*   **Sources Panel:** Upload and manage academic documents.
*   **Chat Panel:** An interactive chat interface to discuss and get insights from your sources.
*   **Studio Panel:** Generate summaries, outlines, and Q&A from your uploaded documents.
*   **Academic Calendar:** A comprehensive, editable calendar that consolidates all academic events and personal routines.
*   **Settings:** A dedicated screen for users to configure the application, including entering their own generative AI API key.

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
*   **Enhanced Testing:** Integrated `MockK` and `Ktor MockEngine` to verify API interactions and token persistence without real network calls.

### Next Steps

The following tasks are planned for the next phase of development:

#### Core Functionality & AI
*   **Automatic Event Creation:** Automatically trigger the AI service to analyze a syllabus when it is added, and populate the calendar with the results.
*   **AI Task Decomposition:** Implement a "Break It Down" feature to split large assignments into smaller, actionable sub-tasks with suggested deadlines.
*   **Advanced AI Features:** Replace the dummy AI service with a real generative AI model (e.g., Gemini) using the stored API key.
*   **Interactive Chat Actions:** Enable the AI to modify the calendar directly (create/move events) based on chat conversations.

#### Calendar & Sync
*   **Client Secrets Management:** Provide a secure way to inject `client_secret.json` for Google OAuth.
*   **Native Mobile Auth:** Replace mock implementations in `GoogleAuthService.android.kt` and `GoogleAuthService.ios.kt` with native Google Sign-In SDKs.
*   **Two-Way Synchronization:** Complete full synchronization with Google Calendar.
*   **Conflict & Load Flagging:** Implement "Exam Season" detection to flag high-load weeks and suggest proactive study blocks.

#### Integration & Persistence
*   **LMS Integration:** Research and implement connectors for Canvas, Blackboard, or Moodle to pull assignments automatically.
*   **Database Migration:** Integrate **SQLDelight** for robust, KMP-compatible local persistence of events and source data.

#### UI & UX
*   **Visual Progress Tracking:** Add progress bars and "Time Remaining" visuals for long-term projects to help with time visualization.
*   **Export Support:** Implement `.ics` file export using `ICalGenerator`.
