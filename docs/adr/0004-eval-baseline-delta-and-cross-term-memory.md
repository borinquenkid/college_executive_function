# ADR 0004: Eval Baseline/Delta Reporting and Cross-Term Memory for ContextAgent

## Status
Accepted

## Context
Two patterns from Anthropic's internal Managed Agents workshops (`eval-driven-agent-development`,
`agents-that-remember`) were evaluated for fit against CEF's actual architecture:

1. **Eval baseline + delta.** `eval-corpus.yml` runs `SyllabusEvaluationIntegrationTest`,
   `ContributorPdfIntegrationTest` (tolerance-gated, `maxAllowedFailures = 2`), and
   `StlccIntegrationTest` nightly against live Gemini (HARD-4/HARD-5 in `ROADMAP.md`), reporting
   pass/fail + tolerance only. A prompt or model change's effect on eval quality is invisible
   run-to-run unless it crosses the failure threshold outright.
2. **Cross-term memory.** `ContextAgent` already performs per-conversation rolling-summary
   compaction (`compactHistory`, backed by `ChatRepository`/`ChatBudgetAllocator`), but has no
   memory across conversations or academic terms — no sense of a student's recurring deadline
   cadence, course load, or extraction-category patterns from prior terms.

### Grounding evidence
To validate the cross-term design against real data rather than a synthetic fixture, six UT Austin
spring 2026 syllabi were sourced from `utdirect.utexas.edu`'s public, no-login course-docs search
and added under `contributions/tx/ut_austin/2025-2026/spring/`, chosen to genuinely recur (same
course number **and** same subject) against six of the thirteen fall 2025 syllabi already in the
corpus: `M408N`, `M427L`, `M427J`, `BIO325`, `BIO325L`, `HIS378W`.

This surfaced a finding that materially shapes the design below: **course code is not a stable
cross-term identity at this institution.** Six of the thirteen fall courses (upper-division
literature/history seminars) don't recur under the same number at all — they're rotating-topics
courses. A seventh, `BIO337`, recurs under the *same number* with a *completely different subject*
("Science and Religion from Newton to the Present" vs. the fall's research-methods topic) —
a coincidental number reuse that would have silently poisoned a naive "same course code = same
course" cross-term join.

## Decision
Adopt both patterns, adapted to CEF's actual shape, as two independent, additive changes.

### 1. Eval baseline + delta
- The three eval-shaped Kotest classes gain an optional `-PrecordEvalBaseline=true` Gradle flag.
  When set, each class writes its computed metrics (not just pass/fail) to a checked-in
  `evals/baseline_<name>.json` instead of/in addition to asserting against the existing hardcoded
  threshold.
- `eval-corpus.yml`'s nightly run always diffs its live-computed metrics against the checked-in
  baseline and posts the delta to `$GITHUB_STEP_SUMMARY`. This costs no extra Gemini calls — the
  comparison is against the static committed file, not a second live run — which matters given the
  documented free-tier quota fragility (`ROADMAP.md`'s "Observed failure mode (June 2026)").
  The delta uses a tolerance band, not exact-match, since live-Gemini metrics carry sampling noise.
- Baseline JSON files are updated only by a human running the flag locally and committing the
  result in its own reviewed PR — never auto-written by CI. This preserves the "fail loud, not
  silent" discipline (`HARD-1`): an unreviewed CI-written baseline could quietly bake in a
  regression as the new normal the same night it happens.
- This is purely additive: the existing `maxAllowedFailures` / threshold gate is unchanged.

### 2. Cross-term memory
- **Home:** a new table in the shared `AppDatabase.sq` (SQLDelight, `commonMain`) — not
  `TenantDatabaseFactory`, which is the server-only multi-tenant sharding layer (ADR 0002). The
  shared schema is what both `DriverFactory` (on-device, single-tenant) and `TenantDatabaseFactory`
  (server, per-student) build on, so one table serves both deployment shapes for free.
- **What gets written, and when:** at term boundaries only (batch, not per-message), detected by
  applying `SemesterResolver.getSemesterRange(date)` — an existing pure function — to a student's
  event *max-date* rather than to wall-clock `today` the way `WarningClassifier.activeSemesterFrom`
  uses it. (`WarningClassifier`'s own usage is deliberately `today`-based for its UI-warning
  purpose and is not reusable as-is for this data-driven trigger; see Alternatives Considered.)
  No new date-based scheduler. The written record is a small structured aggregate — course load,
  `AcademicCategory` distribution, deadline cadence by weekday, which study-plan constraints were
  actually exercised — computed by plain aggregation code over existing `Event`/
  `EventGenerationService` data. No LLM call for facts that don't require judgment.
- **Course identity guardrail:** per the grounding evidence above, cross-term aggregation keys on
  course *category*/department-level facts, never on raw course-code equality — a recurring course
  code is not evidence of a recurring course at this institution, and treating it as such would
  misattribute one term's grading/deadline pattern to an unrelated course the next term.
- **Confabulation guardrail:** a distilled fact only surfaces once a student has **≥2 completed
  terms** of data. Below that floor, no profile block is injected and behavior is unchanged from
  today (per-conversation summary only). This directly addresses the risk that one term of sparse
  data gets reported as a "recurring pattern" — the same class of problem already tracked as open
  in the study-plan confabulation gap.
- **Key guardrail:** any LLM call in this path (reserved only for qualitative pattern-mining that
  aggregation can't produce, gated at the same nightly/manual cadence as the eval corpus) resolves
  its Gemini key through the same `AIService` instance the app already uses per-tenant/per-device
  (`Settings` key on-device, per-tenant key on server via `POST /api/settings`).
  `CEF_GEMINI_API_KEY` is out of scope for this feature entirely — it is a CI-test-only secret
  (see AGENTS.md's "AI Eval Corpus Gate" section) and must never be reachable from a code path that
  runs against real student data in production.
- **Read path:** extend `ChatBudgetAllocator` with a small fixed token line item (~100-300 tokens)
  for the profile block, injected once per new conversation the same way the existing rolling
  summary is injected — not RAG-retrieved, since it's one record per student, not a corpus to
  search.

## Alternatives Considered
* **A separate long-running "memory agent" (CMA-style Dreaming daemon).** Rejected: CEF is a
  stateless-per-request client/server app (Ktor request handlers, on-device Compose app), not a
  long-running managed-agent process. The workshop's framing assumes a persistent agent process;
  here the equivalent is a batch function triggered at a detectable boundary
  (`WarningClassifier`'s semester logic), not a daemon.
* **RAG-retrieve the profile like source fragments.** Rejected: one record per student doesn't
  need ranking/retrieval — a fixed-size structured block injected directly is simpler and cheaper
  than running it through `FragmentRanker`.
* **Reusing `WarningClassifier.activeSemesterFrom` directly for the term-boundary trigger.**
  Rejected on inspection: it resolves the semester from `today` (wall clock), which suits its
  actual purpose (classifying a warning as in/out of the *current* period for display) but is
  wrong for a batch trigger that must fire based on a student's *data* reaching a new term,
  independent of when the batch job happens to run. `SemesterResolver.getSemesterRange(date)`
  underneath it is pure and date-input, so it's reused directly against event max-date instead.
* **Per-message capture instead of batch-at-term-boundary.** Rejected: the workshop's own framing
  distinguishes live/per-message capture from periodic distillation: this is explicitly the
  distillation pattern, and per-message writes would multiply DB writes for data that's only
  useful in aggregate.
* **Auto-committed CI baseline updates (Pattern 1).** Rejected: removes the human review step that
  keeps a regression from being silently absorbed as the new baseline.

## Consequences

### Positive
* Eval quality trends become visible before they cross a hard failure threshold, at zero
  additional Gemini-quota cost.
* Cross-term memory reuses proven plumbing (`ChatBudgetAllocator`, the rolling-summary injection
  pattern, `WarningClassifier`'s semester detection) rather than introducing new architecture.
* The course-identity and min-terms guardrails are grounded in an observed real-data failure mode
  (`BIO337`'s number/subject mismatch), not a hypothetical.
* Works identically on-device and server-multi-tenant since both build on the same shared schema.

### Negative
* The eval baseline JSON files are another artifact that can go stale if nobody re-records them
  after an intentional, accepted metric shift — this is a process discipline cost, not a technical
  one.
* The ≥2-terms floor means new students (or students on their first tracked term) get no benefit
  from this feature — an accepted tradeoff against the alternative of surfacing under-evidenced
  claims.
* Course-category-level (not course-code-level) aggregation is coarser and will occasionally merge
  genuinely distinct courses that share a department — accepted as the safer failure mode given the
  `BIO337` counter-example.
