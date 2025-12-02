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

### Next Steps

The following tasks are planned for the next phase of development:

*   **Automatic Event Creation:** Automatically trigger the AI service to analyze a syllabus when it is added, and populate the calendar with the results.
*   **Enhanced Source Handling:**
    *   Add support for adding sources from a web page URL.
*   **Advanced AI Features:**
    *   Replace the dummy AI service with a real generative AI model that uses the stored API key.
    *   Implement citation linking from AI responses back to the source documents.
*   **Export and Sync:**
    *   Implement the ability to export the Academic Calendar as an `.ics` file.
    -   Integrate with Google Calendar for two-way synchronization.
