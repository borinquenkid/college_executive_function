# SonarQube — Local Setup

**Status: adopted (2026-07-05), local-only.** Replaces `CrapIndexReporter`/`CRAP.md`/`COVERAGE.md`
entirely — real AST-based Kotlin analysis (Cognitive Complexity, duplication, maintainability
rating, coverage import) instead of a hand-rolled regex/brace-counting proxy. Config and rationale
ported from the sibling `oficio` project, which made this call first and traced the old tool's
overcounting to two mechanical bugs (double-counted `?.let{}` safe-call guards, and English words
like "for"/"when" inside comments matched as control flow) — see that project's own
`docs/ops/sonarqube-local.md` for the full before/after table. Not wired into CI, no persistent
server yet — that (and any move to a cloud-hosted instance) is deferred until it's needed, not
before.

---

## First-time setup

### 1. Start the container

```bash
docker compose up -d sonarqube
```

First boot takes **1–3 minutes** (Elasticsearch cluster formation + index creation inside
the container) — don't assume a hang. Watch it come up:

```bash
docker compose logs -f sonarqube
```

Ready when you see `SonarQube is operational` in the logs, or once
`docker compose ps` shows `sonarqube` as `healthy`.

### 2. Log in and rotate the default password

Open http://localhost:9000 — default credentials are `admin` / `admin`. SonarQube forces
a password change on first login; do it.

### 3. Generate a local analysis token

**My Account → Security → Generate Token.** Name it something like `local-gradle`, type
"User Token", no expiration (this is a local-only trial instance, not a shared secret).

Add it to the repo-root `.env` (gitignored, never committed):

```
SONAR_TOKEN=sqp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

`composeApp/build.gradle.kts`'s `loadEnvKey()` reads this the same way it already reads
`GOOGLE_CLIENT_ID` etc. — no shell export needed.

### 4. Run an analysis + Quality Gate check

```bash
./gradlew :composeApp:checkQualityGate
```

One command — chains `jvmTest` → `koverXmlReportJvm` → `sonar` → polls the Compute Engine task
→ checks the Quality Gate, printing each condition and hard-failing the build if it's not
OK (see `SonarQualityGateChecker.kt`, `composeApp/src/jvmTest/kotlin/.../SonarQualityGateChecker.kt`).
This is the mandatory-every-phase check per AGENTS.md's "Static Analysis Quality Gate"
section — replaces the old `generateCrapReport`/`refreshCrap` steps.

Results appear at http://localhost:9000/dashboard?id=cef-composeApp (or whatever
`sonar.projectKey` is set to in `composeApp/build.gradle.kts`).

---

## Known gotchas

- **`vm.max_map_count` (Elasticsearch requirement, ≥ 262144).** SonarQube bundles
  Elasticsearch, which refuses to start below this. Docker Desktop for Mac's own VM default
  already satisfies this — no action needed on this machine. If your own machine's first boot
  fails with an ES bootstrap-check error, this is the cause; on Linux, `sudo sysctl -w
  vm.max_map_count=262144` on the host.
- **Docker memory allocation.** SonarQube's embedded Elasticsearch wants real headroom —
  4GB+ recommended for Docker Desktop's overall allocation.
- **Pinned image tag.** `docker-compose.yml` pins `sonarqube:26.6.0.123539-community` —
  not `:latest`/`:community` (both floating tags), matching this repo's existing
  precedent of pinning image tags after prior floating-version incidents.
- **`curl` vs `wget` in the image.** On the pinned `26.6.0.123539-community` tag, `wget`
  is absent and `curl` is present — `docker-compose.yml`'s healthcheck uses `curl`
  accordingly. Don't trust the widely-cited docker-library reference healthcheck (which
  assumes `wget`) if you re-pin to a different tag; verify which tool is actually present
  first.
- **Coverage import is silent-failure-prone, not fail-loud.** Sonar's JaCoCo-XML coverage
  import (used here to read Kover's `reportJvm.xml` output) can silently show 0% coverage if
  the path is wrong or the report is stale — it won't error the scan. If a file you know is
  covered shows 0% in the dashboard, check that `koverXmlReportJvm` actually ran first.

## Scope

`:composeApp` only — matches the retired `CrapIndexReporter`'s own scope (it only ever
scanned `composeApp/src/jvmTest/kotlin`'s classpath, and `CRAP.md` only ever reported on
composeApp files). `sonar.sources` covers `src/commonMain/kotlin` and `src/jvmMain/kotlin`
(the KMP source sets that actually feed the JVM/desktop build Kover instruments);
`sonar.tests` covers `src/jvmTest/kotlin`. `:server`, `:shared`, `:androidApp`, and
`:iosApp` are not scanned — none of them had Kover coverage wired up for CRAP either.

## Stopping / resetting

```bash
docker compose stop sonarqube        # keep data, stop the container
docker compose down                  # stop everything (docker-compose.yml's other services too)
docker volume rm college_executive_function_sonarqube_data \
  college_executive_function_sonarqube_extensions college_executive_function_sonarqube_logs
                                      # full reset — wipes analysis history, re-triggers
                                      # first-boot setup (including the admin/admin
                                      # password prompt) next time
```
