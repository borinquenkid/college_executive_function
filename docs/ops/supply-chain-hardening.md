# Supply-Chain Hardening — Detection, Incident Runbook, and Prevention

**Status: proposed, not started.** Proactive — written after reading a third-party report
describing a real-world attack pattern (obfuscated payloads hidden inside ordinary build-config
files like `tailwind.config.js`/`next.config.js`, beaconing to a blockchain RPC endpoint for C2,
plus tampered git history). **No compromise has been detected in this repo or on any of this
project's developer machines** — confirmed via `git log`/`git remote` review during planning; this
document is preventative, not a response to an incident. A twin plan exists for
[Oficio](../../../oficio/docs/ops/supply-chain-hardening.md) — the two repos share a developer and
an architecture pattern (Ktor + Vite frontend) but are otherwise independent; each repo's version
is grounded in what that repo's actual CI/tooling looks like today, not copied wholesale from the
other.

**What's carried over verified vs. taken on faith:** the attack *pattern* (obfuscated config-file
payloads + blockchain C2 + git tampering, allegedly missed by both a commercial AV product and
macOS's built-in protection) comes from the source article as described secondhand — it wasn't
independently re-verified against the original report. The *response* to that pattern below —
what to actually check in **this** codebase, what CI already does, what gaps exist — was
independently confirmed against this repo's real state, not assumed.

**What's actually true about this repo, checked before writing this plan (2026-07-09):**

| Item | Reality |
|---|---|
| Frontend build tooling | `web/` is **Vite** (`vite.config.ts`, `eslint.config.js`) — not Next.js/Tailwind, the article's specific example. Same threat model, different filenames to watch. |
| `main` branch protection | **None** — `gh api repos/borinquenkid/college_executive_function/branches/main/protection` returns 404 "Branch not protected." No required reviews, no required status checks, force-push and direct pushes to `main` are both currently possible. |
| CODEOWNERS | Exists (`.github/CODEOWNERS`), but scoped to the LLM-prompt-injection surface (`AiPrompts.kt`, `EventBuilder.kt`, etc., per ADR `0003-contribution-validation-and-prompt-injection-hardening`) and `.github/` itself — **does not cover** `build.gradle.kts`, `web/vite.config.ts`, `web/eslint.config.js`, or any other build-tooling config. |
| Dependency automation | No Dependabot or Renovate config anywhere in the repo. |
| Gradle dependency verification | No `verification-metadata.xml` — Gradle's built-in checksum/signature verification isn't enabled. |
| CI workflows | `eval-corpus.yml`, `pr-check.yml`, `release-desktop.yml`. `pr-check.yml` already runs Gradle build/test on every PR — the natural place to add a Detection grep step. |

That table is this plan's actual starting point — the sections below are scoped to close *these*
specific gaps, not a generic checklist.

---

## 1. Detection

Ordered by how cheap and immediately actionable each check is, not by the order the source
material presented them:

1. **Grep build-tooling config files for obfuscation markers.** Target the files that actually
   exist here: `web/vite.config.ts`, `web/eslint.config.js`, `build.gradle.kts`,
   `androidApp/build.gradle.kts`, `iosApp/` build settings, `settings.gradle.kts`, and any
   `buildSrc`/custom Gradle plugin code. Flag `eval(`, `atob(`/`btoa(`, `new Function(`, oddly-used
   `setInterval`/`setTimeout` (config files evaluate once at build time — a config file that
   schedules recurring work is itself a red flag independent of any base64), and any base64-shaped
   string literal longer than ~200 characters. Legitimate Vite/Gradle config essentially never
   needs any of these constructs.
2. **Diff and pin lockfiles.** `web/package-lock.json` already exists and is committed — good.
   Nothing currently fails a PR if it changes unexpectedly; a reviewer has to notice a lockfile
   diff manually. No corresponding Gradle lockfile/dependency-verification exists yet (see Harden
   §2).
3. ✅ **DONE 2026-07-09 — `git log` vs. `git reflog --all` divergence check.** Automated, not
   manual — see item 5 below. Author-timestamp/timezone anomaly review is not automated yet.
4. ✅ **DONE 2026-07-09 — Process audit on dev machines.** Automated as part of the same weekly
   job (item 5) — `ps aux | grep -E '[n]ode|[j]ava|[g]radle'`. **Caveat: informational only, no
   baseline yet** — it logs the count each run but doesn't know what's "normal" for this machine,
   so it can't yet flag an anomaly on its own; a human still has to glance at the log. A real
   baseline (e.g., flag if count exceeds some N, or diff against the prior run) is a future
   refinement, not built yet.
5. ✅ **DONE 2026-07-09 — Scheduled re-scan, but local (`launchd`), not a GitHub Actions
   workflow.** Originally scoped as a GitHub Actions nightly job (per the draft below, preserved
   for context); actually built as a **local `launchd` LaunchAgent** instead, because two of the
   other checks bundled into the same run — `git reflog` divergence (item 3) and the process audit
   (item 4) — fundamentally need local-machine access that neither GitHub Actions nor a cloud
   agent sandbox can provide (a fresh CI checkout has no real reflog history; a cloud sandbox has
   no visibility into this machine's processes). Bundling all three into one local job was simpler
   than splitting Detection across three different execution environments.

   **What's live:** `scripts/supply-chain-audit.sh` (this repo), covering **both** CEF and the
   sibling `oficio` repo in one run — git log/reflog divergence, build-config grep (this item's
   original scope), a ruleset-integrity check (enforcement still `active`, exactly one bypass
   actor) via `gh api`, the process audit, and opening
   `github.com/settings/installations`/`/applications` for manual review (GitHub Apps/OAuth
   auditing cannot be done headlessly — no authenticated browser session in a scheduled job).
   Registered as `~/Library/LaunchAgents/com.borinquenterrier.supplychainaudit.plist`, weekly
   (Monday 9am local), loaded via `launchctl load`. Findings write to
   `~/Library/Logs/supply-chain-audit/`; a macOS notification only fires if something is actually
   found — a clean run stays quiet.

   **Original draft scope (not built, superseded by the above):** ~~`pr-check.yml` only runs on
   `pull_request: branches: [main]` — a payload that lands via a merge without triggering PR
   checks (e.g. a direct push, currently possible per the branch-protection gap above) would never
   be caught. A nightly GitHub Actions workflow re-running the Detection §1 grep plus a
   dependency-vulnerability scan (OSV-Scanner or equivalent) against `main` would close that blind
   spot.~~ A GitHub-Actions-based dependency-vulnerability scan (OSV-Scanner) is still a real gap
   this local job doesn't cover — worth adding separately later (see Harden §4, Dependabot/Renovate,
   which is a related but distinct piece of this).
6. **Build-time network egress.** No current CI step monitors this. Lowest priority of this list —
   real, but the other five items are cheaper and address more probable failure modes first for a
   project this size.

---

## 2. Incident runbook (if Detection ever finds something — not a current action item)

This section is a **documented response plan**, not a task to execute now — nothing here should
run until Detection (§1) actually flags something. Written now so it doesn't need to be improvised
under pressure later:

1. Kill suspicious processes; don't push or deploy; disconnect the affected machine from the
   network if it's actively beaconing.
2. Rebuild from a known-clean commit/tag rather than hand-editing the compromised one. Audit the
   full history of affected files across all branches (this repo has no forks currently, per
   `pr-check.yml`'s fork-handling comment, which simplifies this step relative to a project with
   active external forks).
3. Rotate: `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`, `CEF_GEMINI_API_KEY`, `SONAR_TOKEN`,
   `OOC_TOKEN`, the newly-added `MICROSOFT_CLIENT_ID`/`SECRET` (see `.env`) — every credential
   currently in `.env`/GitHub Actions secrets, not a subset.
4. Re-image the affected developer workstation if the entry vector isn't confirmed, rather than
   selectively cleaning.
5. This project currently has a single active developer/maintainer (per CODEOWNERS — one owner,
   `@borinquenkid`), so the "notify collaborators sharing the same workspace" step from the source
   material has no other party to notify today. Revisit if that changes.

---

## 3. Harden

Ordered to fix the biggest confirmed gap first, not the source material's original order:

1. ✅ **DONE 2026-07-09.** Ruleset `Protect main` (id `18722247`) is live and active — see
   ROADMAP.md Phase 11 Task 1 for the exact rule/bypass configuration and API confirmation.

   **Enable a branch protection ruleset on `main` — with the owner able to bypass it.** This is the
   single largest gap found while grounding this plan — currently *nothing* stops a direct push or
   force-push to `main`, independent of any supply-chain concern. But `git log` shows CEF's entire
   history is direct pushes, including `release.sh`'s automated version-bump commits — a strict,
   no-exceptions PR requirement would break the existing solo-maintainer workflow immediately.

   CEF is intended to be OSS but with a permanent sole owner (no internal contributors planned —
   external contributions, if any, would only ever arrive as fork PRs, which are already
   naturally gated). So the actual requirement is: block unreviewed pushes from anyone/anything
   that *isn't* deliberately the owner, while the owner retains the ability to push directly.

   Use a **Repository Ruleset** (`repos/{owner}/{repo}/rulesets`, confirmed available on this repo
   — `gh api` returns `[]`, not a 403), not classic branch protection — rulesets support an
   explicit **bypass list** (e.g. `RepositoryRole: admin`), which is a precise, auditable way to
   grant override rather than the old blanket "administrators are exempt" checkbox. Configure:
   require a PR before merging, require `pr-check.yml`'s `build-and-test` status check to pass,
   bypass list = repository admin role (i.e., the owner account). This is more foundational than
   anything else in this list and should land first.

   **Honest limitation, not a reason to skip this:** owner-bypass protects against unreviewed
   external pushes and unreviewed PR merges. It does **not** protect against the exact scenario
   this whole plan is about — a compromised owner machine pushing malicious code as the owner is
   indistinguishable from a legitimate owner push. That's what Detection (§1) is actually for; this
   item is a review gate, not a substitute for it.
2. **Extend CODEOWNERS to cover build/config files.** Add `build.gradle.kts`,
   `settings.gradle.kts`, `web/vite.config.ts`, `web/eslint.config.js`,
   `androidApp/build.gradle.kts`, and `iosApp/` build settings to the existing
   `.github/CODEOWNERS` — same required-review treatment the LLM-pipeline files already get, same
   rationale (files that control what code executes / what CI runs), one line each, no new
   pattern.
3. **Gradle dependency verification.** Enable Gradle's built-in checksum/signature verification
   (`gradle/verification-metadata.xml`) — nothing currently checks that a transitive Gradle
   dependency hasn't changed out from under a pinned version.
4. **Dependabot or Renovate, with mandatory human review** — neither exists today. Given this is a
   solo-maintainer project (per CODEOWNERS), "mandatory human review" already exists by
   construction (every PR needs `@borinquenkid`'s review once Harden §1 lands) — this item is
   really just "turn on automated dependency-update PRs," not a new review-process design.
5. **Restrict `npm install` lifecycle scripts in CI where feasible** (`--ignore-scripts`) for
   `web/`'s build step, and consider running that build in a network-restricted CI container —
   neither is currently the case in `pr-check.yml`.
6. **Scheduled process/git-integrity checks (Detection §3–4) should stay manual for now**, not a
   blind cron job — a scheduled script that kills processes or alerts on every Gradle daemon
   would generate constant false positives on a normal dev machine. If automated later, it should
   alert a human, not auto-remediate.
7. **Developer workstation posture** (behavioral monitoring beyond traditional AV, reviewed
   VSCode/IDE extension allow-list) — noted from the source material as a real gap category, but
   deliberately left as an open item rather than a concrete task here: it's a workstation-policy
   decision, not a repo change, and needs its own scoping pass.

---

## 4. Local developer secrets via a cross-platform keychain

**Verified before recommending, not taken on faith:** `com.github.javakeyring:java-keyring` is
real, on Maven Central, currently at **1.0.4**, BSD-licensed, with working CI for macOS/Linux/
Windows keystores. **Caveat worth being explicit about:** the GitHub repo's release history looks
sparse in recent years — it's functionally stable and does what it claims, but isn't under active
development. That's an acceptable tradeoff for a small, focused API like this (a thin wrapper
around three OS-native keychain APIs doesn't need frequent updates the way a larger dependency
would), but it's a real data point, not nothing — re-check before adopting if this sits unused for
another year or two.

**Integration pattern:** a custom Gradle task (e.g. `devSecrets`) running before `bootRun`/the KMP
dev target, checking `java-keyring` for each required secret (`GOOGLE_CLIENT_SECRET`,
`MICROSOFT_CLIENT_SECRET`, `CEF_GEMINI_API_KEY`, etc.), prompting once if missing and persisting
the result so the developer isn't re-prompted, then injecting values as environment
variables/system properties for the JVM process. For `web/`'s Vite dev server (not on the JVM):
either have the same Gradle task write a gitignored, `600`-permission local env file Vite reads at
startup, or proxy the dev server through the Ktor `server/` process so raw secrets never reach the
Node process at all — CEF already has a Ktor↔React relationship via the AG-UI SSE endpoint
(Phase 6b), so a proxy path may already be closer to in place than in a project starting from
scratch.

**Scope this is (and isn't) meant for:** this replaces the current pattern of real secrets sitting
in a plaintext, gitignored `.env` at the repo root (see `.env` today — `GOOGLE_CLIENT_SECRET`,
`CEF_GEMINI_API_KEY`, `MICROSOFT_CLIENT_ID`, `SONAR_TOKEN`, `OOC_TOKEN`, etc. all currently sit
there in plaintext). Protection level varies meaningfully by OS — macOS Keychain is strong for a
signed/notarized app; Windows Credential Manager only gets real isolation when packaged as MSIX
with code-injection lockdown; Linux keyrings offer no protection beyond normal Unix file
permissions once unlocked. Treat this as a convenient single place for **low-sensitivity, dev-only**
secrets, not a hardened vault — CEF has no production secrets-manager need today (it's a local-first,
BYOK app; `CEF_GEMINI_API_KEY` in CI is the one real production-adjacent secret, and that's already
scoped to GitHub Actions secrets, not this local-dev mechanism, per the existing "CI-test-only use"
exception documented for that key).

---

## Priority note

Per direct instruction: **this hardening work is prioritized above new feature work** (i.e., above
continuing Phase 11's Outlook Calendar integration) until at least Harden §1 (branch protection)
and §2 (CODEOWNERS extension) land — those two are cheap, load-bearing, and address the single
largest confirmed gap (`main` currently accepts unreviewed direct pushes) independent of whether
the source article's specific attack pattern is ever actually relevant here.
