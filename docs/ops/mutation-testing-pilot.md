# Mutation Testing Pilot (Pitest) — 2026-11-07

Task T9 (mutation half) of `tasks/best-practices-review.md`. REPORT-ONLY: no CI gate, no threshold,
no change to the Gradle build or `gradle/verification-metadata.xml`.

## Result: `Bm25Ranker` (one class, one test class)

| Metric | Value |
|---|---|
| Line coverage (Kover/PIT, mutated classes) | 100% (57/57) |
| Mutants generated / killed | 45 / 25 |
| **Mutation score** | **56%** (test strength 58%); 84% after the exact-score tests added the same day |

Coverage says "fully tested"; the mutation score says nearly half of the behavior changes go unnoticed.

## Wider run (same day, via `scripts/mutation/pit.sh`)

| Class (JVM target) | Mutants | Killed | Score |
|---|---|---|---|
| `GroundingGuardAIService` | 56 | 18 | **32%** (13 mutants have no covering test) |
| `GeminiResponseParser` (before / after) | 93 | 61 / 74 | 66% / **80%** (`keepDominantYearWhenUngrounded` had no test; 4 added) |
| `Bm25Ranker` (before / after new tests) | 45 | 25 / 38 | 56% / **84%** |
| `SourceDateGrounder` | 17 | 14 | 82% |
| `WeekAnchorDateResolver` | 55 | 45 | 82% |
| `StudyPlanGrounder` | 13 | 11 | 85% |
| `SemesterFilter` | 11 | 10 | 91% |
| `SourceFactGrounder` | 15 | 14 | 93% |
| `RetryAfterParser` | 4 | 4 | 100% |
| `UrlListParser` | 4 | 4 | 100% |

Reading: the date/fact grounders are well tested. `GeminiResponseParser` was the real gap (an untested grounding function), now closed.
`GroundingGuardAIService`'s 32% is **mostly not a test gap**: triaged survivors are tracing attributes
(`setAttribute`/`addEvent`), `Logger.d` calls, `ResultKt::throwOnFailure` coroutine plumbing, and TIMED_OUT
negated conditionals, i.e. observability side effects. Its behavior (year filter, chat/decompose grounding,
pass-through) already has tests. T7's in-memory-exporter span tests are the right place to pin the attributes. Scores are
per class with only its own test class run; other tests may kill more. Single run, no thresholds set.

## Coverage beside mutation score (2026-11-08, fresh Kover run, same 10 classes)

| Class | Line coverage | Mutation score | Gap |
|---|---|---|---|
| `GroundingGuardAIService` | 96% (48/50) | 32% (56 mutants) | +64 pts |
| `GeminiResponseParser` | 97% (115/118) | 80% (93) | +17 pts |
| `Bm25Ranker` | 100% (21/21) | 84% (45) | +16 pts |
| `SourceDateGrounder` | 100% (40/40) | 82% (17) | +18 pts |
| `WeekAnchorDateResolver` | 100% (45/45) | 82% (55) | +18 pts |
| `StudyPlanGrounder` | 100% (15/15) | 85% (13) | +15 pts |
| `SemesterFilter` | 100% (6/6) | 91% (11) | +9 pts |
| `SourceFactGrounder` | 100% (18/18) | 93% (15) | +7 pts |
| `RetryAfterParser` | 100% (6/6) | 100% (4) | 0 |
| `UrlListParser` | 100% (2/2) | 100% (4) | 0 |

Reading: line coverage is 96-100% everywhere and does not predict the mutation score. Eight of ten classes sit
7-18 points below coverage; only the two smallest classes close the gap. `GroundingGuardAIService` is the outlier
(+64) and is mostly telemetry noise (see above), so coverage overstates test strength mainly where the code
carries observability side effects. Pitest was run on 10 hand-picked classes, not per package, with only each
class's own test class; Kover is the whole suite, so the gap is an upper bound. No thresholds are set from this
(owner rule: thresholds come from more observed data).

## Survivor triage (Bm25Ranker, first run)

Real gaps (the test only asserts the *ranking order*, never score values or edge cases):
- **BM25 arithmetic** (score formula, IDF smoothing; 7 mutants): flipping `*`/`/`, `+`/`-` still keeps
  the relevant document first. Add exact-score assertions on a small hand-computed corpus.
- **Empty `documents` guard** (`return emptyList()`): no test for an empty list.
- **`avgDocLength ... ?: 1.0` fallback and its conditionals**: no test where all documents tokenize to nothing.
- **`MIN_WORD_LEN` boundary** (`>=`): no test with a word of exactly 3 characters.

Noise (ignore): `Intrinsics::checkNotNullExpressionValue`, and `throwCountOverflow` / `throwIndexOverflow`
(Kotlin-inlined stdlib code, reported on lines past the end of the source file; the two NO_COVERAGE rows).
Kotlin generates these; the Arcmutate Kotlin plugin (commercial) would filter them.

## Pilot gotchas (all hit and solved)

1. **KMP is unsupported by the Pitest Gradle plugin**, so PIT's command-line runner is used directly on
   the compiled JVM classes.
2. **Test engine:** tests are Kotest. `pitest-junit5-plugin` discovers them through the JUnit Platform
   (confirmed: `[engine:kotest]` tests found).
3. **`--classPath` is comma-separated**, not colon-separated; a colon list silently yields "No mutations found".
4. **Add `--excludedClasses '*Test*'`**: `Bm25Ranker*` also matches `Bm25RankerTest`, which makes PIT mutate
   the tests themselves and inflates survivors.
5. **The mutation minion needs `commons-text` and `commons-lang3` on `--classPath`** (else
   `NoClassDefFoundError: StringEscapeUtils`, minion dies, run hangs ~minutes).

## Reproduce

One command: `scripts/mutation/pit.sh '<target class>' '<test class>' [reportDir]` (downloads and sha1-verifies the
jars into `~/.cache/cef-pitest`, then runs the steps below). Manual steps:

1. Compile and dump the test runtime classpath. A throwaway Gradle init script registers a task that writes
   `configurations.jvmTestRuntimeClasspath` (it triggers `compileTestKotlinJvm`); the build's
   configuration cache complains about the task but the file is still written.
2. Download, with `.sha1` verification against Maven Central: `pitest`, `pitest-entry`,
   `pitest-command-line` 1.30.2, `pitest-junit5-plugin` 1.2.3, `commons-text` 1.13.1, `commons-lang3` 3.17.0.
3. Run:
   ```
   java -cp <pitest jars> org.pitest.mutationtest.commandline.MutationCoverageReport \
     --reportDir <out> --targetClasses 'com.borinquenterrier.cef.Bm25Ranker*' --excludedClasses '*Test*' \
     --targetTests 'com.borinquenterrier.cef.Bm25RankerTest*' \
     --sourceDirs composeApp/src/commonMain/kotlin \
     --classPath "composeApp/build/classes/kotlin/jvm/test,<commons jars>,<jvmTest runtime classpath, commas>" \
     --threads 2 --outputFormats XML,HTML --timestampedReports false
   ```
   Whole run for this class: ~10 s.

## Next

- `GeminiResponseParser` remaining survivors are mostly telemetry calls and Json-builder flags; `GroundingGuardAIService` is triaged as telemetry noise.
- Compare mutation score against Kover per package; only then consider thresholds.
- Bm25Ranker gaps closed by 4 new exact-score/boundary tests in `Bm25RankerTest`.
