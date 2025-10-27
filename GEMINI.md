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

## UI Implementation Status

To fully realize the application's design, the following UI components have been built out.

### General Styling
- [x] **Borders and Padding:** Apply consistent borders and padding to all three panels to create clear visual separation and a polished look.
- [x] **Typography:** Ensure a consistent and readable typography scale is used throughout the application.
- [x] **Color Palette:** Implement a consistent color scheme for backgrounds, text, and interactive elements.

### Sources Panel
- [x] **"Add Source" Button:** Add a prominent "Add Source" button or icon to the top of the panel.
- [x] **Selection State:** Implement a visual indicator for the currently selected source.
- [x] **Styling:** Refine the styling of the source items to be more visually appealing.

### Chat Panel
- [x] **Message Display:** Create a `LazyColumn` to display a list of chat messages, with distinct styling for user and AI messages.
- [x] **Input Field:** Add a `TextField` at the bottom of the panel for user input.
- [x] **Send Button:** Add an `IconButton` next to the input field to send messages.

### Studio Panel
- [x] **"Generate Content" Button:** Add a styled `Button` to the top of the panel.
- [x] **Output Display:** Create a text area to display the generated content (summaries, outlines, etc.).
- [x] **Action Chips:** Implement a row of `Chip` components for suggested actions.

## Next Steps

The following tasks are planned for the next phase of development:

*   **Functionality:**
    *   Implement the file picker logic for the "Add Source" button.
    *   Integrate a generative AI model to power the chat and studio panels.
    *   Connect the "Generate Content" button and action chips to the AI model.
    *   Implement citation linking from AI responses back to the source documents.

*   **UI Refinement:**
    *   [x] Refine the typography and color palette to perfectly match the target design.
    *   [x] Improve the styling of chat messages to distinguish between the user and the AI.
    *   Add animations and transitions to create a smoother user experience.
