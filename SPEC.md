# College Executive Function (CEF) — System Specification

This specification serves as the master design document for the College Executive Function (CEF) application, detailing the core architecture, features, AI integration strategies, and the real-time agentic web stream protocol.

---

## 1. Core Architecture

The application conslidates academic data into a single, synchronized "Source of Truth" using a structured flow:

```
┌────────────────────────────────────────────────────────┐
│                        INPUTS                          │
│   - ICS Calendar Feeds (.ics URL / File)               │
│   - Course Syllabi (PDF / DOCX)                        │
│   - Class Documents (Rubrics, Notes)                   │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│                     LOGIC AGENTS                       │
│  - IngestionAgent (Extracts and categorizes fragments) │
│  - EventAgent (Generates deliverables & study plans)  │
│  - NormalizationService (De-duplicates categories)     │
│  - CalendarAgent (Controls calendar sync gateways)    │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│                   THE DESTINATION                      │
│            Student's Master Calendar                   │
└────────────────────────────────────────────────────────┘
```

### 1.1 Ingestion Flow
* **`IngestionAgent`:** Intelligent parser that extracts raw content into high-fidelity `SourceFragments`. Automatically categorizes all non-ICS text content.
* **`EventAgent`:** Consumes structured content to generate direct Deliverables (exams, deadlines) and proactive Study Plans using high-context reasoning.
* **`NormalizationService`:** Standards category labels and runs programmatic deduplication guards.
* **`CalendarAgent`:** Gateway managing local calendar persistence and remote provider synchronization.

---

## 2. Core Features

* **Sources Panel:** Manages inputs from local folders, web URLs, and Google Drive.
* **Academic Calendar:** central editable dashboard grouping events by date with linear progress tracking and countdown chips.
* **Sync Proposals:** Displays an interactive negotiation proposal dialog to resolve conflicts between local and remote modifications.
* **Task Decomposition:** FIFO-based depth-3 sub-task decomposition orchestrator (`DecompositionOrchestrator`) breaking down major deadlines.
* **Multi-Source Context Chat:** Dynamic RAG chat using `ContextAgent` querying across all loaded source fragments ranked via TF-IDF.

---

## 3. AI Strategy

* **Primary Engine:** Gemini 1.5 Flash (via REST API) for high stability and a large 1M-token context window.
* **Model Negotiation:** `ModelManager` caches the best available model in SQLite and handles automated fallbacks upon quota exhaustion.
* **Critic-Actor Loop:** Decorator loop (`CriticActorAIService`) running refinement iterations with cycle/oscillation detection.
* **Stateful Preference Memory:** Implicit constraints (derived from manual calendar modifications) are injected as rules into prompt context.

---

## 4. Web Module & Agentic Stream (AG-UI) Protocol

To support a React web client without duplicating domain classes, CEF communicates via a Server-Sent Events (SSE) stream using the **AG-UI Protocol**.

```
┌────────────────────────┐             GET /api/agent/stream
│   React Web Frontend   │ ───────────────────────────────────────────► ┌────────────────────────┐
│                        │ ◄─────────────────────────────────────────── │      Ktor Backend      │
│  - useAgentStream hook │          AG-UI Event Stream (SSE)            │                        │
│  - Dynamic Renderers   │                                              │  - DependencyContainer │
└────────────────────────┘                                              └────────────────────────┘
```

### 4.1 Transport Specification
* **Endpoint:** `GET /api/agent/stream?query={urlEncodedQuery}`
* **Response Content-Type:** `text/event-stream`
* **Response Headers:** `Cache-Control: no-cache`, `Connection: keep-alive`

### 4.2 AG-UI Event Schemas (JSON)
All events use a generic wrapper structure:
```json
{
  "type": "EVENT_TYPE",
  "timestamp": 1717720000000,
  "data": {}
}
```

* **`RUN_STARTED`**: Stream session initialized.
* **`REASONING_START` / `REASONING_DELTA` / `REASONING_END`**: Emits chain-of-thought text (from Critic-Actor loop).
* **`TOOL_CALL_START` / `TOOL_CALL_RESULT`**: Notifies the UI of background tasks (e.g. database reads, sync flushes) and returns JSON results.
* **`TEXT_MESSAGE_START` / `TEXT_MESSAGE_DELTA` / `TEXT_MESSAGE_END`**: Streams response markdown text word-by-word.
* **`STATE_SNAPSHOT`**: Delivers a full update of active source documents or calendar events.
* **`RUN_FINISHED`**: Execution complete.

### 4.3 REST API Endpoints
To support sources management, settings persistence, and calendar synchronization, the server exposes the following REST endpoints:

* **`GET /api/sources`**: Returns the list of `SourceItem`s currently stored in the database.
* **`POST /api/sources`**: Ingests new content. Supports two formats in `multipart/form-data`:
  * `url`: A string URL to be ingested.
  * `file`: A binary file (e.g. PDF/DOCX/ICS) to be processed.
* **`DELETE /api/sources/{id}`**: Deletes a source by its title (which acts as the source ID) and cleans up associated calendar events.
* **`GET /api/events`**: Returns the list of active calendar events from the local calendar database.
* **`POST /api/events/sync`**: Forces a two-way synchronization between the local repository and the remote provider.
* **`GET /api/settings`**: Returns the active system settings: `{ "apiKey": "...", "studyPreferences": { ... } }`.
* **`POST /api/settings`**: Saves the Gemini API Key and/or study preferences.

