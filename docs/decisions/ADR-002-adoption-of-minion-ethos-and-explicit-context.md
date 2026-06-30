# ADR-002: Adoption of Minion Ethos and Explicit Context for LLM Operations

## Status
Accepted

## Date
2026-06-29

## Context

The College Executive Function (CEF) application utilizes multiple agentic and LLM-driven pipelines:
- [IngestionAgent](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/IngestionAgent.kt): For multi-format document parsing and automated source categorization.
- [EventAgent](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/EventAgent.kt): For extraction of deliverables and generating proactive study plans under time/break constraints.
- [DecompositionOrchestrator](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/DecompositionOrchestrator.kt): For decomposing complex academic deadlines.
- [ContextAgent](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/ContextAgent.kt): For multi-source user chat query resolution.

### Core Problems:
1. **Unpredictable Reasoning/Model Drift:** Natural language commands without structural scoping result in highly variable outputs, hallucinations, or failing to adhere to strict business rules (such as study hour constraints or year-level filtering).
2. **The RAG Trap:** Pure similarity-based Retrieval-Augmented Generation (as discussed in [ADR-001](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/docs/decisions/ADR-001-vector-db-for-on-device-rag.md)) retrieves text fragments based on semantic proximity (dense/sparse vector matching). This retrieval introduces key vulnerabilities:
   - It fetches irrelevant historical/future blocks while omitting crucial metadata.
   - It lacks structural precision, making it hard to audit and trace the exact fact-anchors used for generation.
   - It degrades on authoritative guidance where strict date boundaries (such as a specific academic semester) are required.

CEF targets students with executive function challenges. Therefore, the assistant's behavior must be highly consistent, verifiable, deterministic, and predictable.

---

## Decision

CEF formally adopts the **"Minion Ethos"** and **"Explicit Context"** design patterns for all LLM and agentic workflows:

1. **Treat LLM Agents as "Minions" (Well-Briefed Helpers):**
   - We reject the view of LLMs as conversational "genies" or autonomous agents operating without rails. LLM invocations are structured as deterministic tasks assigned to well-briefed helpers.
2. **Mandatory 4-Part Briefing Template:**
   All system and task prompts in CEF must conform to a structured briefing standard:
   - **Topic Clarification:** A brief 1-2 sentence declaration defining the precise task domain (e.g. "This prompt is about decomposing a course deadline into sub-tasks").
   - **Structured Reference Material:** Context must be passed in formatted containers (using Markdown, XML tags, or JSON arrays) to cleanly isolate user content from prompt instructions.
   - **Task Prompt:** Narrow, explicit, and testable instructions (e.g. "Generate a list of study blocks...").
   - **Guardrails & Constraints:** Explicit restrictions on what the model *must not* do (e.g., "Do not schedule study events past 9:00 PM," or "Filter out events outside the document's year").
3. **Transition to "Explicit Context" Injection:**
   - Where authoritative guidance or direct actions are needed, CEF will prioritize explicitly selected, structured context payloads (such as direct academic source mapping or verified calendar events) rather than relying on raw vector similarity RAG.
   - Vector similarity search (when adopted) will only serve as a discovery/ranking engine, but the final context will be assembled explicitly and grounded using the `Confabulation Gate Protocol` detailed in [AGENTS.md](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/AGENTS.md).

---

## Alternatives Considered

### Conversational / Improvised Prompting
- **Pros:** Fast setup and implementation.
- **Cons:** High probability of hallucination, difficult to write deterministic unit tests, violates CEF complexity and testability targets.
- **Rejected:** Incompatible with our core user base of students requiring highly structured environments.

### Unfiltered Semantic RAG
- **Pros:** Easy to plug-and-play with vector search tools (such as ObjectBox outlined in [ADR-001](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/docs/decisions/ADR-001-vector-db-for-on-device-rag.md)).
- **Cons:** Prone to "blowing chunks and losing the plot" (truncating crucial temporal context or including old assignments that match keywords). It fails on exact temporal boundaries like "this week's homework" when query keywords match previous semesters.
- **Rejected:** CEF requires strict boundaries (e.g. year-level and source-fact grounding) that semantic proximity alone cannot guarantee.

---

## Consequences

### Positive
- **High Testability:** Structured prompts are easily parsed and tested. Mock inputs can be isolated inside XML/Markdown tags in test suites.
- **Improved Grounding:** Anchoring the model to structured reference material directly supports our `SourceFactGrounder` and `GroundingGuardAIService` implementation.
- **Predictable Output:** Standardizing prompt structure reduces model divergence when auto-negotiating or switching models (e.g. Gemini 1.5 Flash vs fallback variants).

### Negative
- **Context Overhead:** Structuring inputs with markdown formatting, XML schemas, and templates increases input token size marginally. This is acceptable given the large 1M context window of the primary Gemini engine.
- **Refactoring Effort:** Requires refactoring any legacy prompts across the codebase to conform to the 4-part memo briefing format.
