# ADR 0010: Exception-Path Coverage — Lightweight Audit, Not a Mutation-Testing Pipeline

## Status
Accepted

## Context
Adapted from Oficio's ADR-0031 (same decision, same reasoning, ported
2026-07-21 — see `~/AndroidStudioProjects/playbooks/java-exception-coverage-playbook.md`
for the fully generic version). Both repos are Kotlin/Gradle KMP monorepos on
the same SonarQube-Community-local setup (`composeApp/build.gradle.kts` here,
port 9000 vs Oficio's 9001), so the core argument carries over, but the
concrete surface differs enough to re-derive rather than copy verbatim.

An external plan proposed a five-phase pipeline for closing exception-path
test-coverage gaps: PIT mutation testing (optionally licensing Arcmutate's
Kotlin mutator plugin to filter null-check/synthetic bytecode noise), an ASM
bytecode walker enumerating catch-block coverage against JaCoCo execution
data, static-analysis-based prioritization, an agent test-generation loop
gated on mutation-kill verification, and CI wiring.

Checked against this codebase specifically:

1. **Kotlin has no checked exceptions** (confirmed: zero `@Throws`
   annotations in `server/src`). Same as Oficio — there's no compiler-
   verified exception-obligation signal an ASM walker could enumerate
   against; any tooling investment still bottoms out in a curated allowlist
   of known-throwing APIs.
2. **The external-call surface lives in a different place than Oficio's.**
   This project has no Exposed usage at all — persistence is raw JDBC
   (`Connection`/`DataSource`, 7 files in `server/src`, backing the
   per-tenant SQLite backup system: `TenantDatabaseFactory`,
   `ScheduledBackupJob`, `VacuumBackupRunner`). The external-HTTP surface
   (Gemini API, Google OAuth, LTI) is almost entirely in `composeApp`'s
   Ktor `HttpClient` usage (34 files), not `server` — `server`'s only
   `HttpClient`/OkHttp reference is test-support code. Internal `throw`
   sites (5 files) and `!!` usage (5 occurrences) are a higher ratio than
   Oficio's but still a small, hand-auditable surface, not one that needs
   generated tooling.
3. **CI already runs build+test on every PR** (`.github/workflows/pr-check.yml`),
   unlike Oficio's currently CI-less setup — but it does not run the
   SonarQube quality gate, matching Oficio's local-only-Sonar stance at the
   level that actually matters for this decision (no PR-blocking static-
   analysis infrastructure to extend with PIT/mutation scoring).

## Decision

1. **No PIT, no Arcmutate, no ASM bytecode walker, no new CI job** for
   exception-path coverage. The infrastructure and (for Arcmutate)
   licensing cost isn't justified by the current surface area. Revisit only
   if the codebase grows enough that a manual audit stops scaling.

2. **Exception-path gaps are closed by a direct audit**, not a generated
   backlog:
   - `server/src`: enumerate the 7 JDBC `Connection`/`DataSource` call
     sites, confirm each is inside a `try` (or a caller that is) and closes
     resources correctly (`use {}` / try-with-resources equivalent).
   - `composeApp/src`: enumerate the Ktor `HttpClient` call sites, confirm
     network-failure paths (timeout, non-2xx, serialization failure) are
     handled explicitly rather than left to propagate into UI state
     unhandled.
   This is the Kotlin-specific equivalent of Java's compiler-enforced
   checked-exception obligation — Kotlin doesn't enforce it, so the audit
   is manual, scoped to the known list of I/O boundary calls rather than a
   general bytecode scan.

3. **No allowlist tooling for internal `throw`/`!!` sites.** At today's
   scale (5 files, 5 occurrences respectively) these are read and fixed
   directly.

4. **JaCoCo → SonarQube stays as-is** — already wired
   (`composeApp/build.gradle.kts`). No new coverage-signal infrastructure
   needed.

## Alternatives Considered
Full port of the original five-phase plan (PIT + Arcmutate + ASM enumeration
+ agent loop + CI wiring), rejected for the same reason as Oficio: the
tooling and licensing cost isn't justified by a surface this small, and this
project's existing CI already covers build/test regression risk without a
mutation-testing gate.

## Consequences

### Positive
- No new build-time dependency, no license cost, no new CI job.
- The audit is scoped to the two places I/O failure actually enters this
  codebase (`server`'s JDBC layer, `composeApp`'s Ktor client), not a
  blanket bytecode sweep.

### Negative
- The audit is a one-time pass, not a standing gate — new unguarded I/O
  call sites can slip in later, in either module. Accepted tradeoff:
  revisit as a Sonar custom rule or pre-commit check only if this recurs.
- `composeApp`'s 34-file Ktor surface is materially larger than `server`'s
  7-file JDBC surface — audit that module first if time is constrained.
