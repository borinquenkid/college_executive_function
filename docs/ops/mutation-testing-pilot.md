# Mutation Testing Pilot (Pitest) — 2026-11-07

Task T9 (mutation half) of `tasks/best-practices-review.md`. REPORT-ONLY: no CI gate, no threshold,
no change to the Gradle build or `gradle/verification-metadata.xml`.

## Result: `Bm25Ranker` (one class, one test class)

| Metric | Value |
|---|---|
| Line coverage (Kover/PIT, mutated classes) | 100% (57/57) |
| Mutants generated / killed | 45 / 25 |
| **Mutation score** | **56%** (test strength 58%) |

Coverage says "fully tested"; the mutation score says nearly half of the behavior changes go unnoticed.

## Survivor triage

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

- Decide whether to commit a `scripts/` wrapper (steps 1–3) or keep this as a documented manual procedure.
- Repeat on the grounding gates (`GroundingGuard*`) and the response parsers, then compare per-package
  mutation score against Kover.
- Optionally strengthen `Bm25RankerTest` with the gaps above (separate change; not done in this pilot).
