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
5. Later, separate task: the full devSecrets Gradle task (prompt-if-missing, cross-platform)
   from supply-chain-hardening.md §4 — this plan's loader scripts are a stepping stone toward
   that, not a replacement for it
```

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
