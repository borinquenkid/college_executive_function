# ALL TASKS COMPLETE

# Lint Remediation Plan

Each task below is one `/loop` iteration. Mark `[x]` when complete and move `▶ CURRENT` to the next task.
After every task that touches Kotlin/Gradle source, run the build-verification triple:
```
./gradlew :composeApp:assembleDebug :iosApp:assemble :server:assemble
```

---

## Tier 1 — Trivial mechanics (5 issues, zero risk)

- [x] **T1-A** `XmlUnusedNamespaceDeclaration.xml` (1 issue)
  Remove the unused XML namespace declaration from the flagged file.
  _Verify: file compiles, no new warnings._

- [x] **T1-B** `EmptyMethod.xml` (1 issue)
  Delete or stub-fill the empty method body. If the method is part of a public API, replace with `TODO("not implemented")`.
  _Verify: build passes._

- [x] **T1-C** `ConvertTwoComparisonsToRangeCheck.xml` (1 issue)
  Replace the two-comparison expression with the Kotlin range check (`x in a..b`).
  _Verify: build passes, logic unchanged._

- [x] **T1-D** `AndroidLintObsoleteSdkInt.xml` (2 issues)
  Remove the version-guarded blocks whose minimum SDK makes them unreachable.
  _Verify: `assembleDebug` passes._

---

## Tier 2 — Dead code (37 issues, low risk)

- [x] **T2-A** `UnusedSymbol.xml` (3 issues)
  Delete the three dead properties in `DependencyContainer.kt` (`modelManager`, `googleAuthManager`, `calendarSyncManager`).
  Check if any are wired up via DI or reflection before deleting; if so, annotate `@Suppress("unused")` with a comment instead.
  _Verify: build triple passes._

- [x] **T2-B** `AssignedValueIsNeverRead.xml` (5 issues)
  Remove or inline the dead assignments. If an assignment is inside a catch block, confirm the value isn't used for logging before removal.
  _Verify: build passes._

- [x] **T2-C** `unused.xml` (29 issues — unused parameters/declarations)
  Work through each problem entry. For each:
  - Unused parameter in a public/override method → annotate `@Suppress("UNUSED_PARAMETER")` only if removal would break the signature.
  - Unused parameter in a private method → remove it and update all call sites.
  - Unused local declaration → delete.
  _Verify: build triple passes._

---

## Tier 3 — Build & dependency cleanup (28 issues, low–medium risk)

- [x] **T3-A** `UnusedVersionCatalogEntry.xml` (13 issues)
  Remove the 13 entries from `gradle/libs.versions.toml` that are never referenced.
  _Verify: `./gradlew :composeApp:assembleDebug` — no "unresolved reference" errors._

- [x] **T3-B** `AndroidLintUnusedResources.xml` (6 issues)
  Delete the unused resource files/entries. Confirm no dynamic `R.` lookups reference them (grep for the resource names first).
  _Verify: `assembleDebug` passes._

- [x] **T3-C** `AndroidLintUseKtx.xml` (3 issues)
  Replace the flagged APIs with their KTX equivalents as suggested by each problem's `<description>`.
  _Verify: build passes._

- [x] **T3-D** `AndroidLintUseTomlInstead.xml` (6 issues)
  Migrate the six hardcoded dependency strings from `build.gradle.kts` into `gradle/libs.versions.toml` and update the build file to use the catalog alias.
  _Verify: build triple passes._

---

## Tier 4 — Same-value patterns (13 issues, medium risk)

- [x] **T4-A** `SameReturnValue.xml` (11 issues)
  For each method that always returns the same constant: if it's an override of an interface/abstract method, leave it and annotate `@Suppress`; if it's a standalone method, consider replacing it with a property or constant. Do not change method signatures in public APIs without checking all call sites.
  _Verify: build triple passes; run `./gradlew :composeApp:jvmTest`._

- [x] **T4-B** `SameParameterValue.xml` (2 issues)
  For call sites that always pass the same literal: inline the constant as a default parameter value if the method is not an API boundary, then update the call sites to omit the argument.
  _Verify: build triple passes._

---

## Tier 5 — Markdown / documentation (19 issues, zero risk)

- [x] **T5-A** `MarkdownUnresolvedFileReference.xml` (10 issues)
  All 10 are inside `web/node_modules/` — third-party READMEs. No-op; fix is to exclude node_modules from IDE inspection scope.

- [x] **T5-B** `MarkdownUnresolvedHeaderReference.xml` (2 issues)
  Both are inside `web/node_modules/` — no-op.

- [x] **T5-C** `MarkdownIncorrectTableFormatting.xml` (4 issues) + `MarkdownIncorrectlyNumberedListItem.xml` (3 issues)
  All 7 are inside `web/node_modules/` — no-op. Entire Tier 5 is node_modules false positives.

---

## Tier 6 — Dependency / API version bumps (56 issues, medium–high risk)

> Run the full test suite after each sub-task: `./gradlew :composeApp:jvmTest`.

- [x] **T6-A** `AndroidLintGradleDependency.xml` (10 issues)
  Bumped: androidx-activity 1.11→1.13, androidx-core 1.17→1.19, play-services-auth 21.0→21.6, compose-bom 2025.02→2026.06.
  Skipped: compileSdk 36→37 — Android SDK 37 not installed locally (only 34, 36, 36.1 present). Revisit once SDK 37 is available via SDK Manager.

- [x] **T6-B** `AndroidLintOldTargetApi.xml` (2 issues)
  Skipped: targetSdk bump to 37 requires compileSdk ≥ 37; SDK 37 not installed (same blocker as T6-A compileSdk). Revisit together.

- [x] **T6-C** `AndroidLintAndroidGradlePluginVersion.xml` (1 issue)
  Bumped Gradle wrapper 9.4.1 → 9.6.0 (the report flagged the wrapper URL, not AGP itself).

- [x] **T6-D** `AndroidLintNewerVersionAvailable.xml` (43 issues)
  Batched 9 ecosystem upgrades, all green:
  - Batch 1: androidx-lifecycle 2.8→2.10, kotest 5.9→6.2.1, ktor 3.0→3.5, coroutines 1.9→1.11, serialization 1.7→1.11 (MockK 1.14.2→1.14.11 also bumped here)
  - Batch 2: multiplatform-settings 1.2→1.3, datetime 0.6→0.8, dotenv-kotlin 6.4→6.5.1
  - Batch 3: okio 3.9→3.17, pdfbox 3.0.3→3.0.7, kover 0.8→0.9.8
  - Batch 4: google-calendar v3-rev20241101→v3-rev20260225, google-api-client 2.7→2.9, google-oauth-jetty 1.37→1.39
  - Batch 5: Gradle wrapper 9.4.1→9.6.0 (T6-C already covered this)
  - Batch 6: SQLDelight 2.0.2→2.3.2 — added Unit to 7 repository execute methods; rewrote GeminiModelNegotiatorTest to use real in-memory JdbcSqliteDriver (MockK NPEs on QueryResult<Long> via ValueClassSupport bug)
  - Batch 7: OpenTelemetry 1.40.0→1.63.0
  - Batch 8: Compose Multiplatform 1.10.3→1.11.1, composeHotReload 1.0.0-beta07→1.1.1
  - Batch 9: Kotlin 2.3.21→2.4.0 (highest risk — compiler, AGP, Compose compiler all upgraded together)
  Skipped: google-http-client-gson 1.44.1→2.x (major version break — separate work item)

---

## Tier 7 — Deferred blockers (requires external prerequisites)

- [x] **T7-A** `AndroidLintGradleDependency.xml` + `AndroidLintOldTargetApi.xml` — compileSdk + targetSdk 36 → 37
  Android 17 (API 37) installed via Android Studio SDK Manager. Set `android-compileSdk = "37"` and `android-targetSdk = "37"` in `libs.versions.toml`. Build clean.

- [x] **T7-B** `AndroidLintNewerVersionAvailable.xml` — google-http-client-gson 1.44.1 → 2.1.0
  No API break at call sites — build and full test suite passed without changes.

---

## Skipped (will not fix in this campaign)

| File | Reason |
|------|--------|
| `SpellCheckingInspection.xml` (1,615) | Too noisy; spelling is context-specific |
| `GrazieInspection.xml` + `GrazieStyle.xml` (53) | Grammar style in strings/comments; low value |
| `AndroidLintLoginCredentials.xml` (3) | Play Console policy note, not a code defect |
| `UnnecessaryModuleDependencyInspection.xml` (40) | KMP-generated module graph; not manually editable |
| `HtmlDeprecatedAttribute/RequiredAlt/UnknownTarget` (3) | HTML in template strings; no runtime impact |
