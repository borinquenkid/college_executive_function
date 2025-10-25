# College Executive Function

An application designed to assist students with executive function challenges by providing a structured environment to manage academic sources and generate study materials.

## Features

*   **Sources Panel:** Upload and manage academic documents like syllabi and calendars.
*   **Chat Panel:** An interactive chat interface to discuss and get insights from your sources. The AI acts as a thinking partner, providing structured and cited responses.
*   **Studio Panel:** Generate summaries, outlines, and Q&A from your uploaded documents using the power of Generative AI.

## Development Log

This project was recently analyzed and enhanced. Here are the key improvements:

### AI Interaction Model

*   The AI's persona and response format have been updated to follow a strict three-panel layout (`[SOURCES PANEL]`, `[CHAT PANEL]`, `[STUDIO PANEL]`),
    mimicking a thinking partner environment.
*   This ensures that AI responses are well-structured, grounded in the provided source documents, and offer clear next steps for the user.

### Performance and UI Enhancements

The initial version of the application experienced UI lag. The following optimizations were implemented:

1.  **Studio Panel UX Improvement:**
    *   When generating content (Summaries, Outlines, etc.) via the backend API, the UI would become unresponsive.
    *   **Solution:** A loading indicator is now displayed during content generation to provide visual feedback to the user.

2.  **Sources Panel Optimization:**
    *   The sources panel was identified as a performance bottleneck due to its entire structure rebuilding on data changes.
    *   **Solution:** The UI for each source was extracted into a separate, more efficient component. This ensures that only the necessary components are rebuilt when data changes, improving overall UI fluidity.

3.  **Chat Panel Analysis:**
    *   The chat panel uses an efficient list view for displaying messages. However, rendering markdown for each message was noted as a potential area for future optimization if conversations become very long.

## Application Architecture

The application is designed with a modern, reactive architecture. State is managed centrally and propagated to the UI components. Key components include:

*   `main`: The main entry point and layout structure.
*   `chat_panel`: Displays the conversation history.
*   `sources_panel`: Manages user-uploaded source documents.
*   `studio_panel`: Integrates with a generative AI model to create study aids.
