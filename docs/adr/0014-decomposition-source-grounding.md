# ADR 0014: Ground Task Decomposition in Source Text

## Status
Accepted (2026-08-12)

## Context

Prompted by an external analysis of why multi-agent pipelines fail for complex analytics: the
failure mode isn't having multiple steps, it's when one step's *compressed reasoning* (not raw
facts) becomes the next step's entire input — nuance gets stripped at the handoff and a
downstream step draws a conclusion the original evidence wouldn't support.

A trace of CEF's actual agent-to-agent data flow against that claim found most of the app
already avoids it: `IngestionAgent`→`EventAgent` both read the same raw `SourceFragment`s, not
a summary of each other's output; `EventAgent`→`CalendarAgent` passes typed `Event` objects, not
prose; `ContextAgent`'s RAG reads raw fragments directly, bounded by `FragmentRanker`'s
`topK = 15`. `CriticActorAIService`'s calendar-event and chat critique passes both re-include the
source text (`EventBuilder.getEventCritiquePrompt`'s `<source_syllabus_document>`, and chat's
critique echoing the full original prompt — which already embeds retrieved fragments — back
alongside the generated response).

Two places didn't match that pattern:

1. **`DecompositionOrchestrator`'s 3-level recursive decomposition never saw source text at
   all** — not just after depth 0, at any depth. `AIService.decomposeTask(taskTitle, dueDate)`
   had no source parameter in its signature; `TaskDecompositionService.decompose(event)` called
   it with only `event.title`/`event.date.toString()`. Each recursion level fed the next only
   the previous level's own LLM output — structurally the same shape as a multi-agent handoff
   chain, compressed into recursion depth instead of sequential agents.
2. **`GroundingGuardAIService`** — the one deliberate deterministic, non-AI check that runs on
   every other generation surface (`generateCalendarEvents`, `generateStudyPlan`,
   `generateChatResponse`) — **had no `decomposeTask` override**, falling through
   `AIService by delegate` untouched. Decomposition output got zero post-hoc grounding of any
   kind.

Two enabling facts made a scoped fix cheap rather than a redesign:

* `Event.sourceId: String?` already exists and links to
  `SourceRepository.getFragmentsForSource(sourceId)` — resolving an event's origin document is
  wiring, not a schema change.
* `StudyPlanBuilder.getTaskDecompositionPrompt` already had an unused `context: String = ""`
  parameter (rendered as `<extra_context>`); no production call site ever populated it.

(A third candidate, chat critique, was investigated and ruled out — see below.)

## Decision

**Thread the originating event's source text through `decomposeTask` end-to-end**, constant
across all 3 recursion levels since every level decomposes the same origin document, not a
per-level derived summary:

* `AIService.decomposeTask(taskTitle, dueDate, sourceContext: String = "")` — new parameter on
  the interface only (Kotlin overrides inherit the default; no override redeclares it).
* `TaskDecompositionService` resolves `event.sourceId` once via an injected `SourceRepository`
  and passes the joined fragment text down; falls back to `""` on a missing link or a lookup
  failure (grounding is a quality improvement, not a hard dependency for the whole feature).
* `DecompositionOrchestrator.decompose` carries `sourceContext` as a closure-captured constant
  through every `delegate.decomposeTask` call across all recursion levels — no `WorkUnit` schema
  change, since the value doesn't vary per node.
* `GeminiAIService.decomposeTask` forwards it into the existing (previously-unused)
  `getTaskDecompositionPrompt` context slot; `getDecompositionCritiquePrompt` gained the same
  `<source_document>` block the critique loop can check sub-tasks against, plus a checklist item
  telling it to strip unsupported specifics rather than invent support for them.

**Add the missing `GroundingGuardAIService.decomposeTask` override.** It reuses
`SourceFactGrounder` — the same claim-extraction-and-lexical-check utility already applied to
chat responses — against each sub-task's `description` (not `title`: short imperative phrases
like "Draft outline" legitimately won't lexically overlap with source text, and flagging that
would be noise; `description` is where a fabricated specific — a due date, a grading weight —
would actually surface). Ungrounded claims get the same non-destructive warning treatment chat
already uses: appended to the description, not silently dropped, since a lexical check has false
positives a strict filter shouldn't act on unilaterally.

**Chat critique — investigated, not changed.** `getChatCritiquePrompt(originalPrompt, response)`
looked ungrounded from the outside (only title suggests "generated prose critiqued against
nothing"), but `originalPrompt` is the *entire* original prompt, which `getMultiSourceChatPrompt`
already built with raw retrieved fragments embedded. So chat critique already re-grounds against
source — just by echoing the whole prompt back wholesale instead of a dedicated tag like the
event path uses. That's a token-cost/hygiene difference, not a grounding gap, and is explicitly
out of scope here so it doesn't get re-litigated as the same bug next time this area is touched.

## Consequences

* Positive: decomposition and its critique pass can now be checked against the same ground truth
  as the original event extraction, at every recursion level, not just the first. The
  `GroundingGuardAIService` doc comment's claim ("no matter how many generation or critique
  passes happen internally, exactly one deterministic check runs on whatever finally comes out")
  is now actually true for `decomposeTask`, not just the other three methods.
* Positive: no schema change — `Event.sourceId` and `SourceRepository.getFragmentsForSource`
  already existed; this is wiring, not new storage.
* Negative: a guard that flags ungrounded *claims* in a sub-task description can't catch a
  sub-task that's wrong in a way that doesn't produce an extractable date/percentage claim (e.g.
  a plausible-sounding but fabricated action step). It's a backstop against the sharpest failure
  mode (copied-forward specifics), not a general decomposition-quality check.
* Negative: `EventAgent`, `TaskDecompositionService`, and `DependencyContainer` gained one more
  optional constructor parameter each (`sourceRepository`/`logger`), all defaulted to preserve
  every existing call site — no test churn beyond the mockk 2-arg → 3-arg `any()` matcher fixes
  the new `decomposeTask` parameter required (matcher calls must be all-matcher or all-literal;
  literal-only stubs were unaffected since Kotlin resolves the omitted arg via the interface
  default on both sides).

## Revisit triggers

* If a sub-task consistently reads as fabricated (a plausible but source-unsupported action
  step) without tripping the date/percentage claim check, extend `SourceFactGrounder`'s claim
  vocabulary rather than building a second bespoke checker.
* If chat critique's whole-prompt echo becomes a measurable token-cost problem (unlikely at this
  app's scale — see ADR 0013 on corpus size), revisit giving it the same dedicated
  `<source_document>` tag structure as the event and decomposition critique paths.
