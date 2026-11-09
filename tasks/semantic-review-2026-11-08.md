# Semantic review: thinking policy + optional-session prompt rule (2026-11-08)

Run locally via the `/semantic-review` procedure (T9, desktop-only, never CI). Scope: product-behavior commits
`4a89b51` (per-model thinking policy) and `1893e00` (extraction prompt: skip optional sessions). Read against
AGENTS.md (Confabulation Gate, Clarify, Build Verification), ADR 0004 (baseline promotion), ADR 0013, ADR 0015.
Report only: nothing was edited because of this review.

## What the code now does

- `GeminiAIService.generateEventsFromPrompt` sends `thinkingConfig` for `PromptFamily.EVENT_EXTRACTION` only
  (family x effort table in `ThinkingPolicy.kt`; absent cell = nothing sent). Study-plan, chat and categorization
  calls are unchanged.
- `EventBuilder.getSourceEventExtractionPrompt` tells the model not to extract optional/voluntary sessions
  (tutoring, info sessions, refreshers, optional exam reviews, campus-resource events), including instructor-run
  ones, unless the document makes them required.

## Contradicts

- None found against a quoted clause.
- Nearest tension (internal to the prompt, not to a spec): the same prompt says to extract "every scheduled
  in-person or synchronous meeting" (CLASS), while the new rule says skip "optional" sessions. Checked on
  SPMS1185: the 11/19 class day titled "Optional Argumentative Speech day; rehearsal day" survives as the CLASS
  event 'Rehearsal day' (2 of 2 runs). The meeting is kept; the "Optional ..." part of the title is lost.

## Contradicts (verified by reading code, after the first pass)

- Robustness, not a spec clause: a 400 for an unsupported thinking field is categorized as a generic `OtherError`
  (`ErrorCategorizer.kt`: no 400 case except `API_KEY_INVALID` / "response modalities"), which
  `GeminiRequestExecutor` retries 5 times on the SAME model (~31 s backoff), then blacklists the model for 1 hour
  and fails that extraction. Because `ThinkingMatrix` applies a cell to a whole family by name prefix, an unprobed
  `gemini-3*` model that rejects `thinkingLevel: low` would fail one user's extraction and be blacklisted for an
  hour before the next model is tried. The "absent cell can never trigger a 400" KDoc is true only for models
  outside the listed families.

## Could not verify

- `ThinkingPolicy.kt` KDoc: "Every listed cell was confirmed against the live API." The probe covered only
  gemini-3.8-flash, 3.6-flash and 2.5-flash. The cells apply by family, so `gemini-3.5-flash` (last HEAVY
  preference), any other `gemini-3*` model chosen by the negotiator's fallback, and `gemini-2.5-pro` (budget 1024)
  were never probed. The claim is true for three models and unproven for the rest of the family.
- Commit message for `1893e00`: "false positives 10-11 -> 0, stability 6-16 -> 6 events". Evidence is 4 gold runs
  and 1 stability run (3 extractions) on ONE file with negative labels, on gemini-3.8-flash only. Direction is
  clear; the size of the effect elsewhere is not measured. gemini-3.6-flash (the fallback) was not run with the
  new rule.
- "Recall 100%" in the same message: the gold set labels 56 events and, by design, does not label CLASS meetings
  or optional items, so it cannot show that no real event was dropped by the new rule beyond the 56.
- `docs/ops/thinking-policy.md` probe table: single request per cell with a simplified prompt; the doc says so, but
  the event counts differed between cells (32-39) and were not evaluated for quality.
- ADR 0004 says baselines are promoted only by a human running the flag and reviewing. `baseline_retrieval.json`
  was re-recorded in `afa26cf` (recall@5 94.4% -> 61.5%) in a session where the numbers were shown, but it is a
  local unpushed commit: Walter's review at push time is the real gate.
- Date typo behavior (observed, not changed here): SPMS1185 says "Online Speech due by 5 pm on Friday, Nov. 21"
  but Nov 21, 2026 is a Saturday. The extractor returns Friday 11/20 in these runs and 11/21 in others (T5 flagged
  11/20 and 11/21 as unstable). No warning is attached either way.

## Untested behavior

- That EVENT_EXTRACTION requests carry the LOW thinking config while STUDY_PLAN requests do not. `ThinkingMatrixTest`
  covers the table and the request builder; nothing asserts the family-to-effort choice in `generateEventsFromPrompt`
  or that the per-attempt `model` argument is the one actually negotiated after a fallback.
- The prompt rule itself. The only checks are live: the gold eval (report-only by owner rule, asserts nothing) and
  the stability run. If the sentence were deleted, no test fails. This is consistent with ADR 0004's report-only
  stance but is a gap for a behavior change.
- `evalServiceName` is unit-tested, but `ensureTracerInitialized` (private) using it is not; a revert would not fail
  a test (the OpenObserve alert filter depends on the name).
- Study-plan prompt: unchanged and so uncovered by the new rule; whether study plans surface optional sessions was
  not checked.
