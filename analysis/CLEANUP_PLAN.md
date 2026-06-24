# Post-Lint Cleanup Plan

Leftover items after the lint remediation campaign (Tiers 1–7). Each task is one `/loop`
iteration. Mark `[x]` when complete and move `▶ CURRENT` to the next task.

Verification triple (run after every task that touches source or dependencies):
```
./gradlew :composeApp:assembleDebug :server:assemble :composeApp:jvmTest
```

---

## Tasks

- [ ] ▶ CURRENT **C1** — Migrate `runComposeUiTest` to v2 API in `StudioPanelTest` and `UniversalHomeLayoutTest`

  **Why**: Compose Multiplatform 1.11.1 deprecated the v1 `runComposeUiTest` in favour of
  `androidx.compose.ui.test.v2.runComposeUiTest`, which uses `StandardTestDispatcher` by
  default and better reflects production coroutine behaviour. 14 call sites across two files
  produce deprecation warnings on every build.

  **Files**:
  - `composeApp/src/jvmTest/kotlin/com/borinquenterrier/cef/StudioPanelTest.kt` (10 occurrences)
  - `composeApp/src/jvmTest/kotlin/com/borinquenterrier/cef/UniversalHomeLayoutTest.kt` (4 occurrences)

  **Steps**:
  1. Change the import from `androidx.compose.ui.test.runComposeUiTest` to
     `androidx.compose.ui.test.v2.runComposeUiTest` in both files.
  2. Run the full test suite — coroutine timing differences in v2 may require adding
     `advanceUntilIdle()` calls where tests relied on eager dispatch.
  3. Fix any failing tests, then verify no deprecation warnings remain for these two files.

  _Verify: `./gradlew :composeApp:jvmTest` — all tests green, no `runComposeUiTest` deprecation warnings._

- [ ] **C2** — Retry MockK bump 1.14.9 → 1.14.11 (latest)

  **Why**: MockK was reverted from 1.14.11 → 1.14.9 during SQLDelight 2.3.2 debugging because
  of a `ValueClassSupport` NPE. That root cause was `GeminiModelNegotiatorTest` mocking
  `AppDatabaseQueries` methods returning `QueryResult<Long>` — now fixed by switching to a
  real in-memory SQLite database. The MockK bump should be safe to retry.

  **Steps**:
  1. In `gradle/libs.versions.toml` set `mockk = "1.14.11"`.
  2. Run `./gradlew :composeApp:jvmTest`.
  3. If any test fails with a MockK `ValueClassSupport` NPE, identify the new culprit mock and
     apply the same fix (replace with a real in-memory DB or use behavioral assertions).

  _Verify: `./gradlew :composeApp:assembleDebug :server:assemble :composeApp:jvmTest` — all green._

- [ ] **C3** — Semantic audit of `google-http-client-gson` 2.x call sites

  **Why**: `google-http-client-gson` was bumped from 1.44.1 → 2.1.0 (a major version) and the
  build passed cleanly, but no one verified that the existing call sites are using the 2.x API
  correctly. The 2.x release dropped `GsonFactory.getDefaultInstance()` and changed several
  transport and JSON parsing behaviours.

  **Steps**:
  1. Run: `grep -rn "GsonFactory\|google-http-client\|HttpTransport\|NetHttpTransport" composeApp/src/ server/src/`
  2. For each call site, verify it is compatible with the 2.x API (check the
     [migration guide](https://github.com/googleapis/google-http-java-client/releases)).
  3. Fix any deprecated or removed API usages.
  4. Run the full build and test suite.

  _Verify: `./gradlew :composeApp:assembleDebug :server:assemble :composeApp:jvmTest` — all green, no 2.x API warnings._

---

## Skipped

_(none yet)_
