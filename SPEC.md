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
* **Endpoints:**
  * `GET /api/agent/stream?query={urlEncodedQuery}` — chat with the ContextAgent.
  * `GET /api/events/{id}/decompose/stream` — "Break it Down" task decomposition (Phase 6.5); emits `RUN_STARTED` → `TOOL_CALL_START`/`TOOL_CALL_RESULT` (`decomposeTask`) → `STATE_SNAPSHOT` with `{"decomposedTasks": [{"title", "daysBeforeDue", "description"}, ...]}` → `RUN_FINISHED`, or `ERROR` → `RUN_FINISHED` if `id` doesn't match any event on the "default" calendar.
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
* **`REASONING_DELTA`**: Emits chain-of-thought text (from Critic-Actor loop) — one event per phase (initial retrieval, then "reviewing the answer" if a critique pass runs); the client (`useAgentStream`) renders each as its own bubble rather than concatenating them (Phase 6.5).
* **`TOOL_CALL_START` / `TOOL_CALL_RESULT`**: Notifies the UI of background tasks (e.g. database reads, sync flushes) and returns JSON results.
* **`TEXT_MESSAGE_START` / `TEXT_MESSAGE_DELTA` / `TEXT_MESSAGE_END`**: Streams response text word-by-word — implemented as of Phase 6.5 (`SseEventWriter.emitTextWordByWord`, `server/.../SseEventWriter.kt`): one `TEXT_MESSAGE_DELTA` per word (plus trailing whitespace) with a small delay between them, bracketed by `_START`/`_END`.
* **`STATE_SNAPSHOT`**: Delivers a full update of some piece of state — e.g. `GET /api/events/{id}/decompose/stream` (below) uses it for `{"decomposedTasks": [...]}`.
* **`RUN_FINISHED`**: Execution complete.

### 4.3 REST API Endpoints
To support sources management, settings persistence, and calendar synchronization, the server exposes the following REST endpoints:

* **`POST /api/auth/start`**: Establishes a session — no credentials required. If the caller already has a valid session cookie, no-ops and returns it; otherwise generates a new random studentId and sets it as a signed session cookie. The web client calls this once on mount before any other `/api/*` request. Rate-limited to 5 requests/min per IP (see 4.4).
* **`POST /api/auth/logout`**: Clears the session cookie.
* **`GET /api/sources`**: Returns the list of `SourceItem`s currently stored in the database.
* **`POST /api/sources`**: Ingests new content. Supports two formats in `multipart/form-data`:
  * `url`: A string URL to be ingested.
  * `file`: A binary file (e.g. PDF/DOCX/ICS) to be processed.
* **`DELETE /api/sources/{id}`**: Deletes a source by its title (which acts as the source ID) and cleans up associated calendar events.
* **`GET /api/events`**: Returns the list of active calendar events from the local calendar database.
* **`POST /api/events/sync`**: Forces a two-way synchronization between the local repository and the remote provider.
* **`GET /api/settings`**: Returns the active system settings: `{ "apiKey": null, "hasApiKey": boolean, "studyPreferences": { ... } }`. The stored Gemini key never round-trips back to the client — `apiKey` is always `null` in the response; `hasApiKey` just tells the UI whether one is configured.
* **`POST /api/settings`**: Saves the Gemini API Key (if `apiKey` is present in the body — omit it to leave an existing key untouched) and/or study preferences.

### 4.4 Multi-Tenancy & Auth
Every endpoint except `POST /api/auth/start` resolves to a per-student `DependencyContainer` based on a signed, `HttpOnly` session cookie established by `POST /api/auth/start` — requests without a valid session get `401 Unauthorized`. There is no username/password; the session's random studentId is itself the credential (see [docs/adr/0005-session-based-student-auth.md](docs/adr/0005-session-based-student-auth.md) for the full rationale, including the deliberate "ease of adoption over defense-in-depth" trade-off). An `X-Student-ID` header, if sent, is ignored entirely. Each student's data lives in an isolated, hash-partitioned SQLite database — see [docs/adr/0002-multi-tenant-docker-path-partitioned-storage.md](docs/adr/0002-multi-tenant-docker-path-partitioned-storage.md) for the storage design and Docker deployment shape.

