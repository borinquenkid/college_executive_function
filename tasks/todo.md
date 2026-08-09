# Chat Retrieval Improvement — Task Checklist

See [plan.md](plan.md) for full task specs, acceptance criteria, and rationale.

Cost model (owner, 2026-08-09): deadline miss = catastrophic; false positive = credibility
damage. Retrieval metrics are REPORT-ONLY (no gates yet); the digest's deterministic 100%
deadline coverage is the only hard gate.

## Phase 1 — Measure (report-only)
- [x] T1: Deterministic retrieval eval (`retrieval_eval_questions.json` + `RetrievalEvalTest`, style × stake splits, no metric gate) — M
- [x] T2: Metrics DTO + `EvalBaselineComparator` rows + recorded `baseline_retrieval.json` — S
- [x] **Checkpoint 1** (2026-08-09 baseline): recall@5 77.8%, recall@15 100%, MRR 0.596, promptContainsAnswer 88.9% (verbatim 100%, paraphrase|deadline 75%, paraphrase|general 80%) — the gap is entirely in ChatBuilder's per-source compression on paraphrase questions, not fragment ranking

## Phase 2 — Events digest = deadline-safety channel (parallel-safe with Phase 1)
- [x] T3: `EventsDigestBuilder` pure builder + ⚠ marker for inferred events + 100% deadline-coverage assertion — S
- [x] T4: Wire digest through `queryAllSources` → `AiPrompts` → `ChatBuilder`, with digest-over-prose precedence rule in prompt (wired via DependencyContainer eventsProvider — no ChatPanel change needed) — M
- [x] **Checkpoint 2:** full unit suite + `:server:test` green; digest 100% coverage gate passing

## Phase 3 — Lexical gap
- [x] T5: Academic synonym expansion — applied to FragmentRanker ONLY; expanding ChatBuilder's chunk selection was tried and reverted (diluted per-source budget, verbatim|deadline 100→80). Final: recall@5 77.8→94.4, MRR 0.596→0.634, no regression; promptContainsAnswer unchanged (2 residual stage-2 misses, both zero query-answer vocabulary overlap)
- [x] **Checkpoint 3:** measured, verbatim did not regress (after the stage-2 revert)

## Phase 4 — Decide
- [ ] T6: ADR 0013 via cost-anchored rule (residual deadline-question gap after digest+expansion, not flat recall), ROADMAP.md update, re-record baseline — S — **awaiting owner review of numbers**
