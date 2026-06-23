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

- [ ] **T6-A** `AndroidLintGradleDependency.xml` (10 issues) ▶ CURRENT
  Review each flagged dependency for deprecated/insecure versions. Bump only those with a clear safe upgrade path; leave the rest with an inline `//noinspection` comment explaining why.
  _Verify: build triple + jvmTest._

- [ ] **T6-B** `AndroidLintOldTargetApi.xml` (2 issues)
  Raise `targetSdk` to the recommended level. Check for behavior-change notes in the Android release docs for each API level crossed.
  _Verify: assembleDebug + manual smoke test on emulator._

- [ ] **T6-C** `AndroidLintAndroidGradlePluginVersion.xml` (1 issue)
  Bump AGP to the version suggested by the lint warning. Update Kotlin and Compose compiler versions if AGP requires it.
  _Verify: build triple passes._

- [ ] **T6-D** `AndroidLintNewerVersionAvailable.xml` (43 issues)
  Group the 43 upgrades by library ecosystem (Compose, KMP, AndroidX, Google, etc.). Bump one ecosystem at a time and verify the build before moving to the next. Skip any library whose new version is a major bump — file those as separate work items.
  _Verify: build triple + jvmTest after each ecosystem batch._

---

## Skipped (will not fix in this campaign)

| File | Reason |
|------|--------|
| `SpellCheckingInspection.xml` (1,615) | Too noisy; spelling is context-specific |
| `GrazieInspection.xml` + `GrazieStyle.xml` (53) | Grammar style in strings/comments; low value |
| `AndroidLintLoginCredentials.xml` (3) | Play Console policy note, not a code defect |
| `UnnecessaryModuleDependencyInspection.xml` (40) | KMP-generated module graph; not manually editable |
| `HtmlDeprecatedAttribute/RequiredAlt/UnknownTarget` (3) | HTML in template strings; no runtime impact |
