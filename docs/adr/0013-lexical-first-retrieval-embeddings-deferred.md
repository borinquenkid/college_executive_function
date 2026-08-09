# ADR 0013: Lexical-First Chat Retrieval + Deterministic Deadline Digest; Embeddings Deferred

## Status
Accepted (2026-08-09)

## Context

The question that triggered this ADR: *would a vector DB (embedding-based retrieval) outperform
the existing lexical ranking (BM25 / TF-IDF) for chat over a student's course materials?*

Relevant facts about this system:

* **Corpus scale is tiny.** A student's semester is a handful of courses × a few documents. The
  retrieval eval's realistic 5-course corpus is **36 fragments** (per-page PDF fragments +
  3000-char text chunks, mirroring `SourceNormalizer`). Vector DB products (HNSW indexes, etc.)
  solve problems that appear around 10⁵–10⁶ items; at 10² the entire "DB" question is moot —
  brute-force cosine over an embeddings column in SQLDelight would be sub-millisecond. So the
  real question was only ever *embeddings vs. lexical*, never a vector DB.
* **Three lexical call sites existed:** `FragmentRanker` (TF-IDF, top-15 fragment selection for
  chat context), `ChatBuilder.selectRelevantContent` (BM25 paragraph selection inside oversized
  fragments, 6 000-char/source cap), and `SourceSnippetExtractor` (event-title → source-snippet
  grounding; lexical by construction since the titles were generated *from* the source text).
* **The owner's cost model is asymmetric** (stated 2026-08-09): a missed deadline is the
  catastrophic failure — the executive-function-impaired students this app serves outsource
  schedule memory to it and cannot self-correct a silent miss. False positives (confabulated or
  overconfident answers) corrode the credibility that makes the app usable at all. Thresholds
  were explicitly *not* to be guessed up front.

## Decision

**1. Measure before deciding — and keep the eval report-only.**
`RetrievalEvalTest`: 18 curated questions over checked-in fixture syllabi, tagged
`style` (verbatim/paraphrase) × `stake` (deadline/general), deterministic and API-free, metrics
flowing into the ADR-0004 baseline-delta harness as the `retrieval` class. No metric gates; the
2026-08-09 baseline: fragment recall@5 77.8%, recall@15 100%, **promptContainsAnswer 88.9%**
(verbatim 100%, paraphrase 75–80%). Diagnosis: the paraphrase gap lived entirely in the
*compression* stage (per-source char budget), not fragment ranking.

**2. Deadline correctness moves off retrieval entirely: the calendar digest.**
`EventsDigestBuilder` injects the student's own synced calendar into every multi-source chat
prompt — all events within 14 days unconditionally, question-matched others best-effort, hard
line/char caps, ⚠ markers preserved on reconciler-inferred events, and prompt guardrails that
(a) give the digest precedence over document prose for dates, (b) surface digest-vs-document
disagreements to the student instead of silently picking, (c) relay the ⚠ verify caveat. The
in-window guarantee is enforced by a deterministic 100% test gate (every deadline-stake eval
question, asked 3 days out, must appear — pure logic, no LLM). This is the load-bearing answer
to the asymmetric cost model: **the catastrophic failure mode no longer depends on lexical
retrieval at all.**

**3. Close the vocabulary gap lexically: query-side synonym expansion.**
`AcademicSynonyms` (curated groups: essay↔paper↔project↔assignment, exam↔quiz↔midterm,
books↔textbooks, …) expands queries in `FragmentRanker` and in the digest's title matching.
Measured effect: fragment recall@5 **77.8% → 94.4%**, MRR 0.596 → 0.624, no bucket regressed.
A negative result worth keeping: expanding the *within-fragment* chunk-selection query was tried
and **reverted** — it diluted the per-source budget toward other assignments' paragraphs
(verbatim|deadline promptContainsAnswer 100% → 80%). Within an already-relevant fragment, the
student's original words are the precise signal; expansion only earns its keep *across*
fragments and event titles.

**4. Embeddings: deferred, by the cost-anchored rule.**
The agreed decision rule: count deadline-stake paraphrase questions still missed by retrieval
*and* uncovered by the digest. After (2) + (3): the sole deadline-stake retrieval miss
("fake social media argument" → *Hypothetical Twitter Feud Project*) is covered by the digest —
deterministically when the deadline is within 14 days, and via synonym-expanded title matching
beyond it. **Residual deadline gap: zero.** The remaining misses are general-stake questions
whose failure mode is an honest "I don't know" (the prompt forbids guessing) — low severity,
which does not justify adding an embedding pipeline (per-turn embedding calls on the
quota-constrained Gemini path, ingestion-time embedding + cache invalidation, KMP-wide
integration) for a corpus of ~36 fragments.

## Revisit triggers

Embeddings (as a SQLDelight column + brute-force cosine — still not a vector DB) become worth a
spike if any of these hold:

* The retrieval eval (extended with new real-student phrasings) shows a deadline-stake question
  missed by retrieval AND uncovered by the digest — i.e. the residual gap stops being zero.
* Corpus scale changes regime (e.g. institution-wide document sets, 10⁴+ fragments).
* `promptContainsAnswerPercent` for paraphrase buckets degrades below ~70% as the question set
  grows and synonym-map curation stops keeping up (the map is plain data; extending it is the
  first resort, embeddings the second).

## Consequences

* Positive: deadline answers are deterministic; retrieval quality is a tracked number instead of
  vibes; zero new runtime dependencies, API calls, or per-platform work; the synonym map is
  cheap to extend as the eval surfaces real phrasings.
* Negative: general-stake paraphrase questions still miss ~20% of the time (answer text absent
  from the prompt → "I don't know"). Accepted as low-severity; mitigations are fixture-driven
  synonym additions, not infrastructure.
* The eval fixture is now load-bearing: new failure phrasings observed in the wild should be
  added as questions first (they turn anecdotes into metrics), fixes second.
