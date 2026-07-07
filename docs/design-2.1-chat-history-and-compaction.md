# Design 2.1: Chat History & Compaction

## Status
Proposed (scoping) — 2026-07-06. Target release: **2.1**.

## Summary
The in-app multi-source AI chat is currently **ephemeral and context-naive**: conversations are
held only in memory and are wiped on every app restart, and each turn re-sends a flat blob of
text with no token accounting and a hard `takeLast(10)` cut that silently drops older turns.

This design adds two capabilities under the umbrella of "managing the chat":

- **Part A — Chat history (customer-facing):** persistent, manageable conversations — multiple
  named chats that survive restart, each with its own pinned source scope, plus new / rename /
  delete / switch.
- **Part B — Compaction (model-facing):** a token budget with per-model context windows and a
  **rolling summary** so a long conversation stays inside the model's window gracefully instead
  of dropping turns or failing on oversized requests.

## Goals
- Conversations survive process death / app restart.
- Users can keep several distinct chats and manage them.
- A chat can grow indefinitely without losing early context or erroring on size.
- No regression to the existing RAG grounding (top-15 fragment ranking, grounding guard).

## Non-goals (2.1)
- Streaming token-by-token chat rendering (tracked separately under Phase 6b / AG-UI).
- Cross-device sync of chat history (on-device only, consistent with the app's privacy model —
  chat content is not sent to any CEF server).
- Reworking the Critic-Actor decorator chain (only account for its cost; see Risks).

---

## Current state (grounded in code)

**Model & state**
- `ChatMessage(author: String, content: String)` — no id, no timestamp, no conversation id
  (`composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/ChatPanel.kt:55`).
- Held in an in-memory `MutableStateFlow` in `AppController`, seeded with a hardcoded
  `ChatMessage("AI", "Hello! How can I help you today?")`
  (`AppController.kt:32`); appended via `addChatMessage` (`AppController.kt:194`). No remove/clear API.
- `AppController` is a lazy singleton (`DependencyContainer.kt:288`) — conversation survives
  navigation but **not app restart**.

**Persistence**
- Only `.sq` file is `composeApp/src/commonMain/sqldelight/.../db/AppDatabase.sq`; tables are
  `EventEntity`, `ModelCache`, `SourceEntity`, `FragmentEntity`, `AnalysisCacheEntity`,
  `UserOverrideLogEntity`. **No chat/conversation table.** Chat is never written to disk and never
  reloaded on startup. Confirmed ephemeral.

**Sources ↔ chat**
- Selection lives in `SourceSelector.kt` (`_selectedSource`) / `SourceManager.kt`
  (`sourceItems`, hydrated from `SourceEntity` on startup via `loadSources()`).
- `ChatPanel` holds a UI-local `var useAllSources by remember { mutableStateOf(true) }`
  (`ChatPanel.kt:79`) — the scope toggle resets whenever the panel leaves composition.

**LLM / context path**
- Send handler `ChatPanel.kt:136` snapshots history, appends the user turn, then calls
  `ContextAgent.queryAllSources(sources, conversationHistory, question, warnings)`
  (`ContextAgent.kt:78`) or `querySource(selectedSource, text)` (`ContextAgent.kt:54`, **no history,
  no caps — inlines the full document**).
- Prompt built by `ChatBuilder.getMultiSourceChatPrompt` (`ChatBuilder.kt:12`) as **one flat text
  part** (`GeminiBodyBuilder.kt:18` — single `contents` element, no `role: user/model` structure):
  - Sources: `fragmentRanker.rankFragments(..., topK = 15)` (`ContextAgent.kt:88`), each block
    capped at `MAX_CHARS_PER_SOURCE = 6_000` (`ChatBuilder.kt:9`), **re-sent every turn**.
  - History: `conversationHistory.takeLast(MAX_HISTORY_TURNS = 10)` (`ChatBuilder.kt:10,41`),
    verbatim; older turns silently dropped.
- **No token counting anywhere** (grep for `countTokens|usageMetadata|maxTokens|contextWindow` →
  0 non-test hits). `maxOutputTokens` never set. Only crude char caps.
- Model: chat negotiates `tier = LIGHT` (`GeminiAIService.kt:233` → `GeminiModelNegotiator`
  `LIGHT_PREFERENCES`: `gemini-2.5-flash-lite`, `gemini-flash-lite-latest`, `gemini-2.5-flash`,
  `gemini-flash-latest`). `ModelRpm.kt` holds per-model **RPM only** — no context-window sizes.
- Chat uses `family = CHAT` with its own paced/retried `GeminiRequestQueue`
  (`GeminiRequestExecutor.kt:57`, `PromptFamily.kt:21`).
- The Critic pass (`CriticActorAIService.kt:170`) makes a **second** Gemini call that re-embeds the
  entire prompt → effective input ≈ 2× per turn.
- Failure on oversized request → HTTP error → returned as the literal string `"Error: …"` shown as
  an AI bubble (`GeminiAIService.kt:238`, `ChatPanel.kt:160`); may be misclassified and get the
  model wrongly evicted as "bad" (`GeminiRequestExecutor.kt:235`). No size-aware recovery.

---

## Decisions (locked)

1. **Multiple named conversations** (not a single rolling chat). Natural home for per-chat source
   scope and per-chat summary; matches "managing the chat."
2. **Rolling-summary compaction** (not just a bigger sliding window). Preserves early context at the
   cost of an occasional extra LIGHT call; the summary is persisted per conversation.
3. **Per-conversation source scope** — sources are pinned to a conversation, replacing the
   throw-away global `useAllSources` toggle.
4. This document is checked in; `ROADMAP.md` gets a pointer entry.

---

## Part A — Chat history (persistence + management)

### Data model (new tables in `AppDatabase.sq`)

```sql
CREATE TABLE ConversationEntity (
    id           TEXT NOT NULL PRIMARY KEY,   -- deterministic UUID/string id
    title        TEXT NOT NULL,               -- user-editable; auto-derived from first message
    createdAt    INTEGER NOT NULL,            -- epoch millis
    updatedAt    INTEGER NOT NULL,
    sourceScope  TEXT NOT NULL,               -- 'ALL' | 'SOURCE:<sourceId>'  (per-chat pin)
    summary      TEXT                         -- rolling compaction summary (nullable, see Part B)
);

CREATE TABLE ChatMessageEntity (
    id             TEXT NOT NULL PRIMARY KEY,
    conversationId TEXT NOT NULL,
    role           TEXT NOT NULL,             -- 'USER' | 'AI'  (enum, replacing today's free String)
    content        TEXT NOT NULL,
    createdAt      INTEGER NOT NULL,
    tokenEstimate  INTEGER NOT NULL DEFAULT 0,-- cached estimate for budgeting (Part B)
    FOREIGN KEY (conversationId) REFERENCES ConversationEntity(id)
);
```
Deterministic ids (SHA-256 or a passed-in id) — the codebase already learned the "random ids →
duplicates" lesson (see `EventGenerationService`); do not use `Math.random()`/`Uuid.random` in
common code (also blocked in workflow scripts). Follow the existing `SourceEntity`/`FragmentEntity`
load pattern.

### Domain / API changes
- Extend `ChatMessage` → `ChatMessage(id, conversationId, role: ChatRole, content, createdAt)`.
  Add a `ChatRole { USER, AI }` enum (kills the stringly-typed `author == "User"` check at
  `ChatPanel.kt:210`). **Add serder round-trip tests** for the entity⇄domain mapping (project rule:
  every hand-parsed/`@Serializable` DTO conversion needs round-trip tests).
- `AppController`:
  - Replace the single `_chatMessagesWrapper` with `currentConversationId` + a
    `conversations: StateFlowReader<List<ConversationSummary>>` and
    `messages: StateFlowReader<List<ChatMessage>>` for the active conversation.
  - New APIs: `newConversation()`, `selectConversation(id)`, `renameConversation(id, title)`,
    `deleteConversation(id)`, and `addChatMessage` now persists (single write hook at `:194`).
  - On init, hydrate the conversation list from the DB (mirror `SourceManager.loadSources()` /
    `eventAgent.loadPersistedWarnings()`).
- New `ChatRepository` (SQLDelight-backed) behind an interface, injected via `DependencyContainer`.
  Keep DB access off the main thread (existing coroutine dispatch conventions).

### UI (`ChatPanel.kt` + new drawer)
- A **conversation drawer/list** (reuse the existing overlay-drawer pattern from Sources/Studio in
  `UniversalHomeLayout.kt:117`): list, "New chat", swipe/menu to rename & delete, active highlight.
- The greeting stops being a fake persisted message — it becomes the **empty state** of a new
  conversation.
- The source-scope chips (`ChatPanel.kt:101-117`) now read/write the conversation's pinned
  `sourceScope` instead of local `remember` state.
- Keep decomposition logic out of the composable (extract to a plain presenter/class and test that
  — existing "Compose decomposition for CRAP" convention; `ChatInputPresenter.kt` is the pattern).

---

## Part B — Compaction / context management

### Token budgeting
- Introduce a `TokenEstimator` (start with a cheap local heuristic ≈ `chars / 4`; leave a seam to
  swap in Gemini `countTokens` later). Cache `tokenEstimate` per message.
- Introduce per-model context windows: a `ModelContextWindow` map as a sibling to `ModelRpm.kt`
  (`contextWindowFor(model): Int`), with a conservative default for unknown models. This is the
  missing primitive — nothing today knows a model's window.
- **Per-turn budget allocation** (computed in/near `ContextAgent.queryAllSources` before
  `AiPrompts.getMultiSourceChatPrompt`):
  `window − reservedOutput − system/instructions − sourceBlocks(≤15×6k) − summary
   = budget for verbatim recent history`.

### Rolling summary (the compaction)
- Keep the **last N turns verbatim**; everything older is folded into `Conversation.summary`.
- When appended history would exceed the history budget, run **one LIGHT Gemini summarization call**
  (new `family = CHAT` or a dedicated `CHAT_SUMMARY` family) that produces/updates a compact
  running summary of the dropped turns; persist it on `ConversationEntity.summary`.
- The chat prompt gains a `Conversation summary so far: …` block ahead of the verbatim tail —
  replaces the naive `takeLast(10)` in `ChatBuilder.kt:41`.
- Trigger policy: summarize lazily (only when over budget), summarizing the oldest un-summarized
  turns in batches to amortize the extra call.

### Robustness
- Set `maxOutputTokens` in `generationConfig` (`GeminiBodyBuilder.kt:30`) so output is bounded and
  the input budget is predictable.
- **Distinguish token-limit / oversized-input errors** from bad-model errors in
  `GeminiRequestExecutor.categorizeError` (`:206`) so an over-long prompt does **not** evict/blacklist
  the model (`:235`). On that error: compact harder (shrink verbatim tail / re-summarize) and retry
  once, rather than surfacing `"Error: …"` as a chat bubble.
- Account for the **Critic double-send**: the effective input is ≈2× (`CriticActorAIService.kt:170`).
  Either budget for 2× when the critic is enabled for chat, or skip the critic pass when the turn is
  already near the window. (Decision deferred to implementation; do not rework the decorator chain.)
- **Unify `querySource`** (single-source) with `queryAllSources` so the single-source path also gets
  history, caps, and the same budgeting (today it bypasses all of them — `ContextAgent.kt:54`).

---

## Phasing / milestones
1. **Persistence** ✅ **DONE** — `ChatMessage` id/timestamp/role + `ChatRole` enum +
   `ConversationEntity`/`ChatMessageEntity` tables + `ChatRepository`; history survives restart for a
   single (implicit) conversation. `ChatMessageMapper` serder round-trip + deterministic
   `ChatRepositoryTest` (save/load/restart/idempotent) + `AppController` survives-restart test.
   Quality Gate OK, 3-target build green.
2. **Management UI** ✅ **DONE** — `Conversation`/`ChatSourceScope` domain + repository CRUD +
   conversation-aware `AppController` (currentConversationId, conversations flow, new/select/
   rename/delete, first-message title derivation) + `ConversationsPanel` drawer (list, switch,
   New chat, per-row rename + confirmed delete) + `ChatPanel` top bar and per-chat source pin.
   Greeting became a true empty-state. Serder round-trip + repository CRUD + deterministic
   `AppController` + `ConversationsPanel` compose tests. Quality Gate OK (new coverage 89.4%,
   0 new violations), 3-target build green.
3. **Compaction** — `TokenEstimator`, `ModelContextWindow`, budget allocation, rolling summary.
4. **Robustness** — oversized-error classification + recovery, `maxOutputTokens`, critic-cost
   handling, `querySource` unification.

Each phase must clear the mandated gates before "done": `./gradlew :composeApp:checkQualityGate`
(SonarQube) **and** the 3-target build check (see AGENTS.md → Static Analysis Quality Gate Protocol
and Phase completion protocol).

## Testing strategy
- **Serder round-trip** tests for the entity⇄`ChatMessage` mapping.
- **Deterministic coroutine tests** for persistence + load (runTest + `StandardTestDispatcher`,
  `advanceUntilIdle()`; not `Dispatchers.Unconfined` + wall-clock `eventually`).
- **Compaction unit tests**: budget math, "summary triggers only over budget", "last N verbatim +
  summary replaces takeLast(10)", token-estimate monotonicity.
- **Presenter tests** for extracted chat logic (keep composables thin).
- Integration test (`*IntegrationTest`) for a long conversation that crosses the compaction
  threshold, asserting early context is still reflected via the summary and no oversized-request
  error occurs. Soft-assert LLM-quality outputs; set both `timeout` and `invocationTimeout`.

## Risks / open questions
- **Summary quality/latency** — a bad running summary silently degrades answers; mitigate with a
  focused summarization prompt + tests, and only summarize when over budget.
- **Critic 2× interaction** — final policy (budget for it vs. skip near-limit) decided at impl time.
- **Migration** — additive tables only; no migration of existing data (chat has never been
  persisted). Confirm SQLDelight schema-version bump handling.
- **Model-window accuracy** — `ModelContextWindow` values are hand-maintained constants; keep them
  conservative to avoid over-filling.

## Key files
`ChatPanel.kt` (:55, :79, :136, :210), `ChatInputPresenter.kt`, `AppController.kt` (:32, :194),
`DependencyContainer.kt` (:288), `AppDatabase.sq`, `SourceManager.kt` / `SourceSelector.kt`,
`ContextAgent.kt` (:54, :78, :88), `ChatBuilder.kt` (:9, :10, :41), `AiPrompts.kt`,
`GeminiBodyBuilder.kt` (:18, :30), `GeminiAIService.kt` (:229, :233), `GeminiRequestExecutor.kt`
(:57, :206, :235), `GeminiModelNegotiator.kt`, `ModelRpm.kt`, `PromptFamily.kt`,
`CriticActorAIService.kt` (:152), `GroundingGuardAIService.kt` (:59).
