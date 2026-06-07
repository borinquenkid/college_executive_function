# ADR 0001: Adopt AG-UI Protocol via Server-Sent Events (SSE)

## Status
Accepted

## Context
We are implementing a React web client for the College Executive Function (CEF) application, which is backed by a Kotlin Multiplatform (KMP) core engine. 

A traditional REST API design would require:
1. Re-defining data transfer objects (DTOs) on both the Ktor backend (Kotlin) and React frontend (TypeScript), leading to double maintenance.
2. Building separate request-response endpoints for every single action (fetching calendar, uploading files, querying the chat agent, task decomposition).
3. A static, passive UI where the student cannot see the intermediate steps of the agent's reasoning (such as the Critic-Actor cycle evaluating scheduling overlaps).

We need an interaction model that reduces classpath duplication and natively supports real-time, streaming agent reasoning.

## Decision
We will adopt the **AG-UI (Agent-User Interaction) Protocol** using **Server-Sent Events (SSE)** as the primary transport layer for agent-user interactions.

1. **Transport:** The React frontend will open a single persistent SSE connection at `GET /api/agent/stream?query=...`.
2. **Unified Event Protocol:** All reasoning, message streaming, tool calls, and state sync updates will be packaged into standardized AG-UI JSON events:
   * `RUN_STARTED` / `RUN_FINISHED` for execution lifecycles.
   * `REASONING_DELTA` for streaming inner Critic-Actor loops.
   * `TOOL_CALL_START` / `TOOL_CALL_RESULT` for active background actions.
   * `TEXT_MESSAGE_DELTA` for token-by-token answer generation.
   * `STATE_SNAPSHOT` for pushing DB modifications (events, sources) directly to the calendar view.
3. **Thin Client Rendering:** The React client will be a dynamic renderer that maps incoming events to visual states (reasoning terminal, tool log widget, markdown chat bubbles, and calendar list views) without maintaining hardcoded schemas for every API action.

## Consequences

### Positive
* **No Class Duplication:** Frontend models do not need to duplicate Kotlin database mappings. Data is passed within a generic JSON payload inside standard envelopes.
* **Agentic Transparency:** Students get immediate feedback on what the agent is doing (e.g., "Reviewing syllabus details...") and thinking (e.g., "No overlaps found on Monday...").
* **Ease of Extension:** Adding new agent capabilities or tools requires changes only in the Kotlin backend; the frontend dynamically reacts to new event streams without api-contract edits.

### Negative
* **Server Connection Management:** Keeping SSE connections open increases memory utilization on the Ktor Netty server compared to short-lived REST connections.
* **Complex Client Testing:** Verifying the integration requires testing streaming line-by-line event emitters rather than simple HTTP response codes.

## Generalizability to Any Backend Binding

This architectural pattern is not unique to KMP/Ktor; it is a general-purpose paradigm for Agentic UIs:
1. **Decoupled Frontend:** By using a standard envelope structure (`{ type, timestamp, data }`), the frontend is completely agnostic of the backend's implementation language or framework (whether it is Kotlin, Python/LangGraph, or Node.js).
2. **State Synchronization via Snapshot Pushes:** Instead of the frontend guessing when to request data via separate `GET` requests after a background process finishes, the backend streams the updated state directly via `STATE_SNAPSHOT` events, resolving the "stale UI" problem generically.
3. **Execution Transparency:** The protocol natively translates multi-step agent actions (RAG retrieval, database access, Critic-Actor loops) into an interactive timeline, preventing the need for complex, ad-hoc polling APIs.

