# Implementation Plan: Chat Retrieval Improvement (measure-first, lexical-first)

## Overview

Chat context today is built by `ContextAgent.queryAllSources` → `FragmentRanker` (TF-IDF,
top-15) → `SourceContextBuilder`, with `Bm25Ranker` compressing oversized fragments in
`ChatBuilder`. No retrieval quality metric exists, and the student's structured events DB is
never consulted for chat — every "when is X due?" answer depends on lexical fragment ranking.
This plan (1) adds a deterministic retrieval eval so claims become numbers, (2) routes
date-lookup value through the events table (deterministic, EF-trustworthy), (3) closes the
paraphrase vocabulary gap with lexical query expansion, and (4) ends with a measured
go/no-go on embeddings — recorded as an ADR either way.

Origin: 2026-08-09 session analysis concluding a vector DB is over-specified at this corpus
scale (~50–300 fragments/student) and that embeddings-vs-BM25 should be decided by a
measured paraphrase-recall gap, not intuition.

## Architecture Decisions

- **Eval is deterministic and API-free.** `FragmentRanker`/`Bm25Ranker` are pure; fixture
  PDFs (`syllabus_bdan250.pdf`, `syllabus_hist152.pdf`, STLCC set) are checked in and read
  locally by `PdfReader`. The eval is a plain jvmTest (no `IntegrationTest` suffix) so it
  runs in pr-check and locally, not just the nightly eval-corpus workflow.
- **Metrics ride the existing ADR-0004 harness.** New eval class name `retrieval` via
  `EvalBaseline.writeCurrent` + rows in `EvalBaselineComparator`, so drift shows up in the
  same `evalBaselineDelta` table as the other classes. Baseline promotion stays the manual
  `-PrecordEvalBaseline=true` flow.
- **Events digest, not intent routing.** Rather than classifying questions ("is this a date
  question?") and risking misroutes, always inject a compact token-bounded deadline digest
  (from the events DB) into the chat prompt: BM25-matched digest lines + always the next
  14 days. Deterministic, cheap, and helps exactly when fragment retrieval misses.
- **Expansion is a curated academic synonym map, not an LLM call.** essay↔paper↔project,
  exam↔test↔quiz↔midterm, homework↔assignment, due↔deadline↔submit, etc. Offline, free,
  targeted at the EF-paraphrase gap. LLM query rewrite stays out of scope (adds a call to
  the quota-constrained path for speculative gain).
- **Asymmetric cost model (owner-stated):** missing a deadline is the catastrophic failure;
  false positives erode the credibility an EF user depends on. Consequences: (1) deadline
  correctness in chat is guaranteed by the *digest* channel (deterministic, 100%-testable
  logic), never by fragment-retrieval recall; (2) the prompt must prefer the digest over
  document prose on conflict, and say so; (3) inferred events (reconciler-synthesized) keep
  their warning marker (⚠) in digest lines — inferred content is never laundered into a
  confident answer; (4) the retrieval eval launches **report-only** (DRIFT-table rows, no
  pass/fail gate) — gates are set later from observed data, not guessed now.
- **Embeddings decision is cost-anchored, not a flat threshold:** after Phase 3, count the
  deadline-bearing paraphrase questions still missed by retrieval *and* uncovered by the
  digest. If the digest covers all deadline questions, embeddings would only improve
  general-content questions (miss cost = an honest "I don't know") — a low-severity gap that
  sets a high bar for adding an embedding pipeline to the quota-constrained path. If a
  residual deadline-question gap exists, prototype an embeddings column in SQLDelight
  (brute-force cosine — explicitly **not** a vector DB product). Outcome recorded as ADR 0013
  with the measured numbers either way.
- **Seam style follows the codebase:** new `queryAllSources` parameter is nullable/defaulted
  so existing call sites and tests are unaffected (same pattern as `chatRepository` /
  `termProfileRepository` in `ContextAgent`).

## Dependency Graph

```
T1 retrieval eval fixture + harness        (foundation — everything is measured against this)
 ├── T2 baseline wiring (comparator rows + recorded baseline)
 ├── T5 synonym expansion  ──ΔT1 metric──► T7 decision gate + ADR 0013
 └── T7 (reads final numbers)
T3 EventsDigestBuilder (pure)              (independent of T1)
 └── T4 digest wiring into chat prompt
```

T3/T4 can proceed in parallel with T1/T2. T5 must land after T2 (needs a baseline to diff
against). T7 is last.

## Task List

### Phase 1: Measure (foundation)

#### Task 1: Deterministic retrieval eval harness
**Description:** New fixture `composeApp/src/commonTest/resources/retrieval_eval_questions.json`
(~15–20 entries: `question`, `expectAnyOf` [answer substrings], `style` = `verbatim` |
`paraphrase`, `stake` = `deadline` | `general`), curated against the checked-in fixture
PDFs — read the extracted fragment text first so every `expectAnyOf` provably appears in
exactly the fragment a good ranker should surface. New `RetrievalEvalTest` (jvmTest, Kotest
FunSpec): load fixture PDFs via `PdfReader` + `SourceProcessor`, build `SourceItem`s, run
`FragmentRanker.rankFragments` per question, compute recall@5, recall@15, MRR — overall and
split by `style` × `stake`. **Report-only: no threshold assertion** — the test fails only on
harness errors, never on metric values; gates are chosen later from observed data (owner is
explicitly unsure of thresholds, and the cost model is asymmetric).
**Acceptance criteria:**
- [ ] Test runs with no network/API key and is deterministic across runs
- [ ] Metrics printed per question (hit rank or MISS) and aggregated by style × stake
- [ ] Paraphrase questions genuinely avoid the source's vocabulary (reviewed by reading fragment text, not guessed)
- [ ] No pass/fail metric gate — report-only into the baseline-delta table
**Verification:** `./gradlew :composeApp:jvmTest -PunitTestsOnly=true --tests "*RetrievalEvalTest*"`
**Dependencies:** None
**Files:** new fixture JSON, new `RetrievalEvalTest.kt` — **Scope: M**

#### Task 2: Metrics + baseline wiring
**Description:** `@Serializable RetrievalEvalMetrics` (overall + per-style recall@5/@15, MRR,
question count); `RetrievalEvalTest` writes it via `EvalBaseline.writeCurrent("retrieval", …)`.
Add `retrievalRows(dir)` to `EvalBaselineComparator` mirroring `syllabusRows`. Record and
commit `evals/baseline_retrieval.json` via `-PrecordEvalBaseline=true` (human-reviewed per
ADR 0004). Round-trip serder test for the new DTO (project convention).
**Acceptance criteria:**
- [ ] `evalBaselineDelta` table shows `retrieval` rows with OK/DRIFT status
- [ ] Baseline JSON checked in; serder round-trip test passes
**Verification:** `./gradlew :composeApp:evalBaselineDelta` after a local eval run
**Dependencies:** T1
**Files:** `EvalBaselineComparator.kt`, metrics DTO, baseline JSON, comparator test — **Scope: S**

### Checkpoint 1 — Foundation
- [ ] Unit suite green; retrieval baseline numbers reviewed by human (they decide what "bad" looks like before any fix lands)

### Phase 2: Events digest (deterministic value, independent of retrieval)

#### Task 3: `EventsDigestBuilder` (pure, commonMain)
**Description:** `build(events, question, today, maxLines≈12, maxChars)` → compact digest:
one line per event (`date | category | title`), selecting (a) all events in `today..today+14d`,
then (b) `Bm25Ranker`-ranked remaining events against the question, until the budget caps.
Events carrying a `warning` (e.g. reconciler-inferred class meetings) render with a `⚠` marker
so inferred content stays visibly inferred. Returns null when there are no events. **This is
the deadline-safety channel** (per the asymmetric cost model): its standard is a deterministic
100% — for every `stake=deadline` eval question whose answer exists in the fixture events,
the digest must contain it. That is a pure logic test, not a model behavior hope.
**Acceptance criteria:**
- [ ] Near-term events always present regardless of question wording
- [ ] Question-relevant far events included; budget caps respected; deterministic ordering
- [ ] Warning-bearing events render with the ⚠ marker
- [ ] Digest hit-rate on deadline-stake eval questions = 100% (deterministic assertion — this one DOES gate)
- [ ] Pure function — no repository/clock access (caller supplies `today`)
**Verification:** new `EventsDigestBuilderTest` unit tests
**Dependencies:** None
**Files:** new builder + test — **Scope: S**

#### Task 4: Wire digest into the chat prompt
**Description:** `queryAllSources(…, events: List<Event>? = null)`; when non-null, build the
digest and pass a new defaulted `eventsDigest: String?` through
`AiPrompts.getMultiSourceChatPrompt` → `ChatBuilder` (rendered as a clearly-labeled
"Known deadlines from your calendar" block; count it in the token budget alongside
`studentProfile`). The prompt instruction states the precedence rule explicitly: **when the
calendar digest and document text disagree on a date, answer from the digest and tell the
student the document says otherwise** — grounded data beats prose, and the disagreement
itself is surfaced rather than hidden (credibility over confidence). Wire `ChatPanel`'s
call site to supply the current calendar's events.
**Acceptance criteria:**
- [ ] Existing `ContextAgent`/`ChatBuilder` tests pass unmodified (nullable-default seam)
- [ ] Prompt contains the digest when events exist, plus the digest-precedence instruction; ChatPanel supplies real events
- [ ] Digest tokens participate in `ChatBudgetAllocator` accounting (no budget regression)
**Verification:** `ContextAgent`/`ChatBuilder` unit tests + one new prompt-assembly test
**Dependencies:** T3
**Files:** `ContextAgent.kt`, `AiPrompts.kt`, `ChatBuilder.kt`, `ChatPanel.kt`, tests — **Scope: M**

### Checkpoint 2 — Digest
- [ ] Full unit suite + `:server:test` green; manually eyeball one assembled prompt (log or test print) for sane formatting

### Phase 3: Close the lexical gap

#### Task 5: Academic synonym expansion
**Description:** Curated bidirectional synonym groups (essay/paper/project/brief,
exam/test/quiz/midterm/final, homework/assignment/worksheet, due/deadline/submit/turn-in,
class/lecture/session/meeting…) applied to *query terms only* (not documents) in
`TermNormalizer.extractQueryTerms` (feeds `FragmentRanker`) and as expanded query text for
`ChatBuilder`'s `Bm25Ranker` call. IDF naturally down-weights expansion terms that appear
everywhere.
**Acceptance criteria:**
- [ ] Paraphrase recall@15 improves over the T2 baseline (report exact Δ)
- [ ] Verbatim recall@5 does not regress (guards against expansion noise)
- [ ] Synonym map is a plain data table with its own unit test (easy to extend)
**Verification:** rerun `RetrievalEvalTest`, diff against `baseline_retrieval.json` via
`evalBaselineDelta`; full unit suite green
**Dependencies:** T2
**Files:** `TermNormalizer.kt` (or new `AcademicSynonyms.kt`), `ChatBuilder.kt`, tests — **Scope: M**

### Checkpoint 3 — Measured improvement
- [ ] Human reviews before/after retrieval numbers; if paraphrase gains came at verbatim cost, tune before proceeding

### Phase 4: Decide and record

#### Task 6: Embeddings go/no-go + ADR 0013
**Description:** With final Phase-3 numbers, apply the cost-anchored rule: count the
`stake=deadline` paraphrase questions still missed by retrieval AND uncovered by the digest.
Zero residual deadline gap → ADR 0013 ("lexical-first retrieval; embeddings deferred") citing
the measured numbers, the digest as the deadline-safety channel, corpus-scale argument, and
quota/KMP costs — done. Non-zero residual deadline gap → ADR instead scopes a follow-up
spike: Gemini embedding API, vectors as blobs in SQLDelight, brute-force cosine, evaluated
against this same fixture — explicitly not a vector DB product. General-content misses alone
(cost = an honest "I don't know") do NOT justify the spike. Update ROADMAP.md either way;
re-record baseline to lock in improved numbers.
**Acceptance criteria:**
- [ ] ADR 0013 committed with the actual measured numbers in it
- [ ] ROADMAP.md reflects outcome; new baseline recorded and committed
**Verification:** docs review; `evalBaselineDelta` shows OK against new baseline
**Dependencies:** T5
**Files:** `docs/adr/0013-*.md`, `ROADMAP.md`, `evals/baseline_retrieval.json` — **Scope: S**

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Fixture questions accidentally lexical (inflates baseline, hides the gap) | High — whole plan measures the wrong thing | T1 requires reading actual fragment text while authoring; `style` labels reviewed at Checkpoint 1 |
| Synonym expansion adds noise, hurts verbatim queries | Med | Explicit no-regression criterion on verbatim recall@5; IDF damping; tune map at Checkpoint 3 |
| Digest block bloats prompt/token budget | Med | Hard `maxLines`/`maxChars` caps in T3; budget accounting assertion in T4 |
| Digest surfaces stale/wrong events (EF-trust) | Med | Digest is labeled as coming from the student's own calendar; only synced events passed by ChatPanel |
| `PdfReader` output differs across platforms/versions → eval flakes | Low | Eval is jvmTest-only; PDF fixtures are frozen files |
| 85% threshold is arbitrary | Low | It's a human decision point, not an automated gate — Checkpoint 3 review can move it with rationale in the ADR |

## Open Questions (for review before execution)

1. ~~Threshold sign-off~~ **Resolved 2026-08-09:** owner is unsure of thresholds and named the
   asymmetry (deadline miss = catastrophic; false positives = credibility damage). Retrieval
   eval is therefore report-only with gates chosen later from observed data; the only hard
   gate is the deterministic digest hit-rate (100% on deadline-stake questions); embeddings
   decision keys on residual deadline-question gap, not a flat recall number.
2. **Digest scope:** next-14-days for the always-include window, `maxLines≈12` — match your
   intuition for what an EF student needs visible per question?
3. **Should T3/T4 (events digest) ship regardless of eval outcome?** Plan assumes yes —
   strengthened by #1: the digest is now the deadline-safety mechanism, not a nicety.
