# Best-Practices Review — Task Checklist (proposed 2026-11-07)

Origin: a first-pass review of this repo against Walter's living best-practices list
(`THOUGHTS/Best_Practices.md` in his second-brain vault). It was a SAMPLED review (AGENTS.md,
CI/eval setup, retrieval baselines, ADR 0003, targeted greps), not a full audit. Every task below
is PROPOSED: per AGENTS.md's Clarify Protocol, answer the four Clarify questions with Walter before
planning or implementing any of them. Only Walter pushes.

Already settled, NOT tasks: chat retrieval (ADR 0013, embeddings deferred, deadline digest closes the
deadline gap; its eval is report-only by owner decision). Do not reopen it without a revisit trigger.

Corrections to stale facts found in the review (fix as part of T2):
- The tracked corpus is 30 PDFs (13 UT Austin fall, 6 UT Austin spring, 7 Missouri S&T, 3 + 1 STLCC),
  not 16. `ContributorPdfIntegrationTest` discovers every PDF in `contributions/`.
- Hand-labeled recall and date accuracy still cover only 2 fixture syllabi (8 expected events:
  `evals/baseline_syllabus.json`). The other PDFs get depth assertions only (>= 3 events, >= 1 non-exam,
  up to 2 file failures tolerated).

## Priority 1 — Make the rules enforceable (harness hygiene)
- [x] T1 (DONE 2026-11-07: push deny in .claude/settings.json. DECISION on the four-target build rule: NOT automated as a blocking hook. The iOS leg alone exceeds 10 min and OOMs beside Android, so a blocking Stop hook would stall every turn; enforcement stays the AGENTS.md Build Verification Protocol plus release.sh's pre-tag gate, which is the check that actually simulates fresh CI. Revisit if a cheap compile-only proxy appears): Deterministic guards instead of prose. Add a tracked `.claude/settings.json` (today only the
  local allowlist exists) with a deny rule for agent `git push` (the "Only Walter pushes" mandate; the
  documented `release.sh` exception stays because it is run by Walter). Evaluate a hook or script that
  reminds or blocks "done" without the four-target build (`:composeApp:assembleDebug :iosApp:assemble
  :server:assemble :androidApp:assembleDebug`); heavy, so decide report-only vs blocking first. AC: an
  agent attempt to `git push` is refused; the build rule has a named enforcement mechanism or a recorded
  decision not to automate it. — S
- [x] T2 (DONE 2026-11-07: 38.5KB -> ~30KB, ops moved to docs/ops/release-and-store.md, stale model/corpus/repowise lines fixed, owner+review date added; not slimmed further, mandates still large): Slim and date AGENTS.md (38.5 KB). Move Release, Store submission and Ops gotchas to
  `docs/ops/` and link them; keep mandates lean; fix stale lines (AI Strategy still says Gemini 1.5 Flash
  while the nightly eval records gemini-2.5-flash and a recent commit adds gemini-3.8-flash; verify the
  `repowise` MCP note is current); add a named owner and a next-review date; update the corpus counts above
  wherever they appear. AC: AGENTS.md holds mandates only, no stale model or corpus claims, review date set. — M
- [x] T3 (DONE 2026-11-07, worktree removed; branch was merged and clean): Remove or fence `.claude/worktrees/android-play-deploy` (a full duplicate of the repo; excluded
  from git via `.git/info/exclude` but it doubles every agent search). Verify its branch is merged first,
  then `git worktree remove`, or add a read-deny rule for `.claude/worktrees/**`. AC: repo-wide grep returns
  one copy of each file. — XS

## Priority 2 — Strengthen the evals
- [~] T4 (LABELS + HARNESS DONE 2026-11-08, NOT YET RUN LIVE, NOT IN NIGHTLY: 13 syllabi / 59 events in composeApp/src/commonTest/resources/gold/ (+2 existing fixtures = 15 files / 67 events; UT Austin 33, Missouri S&T 21, STLCC 5), scorer `GoldSet.kt`, report-only `GoldSetEvaluationIntegrationTest` writing evals/current_gold.json. Labels were read from the PDFs by Claude and need Walter's review; 3 syllabus date typos were excluded, see each file's `excluded` note. Remaining: Walter reviews labels, one live run, decide whether to add to eval-corpus.yml): Grow the hand-labeled gold set. Label expected events for 8-10 more syllabi mixed across UT Austin,
  Missouri S&T and STLCC (the 28 unlabeled PDFs are the raw material; `_local_originals` has 7 more, untracked).
  Keep it REPORT-ONLY first (owner rule: thresholds come from observed data, not guesses). AC: >= 10 labeled
  files and >= 60 expected events in the baseline-delta harness (ADR 0004), per-institution split reported. — L
- [ ] T5: Measure consistency, not just correctness (pass^k idea). `ScheduleStabilityIntegrationTest` runs one
  document 3 times; extend to N documents and report, per document, whether all runs return the same event set.
  Report-only. AC: a per-document stability row in the nightly delta table. — M
- [ ] T6: Extend the retrieval eval fixtures with 6-8 questions from the Missouri S&T and UT spring syllabi,
  so ADR 0013's documented revisit triggers are checked on the larger corpus. Report-only; no embeddings work
  unless a trigger fires. AC: question count and buckets recorded in `baseline_retrieval.json`. — S

## Priority 3 — Observability and safety audit
- [x] T7 (DONE 2026-11-07: `GenAiSpanAttributes` sets gen_ai.operation.name / provider.name / request.model / usage.input_tokens / usage.output_tokens on the gemini.http_request span; latency is the span duration; grounding-gate firing already lives on the grounding.* spans. Tested with a recording SpanScope, not an OTLP in-memory exporter): Add GenAI attributes to the Gemini call span (model, input/output tokens, latency, whether the
  grounding gate fired), following the OpenTelemetry GenAI semantic conventions; Walter's Oficio project already
  does this, so reuse its approach. AC: a test with an in-memory exporter asserts the attributes. — M
- [~] T8 (TRACE DONE 2026-11-07, ADR 0015 accepted; confirmed no confirm step, auto-push is by design; follow-ups: post-push undo summary, per-source event cap): Authorization audit of the Calendar write (Rule of Two: untrusted syllabus text + private student
  data + an external action). UNVERIFIED in the review: no human-confirm step before `pushToCalendar` was found
  in `commonMain` (confirm dialogs exist only for deletes). Trace the UI path from extracted events to the Google
  Calendar write; if nothing confirms before the write, add a confirmation or record why not (ADR). AC: written
  trace plus a test or ADR. — M

- [~] T9 (MUTATION HALF DONE 2026-11-07: scripts/mutation/pit.sh, 10 classes run, 2 real test gaps fixed (Bm25Ranker 56->84%, GeminiResponseParser 66->80%); see docs/ops/mutation-testing-pilot.md; semantic-review half not started; more packages pending): Add the verification layers you use at work to this repo. Today it has SonarQube (static) and Kover
  coverage only: no PIT mutation testing and no LLM semantic review (Walter-confirmed 2026-11-07). Coverage is not
  test quality: at Charter, mutation testing exposed unit tests that never ran and API tests that asserted no
  values. (1) Mutation testing: run Pitest through its Gradle plugin on the JVM target (start with the grounding gates, `Bm25Ranker` and the response parsers), one package first. PIT works on
  JVM bytecode; Kotlin produces extra mutants, so expect noise, filter it, and compare the survivors against
  Kover's coverage for the same package (UNVERIFIED on this stack: pilot before adopting). (2) Semantic review: a
  Claude review step on each diff against SPEC.md and the relevant ADR that lists claims it could not verify.
  DESKTOP ONLY (Walter decision 2026-11-07): run locally via a skill or slash command before Walter pushes, never
  in CI, so no Claude secret or API spend lives in GitHub Actions; it supplements the deterministic layers and never replaces them. Report-only first;
  set thresholds from observed data. AC: a per-package mutation score beside coverage, a documented survivor
  triage, and one local semantic-review run on a real diff, output saved under `tasks/` or pasted into the PR by Walter. — M

## Not worth doing now
- PDF-text injection scanning in CI (ADR 0003 rejected it; revisit only if PDF-borne injection is observed).
- Embeddings for chat retrieval (ADR 0013).
