# Reliability & Recovery Plan

Working branch: `fix/conceptual-event-dedup` (not yet pushed).

Theme: the happy path worked but had **no immune system** — once state drifted (duplicates,
out-of-term events, sync churn, remote pollution) nothing detected or recovered it, and testing
meant launching the JVM app by hand. This plan tracks the work that fixes that and what remains.

---

## 1. Done on this branch

| # | Area | Change | Verifies |
|---|---|---|---|
| 1 | Dedup | Collapse cross-prompt-family conceptual duplicates; strip leaked `CATEGORY:` title prefixes | `EventDeduplicatorConceptualTest` |
| 2 | Calendar view | Apply active-semester window to the view (remote STUDENT events bypass ingestion) | `SemesterViewFilterTest` |
| 3 | Sync | Idempotent remote writes (reuse stable id → PUT/upsert) — stop duplicate accumulation | `GoogleCalendarSyncServiceBranchTest` |
| 4 | Ingest | OCR image-only PDFs via Gemini document vision (`inlineData`) | `PdfVisionFallbackTest` |
| 5 | Pipeline | Auto-push loaded docs (context→extract→push); semester filter moved to generation choke point | `EventGenerationServiceSemesterTest` |
| 6 | Cache | Analysis cache at the generation choke point (both paths); version-salted; pre-filter payload | `EventGenerationCacheTest` |
| 7 | **Test isolation** | Tests no longer write to the real `cef.db` (`injectedDatabase`) | full suite stays DB-clean |
| 8 | Perf | No auto-decompose; study plan on-demand; single critique pass (configurable) | `SourceProcessingPipelineTest`, `CriticActorAIServiceTest` |
| 9 | UI | Determinate progress bar for multi-step; honest single-batch spinner; "Generate Study Plan" label | `ProgressFractionTest` |
| 10 | **Detect+recover** | `CalendarReconciler` + Settings "Check & Repair" (duplicates / out-of-term / stale timestamps) | `CalendarReconcilerTest`, `CalendarAgentReconcileTest` |
| 11 | Sync churn | Stamp `updatedAt` at generation (root cause of "remote overrides local" forever) | scenario |
| 12 | **Resilient reset** | `ResilientCalendarCleaner`: retry rate-limited deletes, continue past failures, resumable | `ResilientCalendarCleanerTest` |
| 13 | **Test harness** | `PipelineScenarioHarness` + `FakeRemoteCalendar` (seedable, fault-injectable) + scenario/permutation matrix | `PipelineScenariosTest` |

Memory saved: [tests wrote to the real cef.db](.claude memory `bug_tests_wrote_real_db`).

---

## 2. Definition of Done for this branch (before merge)

- [ ] Regenerate CRAP (`koverXmlReportJvm generateCrapReport --rerun-tasks -PunitTestsOnly=true`) and commit `CRAP.md`.
- [ ] 3-target build check: `compileKotlinJvm` + `compileDebugKotlinAndroid` (together), `compileKotlinIosSimulatorArm64` (separately — OOM).
- [ ] Full unit suite green (the `AppControllerTest > retryLocalOnly …` timing test is **flaky** — see item T1; passes in isolation).
- [ ] One clean JVM replay: reset → load 3 docs → verify auto-push, no dup accumulation, no rate-limit stall, in-semester.
- [ ] Push branch + open PR only when the user says so.

---

## 3. Next work (prioritized)

### P0 — finish hardening what shipped
- **R1 — Wire the reconciler into periodic sync (self-heal). ✅ DONE.** `CalendarAgent.synchronize` now runs `selfHeal` after reconciling local↔remote: auto-applies the *safe* fixes (exact duplicates, `updatedAt=0` stamps) and records out-of-term drift in `pendingOutOfSemester` for user review (never silently deleted). Heal failures are caught so they can't break sync. Covered by `PipelineScenariosTest` (direct + sync-triggered).
- **T1 — Fix the flaky `AppControllerTest` retryLocalOnly timing test. ✅ DONE.** Root cause: the init retry-collector ran on `AppController`'s own `Dispatchers.Main` scope, and the tests raced on `delay(300)`. `AppController` now takes an injectable `scope`; the three retry tests supply `Dispatchers.Unconfined` (collector runs synchronously on the setter thread) and cancel it after — no delays, deterministic (verified green across repeated `--rerun-tasks`).

### P1 — deepen detection & recovery
- **R2 — Startup integrity check.** On launch, run `CalendarReconciler.analyze` and, if `!isClean`, log + optionally badge the Repair button. Cheap early warning. Test: seed drift → construct app services → assert a health flag.
- **R3 — Source→event link for orphan detection.** `EventEntity` has no `sourceId` FK (deletion is a fragile id-prefix match). Add a nullable `sourceId` column + migration, set it at generation, then extend `CalendarReconciler` to flag events whose source no longer exists. Deferred deliberately until the FK exists — do not ship heuristic orphan deletion.
- **R4 — More fault permutations in the harness.** Use `FakeRemoteCalendar` hooks: partial-sync failure, conflicting remote edit vs local, list-fails-then-recovers, save succeeds but status-update fails. Each becomes a `PipelineScenariosTest` case.

### P2 — remaining functional gaps
- **F1 — Large / multi-page image PDFs.** Inline `inlineData` caps ~20 MB; add the Gemini Files API path for bigger scans (out of scope of the current fallback).
- **F2 — Grounding in the harness.** The harness scripts the LLM directly, so `GroundingGuard`/`CriticActor` decorators aren't exercised end-to-end. Add a harness mode that wraps `ScriptedAIService` in the real decorator chain to cover confabulation defense in scenarios.
- **F3 — Push/retry telemetry.** Emit OTEL spans/counters for pushes, retries, reconcile actions so drift is observable in OpenObserve, not just logs.

---

## 4. Test strategy (how to add coverage cheaply)

Prefer **headless scenarios** over manual JVM runs. Pattern:

```kotlin
val h = PipelineScenarioHarness()          // real agents, in-memory DB, fake remote, live mode
h.seedLocal(...) / h.seedRemote(...)        // start from ANY intermediate state
h.remote.beforeDelete = { ... throw ... }   // inject a failure
h.ingest(title, scriptedEvents)             // or sync / reconcile / repair / reset
h.localEvents(); h.remoteEvents()           // assert both stores
```

Rules of thumb:
- New pipeline behavior → add a `PipelineScenariosTest` case (and a permutation row if it interacts with initial state).
- New failure mode → add a `FakeRemoteCalendar` fault hook scenario.
- Never construct a `DependencyContainer` in a test without `injectedDatabase = createTestDatabase()` (see the real-DB corruption bug).

---

## 5. Known constraints / deliberately deferred

- **Orphan detection** needs the `sourceId` FK (R3) — no heuristic deletion until then.
- **Reconciler deletion is conservative** — exact (canonical title + date) duplicates only; near-dup collapsing stays at generation.
- **`updatedAt` semantics vs Google's server timestamp** — stamping fixes the `=0` pathology; a deeper last-writer-wins policy is out of scope unless churn reappears.
- **Grounding decorators** are not covered by the scenario harness (F2).
