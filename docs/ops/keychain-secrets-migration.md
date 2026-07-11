# Local Dev Secrets → macOS Keychain — Migration Plan

**Status: CEF and Oficio migrations both DONE 2026-07-09.** Scoped down from the original "devSecrets Gradle task" idea in
[`supply-chain-hardening.md`](supply-chain-hardening.md) §4 to a smaller, verified-safe first
step: move the plaintext secret *values* currently sitting in both CEF's and
[Oficio's](../../../oficio/docs/ops/keychain-secrets-migration.md) `.env` files into macOS
Keychain, with **zero application code changes**, verified by each project's own real integration
tests before and after. The full `devSecrets` Gradle task (prompt-if-missing, cross-platform via
java-keyring) stays a separate, later task — this is the minimal version that gets raw secrets off
disk now.

---

## Why no code refactor is needed

Both projects already resolve secrets through an env-var-first priority chain:

| | CEF | Oficio |
|---|---|---|
| Chain | system props → **env** → `BuildSecrets` (compiled from env at build time) → `.env` file (`AppEnv`) → `client_secret.json` | **`System.getenv`** → Gradle-parsed `.env` file (`loadEnvKey` in `server/build.gradle.kts:42-51`) |
| Where | `GoogleAuthService.jvm.kt:140-197` | `AppConfig.fromEnvironment()` (Hoplite, `AppConfig.kt:21-33`) + scattered `System.getenv("ADMIN_SECRET")` in `AdminRoutes.kt:23` |

In both, a shell environment variable set before Gradle runs is checked **before** the `.env` file
is ever read. So exporting Keychain-sourced values as env vars ahead of any `./gradlew` invocation
satisfies both chains exactly as if `.env` still had the raw value — no touching `AppConfig.kt`,
`GoogleAuthService.jvm.kt`, `AdminRoutes.kt`, or either `build.gradle.kts`.

**What this does NOT cover:** Oficio's `Cloudflare`/`NGROK_AUTHTOKEN`/`WP_*`/`OFICIO_GITHUB`/
`CLOUD_RUN_URL`/`SEED_OWNER_PHONE` keys are read only by shell scripts
(`infra/tofu.sh`, `docker-compose.yml`, `scripts/smoke-test-production.sh`), not JVM code — those
need the loader sourced before *those* scripts specifically, not before Gradle. Same underlying
mechanism (env var wins), different call sites to remember.

---

## Naming pattern (confirmed, not yet applied)

- **Service** = exact repo directory name: `college_executive_function`, `oficio`
- **Account** = exact env var name as it appears in `.env` today (no translation table)

```
security add-generic-password -s "oficio" -a "STRIPE_SECRET_KEY" -w "<value>" -U
security add-generic-password -s "college_executive_function" -a "GOOGLE_CLIENT_SECRET" -w "<value>" -U
```

Confirmed real collisions this solves: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `SONAR_TOKEN`
exist in both `.env` files with **different values** per project. No secret is genuinely shared
across both projects — every collision is same-name-different-value, not same-name-same-value —
so no third "shared" namespace tier is needed. Scales to a third project later without redesign:
new repo name = new service.

---

## Loader script design

One script per project (not shared — different key lists, different consumers):

- `college_executive_function/scripts/load-secrets-from-keychain.sh` — reads every key currently
  in CEF's `.env`, looks each up via `security find-generic-password -s college_executive_function
  -a "$KEY" -w`, prints `export KEY='value'` lines. Usage: `source
  scripts/load-secrets-from-keychain.sh` before `./gradlew`.
- `oficio/scripts/load-secrets-from-keychain.sh` — same pattern, service `oficio`, covering both
  the JVM-consumed keys (for Gradle) and the shell-only keys (for `infra/tofu.sh`/
  `docker-compose.yml` — same script, same source-before-running pattern, different downstream
  command).

**Missing-key behavior: fail loudly, no silent fallback.** `.env` is not kept as a populated
fallback — see "Why `.env` doesn't stay populated" below. If a key isn't found in Keychain, the
loader script prints which key is missing and exits non-zero rather than silently continuing with
an unset variable. A silent skip would just mean the app fails later with a confusing "why is this
null" error instead of a clear "this secret isn't in Keychain yet" one.

## Why `.env` doesn't stay populated as a fallback (revised from initial draft)

The first draft of this plan treated `.env` as a fallback to keep alive "for the transition
period." That's wrong: the entire point of this migration is getting real secret values off disk,
and a live, populated fallback file undermines that completely — anything that can read `.env`
today can still read it after "migrating," so nothing is actually gained. `.env` becomes exactly
what `.env_template` already is: key names only, no real values, ever, once a project's migration
step is done. Real values exist in exactly one place locally: Keychain.

**What Keychain actually protects against here, stated honestly, not oversold:** the loader
script's read path is `security find-generic-password`, the same CLI tool used to write the entry
— items created by `security` CLI are inherently trusted for its own later reads, so this doesn't
trigger a macOS authorization prompt the way a truly unrecognized process's first access might.
The real protection gained: Keychain entries are **not a plaintext file**, so they're immune to the
single most common commodity-malware technique — a blind filesystem grep for
`API_KEY=`/`SECRET=`-shaped strings across `/Users`, which is standard infostealer behavior (and
directly the concern that started this whole hardening pass). This does **not** protect against a
targeted attacker who specifically knows to query Keychain by service/account name — and since CEF
is a public repo, this exact naming convention is visible to anyone reading it. Real improvement
against the common, cheap attack; not a vault against a targeted one. Said plainly so it isn't
oversold later.

## Docker Compose (Oficio's `ngrok` container)

`NGROK_AUTHTOKEN` is consumed by `docker-compose.yml:40`, not JVM code. Docker Compose's
documented precedence is **shell environment variables win over `.env`-file values** for variable
substitution — so sourcing the loader script before `docker-compose up` should satisfy this the
same way it does for Gradle, without needing a separate ephemeral file. **Not yet empirically
verified** — Oficio's exact `docker-compose.yml:40` syntax for how `NGROK_AUTHTOKEN` is referenced
hasn't been read closely enough to confirm this holds; verify during Oficio's execution step rather
than assume.

## Known limitation, not hidden

**IDE-launched Gradle runs (Android Studio/IntelliJ) will not automatically inherit these env
vars** unless Android Studio itself was launched from a terminal that had already sourced the
loader script (e.g. `source scripts/load-secrets-from-keychain.sh && open -a "Android Studio" .`),
or the IDE's run configuration is manually set to source the same values. Command-line
`./gradlew` invocations are unaffected — this only matters for IDE-driven builds/runs/tests. Not
solved by this plan; flagged so it isn't discovered as a surprise later.

**Sourcing the loader script before running the full JVM test suite breaks two `OtelTracerTest`
tests.** `AppEnv.get()` (`composeApp/src/jvmMain/kotlin/com/borinquenterrier/cef/AppEnv.kt`) falls
back to real `System.getenv(key)` even when the test constructs `AppEnv(emptyMap())` — so
`ReleaseTelemetryCheck.missingSecrets`/`failureMessage` tests, which assert `CEF_OTLP_PASSWORD` is
*absent*, fail once that var is a real env var in the shell (discovered 2026-07-09 running
`checkQualityGate` right after `source scripts/load-secrets-from-keychain.sh`). Not a bug in the
migration or in `AppEnv` — it's a pre-existing test-isolation gap the migration surfaced by making
`CEF_OTLP_PASSWORD` a real, commonly-present env var for the first time. Workaround: `unset
CEF_OTLP_PASSWORD` after sourcing secrets and before running the full test suite/Quality Gate, or
run `checkQualityGate` in a shell that hasn't sourced the loader script at all (only `SONAR_TOKEN`
is actually needed for that task). Fixing `OtelTracerTest` to inject env instead of reading real
`System.getenv()` is a small, separate cleanup — not done here, flagged so it isn't rediscovered as
a mystery failure.

---

## Verification plan (per the explicit requirement: prove it with real integration tests)

**Before migrating anything**, establish a passing baseline with the *current* `.env`-based setup:

- CEF: `./gradlew :composeApp:jvmTest -PrunAITests=true --tests
  "com.borinquenterrier.cef.GoogleOAuthIntegrationTest"` — must pass using today's `.env`.
- Oficio: `./gradlew :server:integrationTest` (runs `ModelNegotiationIntegrationTest`, needs
  `ANTHROPIC_API_KEY`) — must pass using today's `.env`.

**Then, per project, in this order — redaction is the last sub-step of migration, not a separate
later phase:**
1. Migrate that project's secrets into Keychain (namespaced per the pattern above).
2. Write and source that project's `load-secrets-from-keychain.sh` (fails loudly on any missing
   key, per above — no silent fallback).
3. Re-run the exact same test command from the baseline step, now sourcing the loader script
   first instead of relying on `.env` — must still pass, proving the Keychain path is equivalent
   to the old plaintext-`.env` path, not just theoretically equivalent.
4. Only once step 3 passes: redact `.env`'s real values immediately (empty string or a `# moved to
   Keychain` comment, keep the bare `KEY=` line for discoverability — effectively converging
   `.env` toward `.env_template`'s existing shape). No transition period where both a populated
   `.env` and Keychain coexist — that window is exactly the plaintext exposure this migration
   exists to close.

**Not independently verifiable by an automated test today** (per the Oficio research — no test
exercises these with a real API call): Stripe, Twilio, Google OAuth (Oficio's copy), Cloudflare,
WordPress. These get migrated to Keychain the same way, but "did it actually still work" for these
specifically has to be confirmed by manually exercising the relevant flow once (e.g., an actual
booking/SMS round-trip for Twilio) rather than an automated green checkmark — called out explicitly
so this isn't silently assumed to be as rigorously verified as the Anthropic/Gemini-OAuth paths.

---

## Sequencing

```
1. This plan reviewed/confirmed                                            ✅ DONE
2. CEF: migrate + write loader + verify + redact .env                      ✅ DONE 2026-07-09
3. Oficio: migrate + write loader + verify + redact .env                   ✅ DONE 2026-07-09
4. Manual spot-check of the not-independently-testable secrets (Stripe/Twilio/Cloudflare/WP)
   — CEF's four done 2026-07-09 (see below); Oficio's still open
5. The full devSecrets Gradle task (prompt-if-missing, cross-platform)
   from supply-chain-hardening.md §4 — this plan's loader scripts were the stepping stone toward
   that                                                                    ✅ DONE 2026-07-10
```

## Step 4 (CEF) — manual spot-check results, 2026-07-09

All four CEF secrets without automated live-call coverage (ROADMAP.md Phase 11 Task 9) were
exercised against their real external service, sourced from Keychain via
`scripts/load-secrets-from-keychain.sh` (not the redacted `.env`):

- **`OOC_TOKEN`** ✅ — `POST {openobserve_base}/_search?type=traces` with basic auth
  `wbduque@mac.com:$OOC_TOKEN` returned `HTTP 200` with 5 real `cef-desktop` trace rows (not a
  stub/empty response). First attempt returned `401` because the loader script had been sourced
  in a separate `Bash` tool call whose exported env vars didn't persist to the next call — not a
  Keychain or credential problem, a tool-session artifact. Re-running source + curl in one shell
  invocation fixed it.
- **`CEF_OTLP_PASSWORD`** ✅ — same query as above proves it transitively: those trace rows only
  exist in OpenObserve because a real OTLP export using this password already succeeded (most
  recent trace timestamp ~1 day before the spot-check, consistent with normal desktop-app use).
- **`SONAR_TOKEN`** ✅ — `./gradlew :composeApp:checkQualityGate` (with only `SONAR_TOKEN`
  exported, per the "Known limitation" section above re: `CEF_OTLP_PASSWORD` breaking
  `OtelTracerTest`) reached the local SonarQube instance and returned `Quality Gate OK` (new
  coverage 89.3%, 0 new violations, 0% duplication).
- **`GOOGLE_CLIENT_SECRET`** ✅ — launched `./gradlew :composeApp:run` with all six secrets
  sourced from Keychain. On startup the *existing* stored Google session was already invalid
  (`Automatic token refresh failed: 401 Unauthorized` → app auto-disconnected to `Unlinked`) — a
  real, pre-existing gap (stale/expired refresh token), unrelated to this migration, consistent
  with the "OPS-11"-style shell/credential-staleness issues seen elsewhere in this doc. Walter
  then completed a real interactive Google sign-in through the app's OAuth flow; log confirmed
  `[GoogleAuth] Google Login Successful!` → `Saving tokens...` → `Validating Calendar access...`
  → `Transition: Connecting -> Linked`. The token exchange at `oauth2.googleapis.com/token`
  requires `client_secret`, and calendar access was validated with a live Calendar API call, so
  this proves the Keychain-sourced `GOOGLE_CLIENT_SECRET` value is correct and live.

**`GOOGLE_REFRESH_TOKEN` gap status:** still not set as an env var anywhere (so
`GoogleOAuthIntegrationTest` still can't run) — this spot-check didn't fix that, it used the
app's own interactive OAuth flow instead, which stores tokens in the app's local `Settings`, not
as a `GOOGLE_REFRESH_TOKEN` env var. Logged here, per Task 9's acceptance criteria, as a
separately-tracked pre-existing gap — not silently conflated with this spot-check.

**Oficio's spot-checks (Stripe/Twilio/Cloudflare/WordPress/etc.) are still open** — out of scope
for this pass; see Oficio's own `docs/ops/keychain-secrets-migration.md`.

## OOC credential rotation, 2026-07-10 — service account swap, and a new search-scope gap

The `wbduque@mac.com` / `OOC_TOKEN` pair verified in Step 4 above (2026-07-09) had gone stale by
the very next day — `POST {base}/default/traces/latest` returned `401` even after re-sourcing
`load-secrets-from-keychain.sh` fresh (ruling out the same "env didn't persist across tool calls"
artifact noted in Step 4). Walter provided a replacement credential as a raw HTTP `Authorization:
Basic <base64>` header value; decoding it (`base64 -d`, split on the first `:`) yielded a new
**service account** — `admin@borinquenterrier.com` — paired with a new token, not just a rotated
token for the same `wbduque@mac.com` login.

**Applied correctly per this doc's own established split** (line ~229 above: `OOC_USERNAME` is
deliberately plain config, not a secret):
- `.env`'s `OOC_USERNAME` updated `wbduque@mac.com` → `admin@borinquenterrier.com` directly (plain
  text, in place).
- Keychain's `OOC_TOKEN` (service `college_executive_function`, account `OOC_TOKEN`) updated via
  `security add-generic-password ... -U` to the new token.
- **A stray `OOC_USERNAME` Keychain entry was created and then deleted during this process** —
  `load-secrets-from-keychain.sh`'s `SECRET_KEYS` array never included `OOC_USERNAME` (by design),
  so writing it there was inert at best, confusing at worst. Removed via `security
  delete-generic-password -a OOC_USERNAME -s college_executive_function`. If a future session finds
  that entry again, it's a repeat of this same mistake, not an intentional addition.

**Verified working:** `GET {base}/default/traces/latest` → `200`, real trace rows returned, using
`admin@borinquenterrier.com` (from `.env`) + the new `OOC_TOKEN` (from Keychain), Basic auth,
identical mechanism to `OpenObserveQueryCli.kt`.

**Dead end, documented so it isn't repeated:** `admin@borinquenterrier.com` turned out to be a
human SSO **User** (role: Admin), not a **Service Account** — those are two distinct IAM entity
types in OpenObserve Cloud (`IAM → Users` vs `IAM → Service Accounts`, the latter literally
labeled "Programmatic access tokens for APIs"). A new Service Account can't reuse an existing
User's email either (`"User already exists"` on attempted creation), and a Service Account's
`Identifier` field is immutable after creation (its "Update" dialog only exposes `Description`).
Whatever the `o2oi_...` token paired with `admin@borinquenterrier.com` actually was, it authenticated
successfully against `/traces/latest` (200) but `403`'d on `_search` even after creating a **new**
Service Account (`developer@borinquenterrier.com`) and explicitly granting it a custom role
(`READ_STUFF`: `Logs`/`Traces`/`Metrics`/`Streams`, List+Get on all four, confirmed saved via a
fresh page reload showing 8/8 permissions checked) — still `403` on both endpoints. Root cause
unresolved; parked, not chased further. `developer@borinquenterrier.com` Service Account + its
`READ_STUFF` role are left in place in OpenObserve Cloud for whenever someone wants to pick this
back up.

**What actually fixed it:** rotating the *existing*, already-correctly-permissioned
`wbduque@mac.com` Service Account's token (`IAM → Service Accounts → 🔄 Rotate Service Token`) —
same identity that worked in Step 4 above, just a fresh secret. `.env`'s `OOC_USERNAME` reverted
`admin@borinquenterrier.com` → `wbduque@mac.com`; Keychain's `OOC_TOKEN` updated to the rotated
value. **Verified end-to-end, live:**
- `GET {base}/default/traces/latest` → `200`.
- `POST {base}/_search?type=traces` (body: `{"query":{"sql":"SELECT * FROM default WHERE
  operation_name='gemini.http_request' LIMIT 3", ...}}`) → `200`, real span rows, including
  `model: "gemini-2.5-flash"`, `operation_name: "gemini.http_request"`, `service_name:
  "cef-desktop"`, `attempt`, `http_status`, `response_bytes` — an exact match to
  `GeminiRequestExecutor.kt`'s actual span attributes. The eval-baseline model-capture work
  earlier this session (`EvalBaseline.modelUsed`) and any future OpenObserve alert on model drift
  can now both be verified against real production data, not guessed column names.

**Correction to this doc's own "Correction" above (2026-07-10, same day) and to the `~/.claude`
memory `reference-openobserve-query`:** that earlier edit removed `?type=traces` as "not a real
parameter," based on the official generic Search API reference page not mentioning it. That was
itself wrong — empirically, `POST {base}/_search` **without** `?type=traces` returns `400
{"code":20002,"message":"Search stream not found: default"}`, because this org has a stream
literally named `default` under *both* the Logs and Traces categories, and the endpoint defaults
to Logs when the type is unspecified. **`?type=traces` is required here and the original
memory/doc note was right all along** — the official docs page is just incomplete for this
same-name-different-category case, not authoritative-over-empirical-testing. Trust a live 200
response over doc-reading when they conflict; re-verify by testing, not by reading harder.

## Step 2 (CEF) — what actually happened, deviations from the draft noted

- **Verification test changed from the draft's `GoogleOAuthIntegrationTest`** — confirmed
  non-viable before touching anything: `GOOGLE_REFRESH_TOKEN` isn't actually set in `.env` (only
  stale, commented example values from past sessions), so `resolveLiveCredentials()`
  (`IntegrationTestHelpers.kt:54-62`) throws `IllegalStateException` immediately, unrelated to this
  migration. Used `AiSchedulingIntegrationTest` instead (backed by `CEF_GEMINI_API_KEY`, which
  *is* set) — both the pre-migration baseline and the post-migration re-run produced an identical
  passing result: a real Gemini API call, same model negotiated (`gemini-2.5-flash`), same 4
  events generated. Proves the Keychain-sourced env var resolves identically to the old `.env`
  value, without depending on an unrelated pre-existing gap (missing refresh token).
- **Migrated 6 secrets, not all `.env` keys**: `GOOGLE_CLIENT_SECRET`, `CEF_GEMINI_API_KEY`,
  `CEF_OTLP_PASSWORD`, `CEF_TEST_USER_API_KEY`, `OOC_TOKEN`, `SONAR_TOKEN`. Deliberately excluded
  plain config/identifiers that aren't actually secrets (`GOOGLE_CLIENT_ID` and its
  Android/iOS/Microsoft counterparts, `CEF_OTLP_ENDPOINT`/`_USER`, `CALENDAR_ID`,
  `SEMESTER_START_DATE`/`_END_DATE`, `OOC_USERNAME`) — Keychain migration exists to protect
  secrets, not general config; moving a semester date into Keychain would add complexity for zero
  security benefit.
- **Found and removed real secret exposure beyond the active keys**: stale, commented-out
  credentials for past collaborators (a "WALTER" alternate `GOOGLE_CLIENT_SECRET`, a "RODRIGO"
  `GOOGLE_CLIENT_SECRET`/`WEB_GOOGLE_CLIENT_SECRET`, and — most notably — an actual
  `GOOGLE_REFRESH_TOKEN` value for Rodrigo sitting in plaintext despite being marked "stale/do not
  use"). A refresh token doesn't expire just from being unused. These were removed (not migrated —
  nothing reads them), with a note left in `.env` suggesting the token be revoked at the source
  (Google Account → Security → Third-party access) rather than assumed inert. Not done as part of
  this session — flagged for later.
- **Scripts**: `scripts/migrate-secrets-to-keychain.sh` (one-time, idempotent via `-U`) and
  `scripts/load-secrets-from-keychain.sh` (source before `./gradlew`; fails loudly, exit 1, on any
  missing key).

## Step 3 (Oficio) — what actually happened, including an unresolved anomaly

- **Migrated 13 secrets**: `DATABASE_PASSWORD`, `TWILIO_AUTH_TOKEN`, `TWILIO_WEBHOOK_SECRET`,
  `ANTHROPIC_API_KEY`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `GOOGLE_CLIENT_SECRET`,
  `NGROK_AUTHTOKEN`, `WP_APP_PASSWORD`, `ADMIN_SECRET`, `CLOUDFLARE_API_TOKEN`, `SONAR_TOKEN`,
  `OFICIO_GITHUB`. Excluded plain identifiers (SIDs, price IDs, phone numbers, usernames, zone
  IDs, model names, URLs) for the same reason as CEF.
- **`OFICIO_GITHUB` is a real GitHub PAT, correctly unreferenced by application code** — per
  Walter, it's provisioned for agent-driven GitHub actions on behalf of this project (tooling, not
  the Kotlin server), which is why the earlier code search found zero references. Not orphaned;
  migrated to Keychain the same as any other real secret. This session's own GitHub actions
  (creating both repos' rulesets) used Walter's own `gh auth` session, not this token.
- **Found two placeholder/non-real values while reading `.env`**: `DATABASE_PASSWORD=changeme`
  and `TWILIO_WEBHOOK_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx` — migrated anyway for consistency,
  but neither was ever a real credential; noted so their presence in Keychain isn't mistaken for
  something sensitive.
- **Baseline test (`./gradlew :server:integrationTest`, `ModelNegotiationIntegrationTest`) failed
  before any migration work touched anything**: `HTTP 401` from Anthropic's API — the key in
  `.env` was rejected. Confirmed unrelated to this migration (nothing had been touched yet).
  Proceeded using "identical failure mode" rather than "passes" as the verification bar, since
  fixing an unrelated invalid credential wasn't this task's job.
- **Anomaly, root cause confirmed (not left open):** the post-migration re-run (same value,
  confirmed byte-identical to the pre-redaction `.env` value via direct diff) **passed** — `HTTP
  401` did not recur. Root cause: `~/.zshrc:175` has a stale `export ANTHROPIC_API_KEY=...` that
  permanently shadows `.env`'s value in any normal shell session — a known, previously-documented
  issue (`AGENTS.md:319-324`, "OPS-11", confirmed 2026-07-03: "Shell env vars take precedence if
  both are set — if a live run inexplicably fails every case with `401 invalid x-api-key`... check
  your shell profile for a stale exported `ANTHROPIC_API_KEY`"). The baseline run used that stale,
  broken shell value, not `.env`'s real one. `load-secrets-from-keychain.sh`'s `export` calls
  overwrote the stale shell value with the correct Keychain-sourced one for that session, which is
  why the second run passed. **Not caused by, or fixed by, this migration** — it's a pre-existing
  gap in the *shell profile*, out of scope for this migration (a dotfile, not either project's
  `.env`/Keychain). Flagged to Walter directly; not edited here.
