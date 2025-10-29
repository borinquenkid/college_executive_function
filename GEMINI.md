# College Executive Function

An application designed to assist students with executive function challenges by providing a structured environment to manage academic sources, generate study materials, and maintain a comprehensive academic calendar.

## Core Concepts

*   **Syllabus:** A source document that outlines the curriculum, assignments, and schedule for a course.
*   **Calendar Item:** A time-sensitive event, which can be sourced from a web page, a text file, or a user-created schedule.
*   **Academic Calendar:** A central, editable calendar (similar to Outlook) that aggregates all events from syllabi and calendar items. It can be initialized with the user's daily routine and will eventually support export and synchronization with external calendar services.

## Features

*   **Sources Panel:** Upload and manage academic documents, including syllabi and calendar items from various sources (web pages, text files, etc.).
*   **Chat Panel:** An interactive chat interface to discuss and get insights from your sources. The AI acts as a thinking partner, providing structured and cited responses.
*   **Studio Panel:** Generate summaries, outlines, and Q&A from your uploaded documents using the power of Generative AI.
*   **Academic Calendar:** A comprehensive, editable calendar that consolidates all academic events and personal routines.

## AI Integration

The application will leverage a generative AI model (initially Google's, but configurable by the user) to perform the following key tasks:

*   **Date Interpretation:** The AI will analyze web pages and text files to identify and extract calendar events.
*   **Syllabus Analysis:** When a syllabus is added, the AI will:
    *   Identify all deliverables (assignments, exams, etc.) and add them to the Academic Calendar.
    *   Create alerts for each deliverable (one day before and one hour before).
    *   Proactively block out 1.5-hour study sessions for major exams and projects.
*   **Calendar Management:** The AI will automatically update the Academic Calendar when new sources are added, blocking out time for events and study sessions.

## Application Architecture and Layout

The application is designed with a modern, reactive architecture and features a responsive layout that adapts to different screen sizes. State is managed centrally and propagated to the UI components.

### Desktop Design: A Re-creation of Google's NotebookLM

The desktop application's look and feel are designed to replicate the user interface of Google's NotebookLM, optimized for larger screens and mouse/keyboard interaction.

#### Three-Panel Layout

A responsive, three-panel, vertical layout structure forms the core of the UI:

*   **Left Panel (Source Panel):** A permanent, vertically scrollable list view for uploaded sources (e.g., documents, PDFs, links). It includes an icon/button to easily add new sources and is collapsible/resizable.

*   **Center Panel (Chat/Interaction Panel):** The primary workspace, featuring a chat interface for interacting with the AI. This panel displays conversational turns and generated content.

*   **Right Panel (Notes/Studio Panel):** A dedicated, resizable panel for generated notes or structured AI outputs like mind maps, study guides, and audio overviews. It dynamically adjusts its content based on the user's current task.

#### Visual Aesthetic and Interaction

*   **Aesthetic:** The design is modern, clean, and minimalist, with a white/light gray background. Subtle vertical dividers provide clear visual separation between the three panels.

*   **Typography:** Readability is prioritized with clean, sans-serif fonts (like Roboto or equivalent).

*   **Color Palette:** Subtle, brand-aligned colors (e.g., blues/greens for accents, buttons, and interaction elements) are used against the light background.

*   **Key Interaction Elements:**
    *   A persistent, anchored input bar is at the bottom of the Chat Panel for user prompts.
    *   All AI-generated responses include clear, citable links back to the specific text snippets in the Sources Panel.
    *   Easily accessible action chips or buttons (e.g., 'Summarize,' 'Generate Mind Map') are incorporated within the Chat Panel.
    *   A prominent App Bar/Title Bar includes a logo, notebook title, and consistent navigation elements.

### Mobile Layout: A Compact, Tab-Based Interface

To ensure a great user experience on smaller screens, the application adopts a compact, tab-based interface that prioritizes the chat experience while keeping other panels easily accessible.

*   **Vertical, Collapsible Panels:** The layout switches to a vertical `Column` arrangement. The `Chat Panel` is the central, always-visible component.
*   **Hidden by Default:** The `Sources Panel` (top) and `Studio Panel` (bottom) are hidden by default to maximize screen real estate for the conversation.
*   **Expand/Collapse on Tap:** Each hidden panel has a corresponding tab (represented by an arrow icon). Tapping the tab for the Sources panel expands it downwards from the top, and tapping the tab for the Studio panel expands it upwards from the bottom. Tapping the tab again collapses the panel.
*   **Focused View:** When a panel is expanded, it occupies a portion of the screen, allowing the user to focus on that specific context (viewing sources or studio content) while the chat remains partially visible.

### Key Components

*   `main`: The main entry point and layout structure.
*   `chat_panel`: Displays the conversation history.
*   `sources_panel`: Manages user-uploaded source documents.
*   `studio_panel`: Integrates with a generative AI model to create study aids.

## Development Roadmap

### Completed Tasks

*   **UI Scaffolding:** Initial UI for all three panels is complete.
*   **General Styling:** A consistent theme, including colors, typography, and borders, has been applied.
*   **File Picker:** A functional file picker has been implemented for desktop and Android.
*   **Dummy AI Service:** A placeholder AI service has been integrated for all platforms.

### Next Steps

The following tasks are planned for the next phase of development:

*   **Academic Calendar Implementation:**
    *   Create the main calendar view, similar to Outlook.
    *   Implement the initial user routine setup flow.
    *   Integrate the calendar with the AI service for automatic event creation.
*   **Enhanced Source Handling:**
    *   Add support for adding sources from a web page URL.
    *   Implement a custom schedule picker for creating calendar events.
*   **Advanced AI Features:**
    *   Replace the dummy AI service with a real generative AI model (e.g., Google's).
    *   Implement citation linking from AI responses back to the source documents.
*   **Export and Sync:**
    *   Implement the ability to export the Academic Calendar as an `.ics` file.
    *   Integrate with Google Calendar for two-way synchronization.
