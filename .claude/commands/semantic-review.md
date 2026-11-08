---
description: Desktop-only semantic review of the current diff against SPEC.md/AGENTS.md and relevant ADRs
---

Semantic review of the current change (default: `git diff main...HEAD` plus uncommitted changes; or the
target in $ARGUMENTS). This is a LOCAL, desktop-only check (T9, Walter decision 2026-11-07): never run it in
CI. It supplements the deterministic layers (tests, Sonar, Pitest) and never replaces them.

1. Read the diff, then `SPEC.md` if present, `AGENTS.md` mandates, and every ADR in `docs/adr/` whose topic the
   diff touches (grep the ADR titles/keywords against the changed files).
2. For each behavior change, state: what the code now does, which spec/ADR/mandate clause covers it, and whether
   a test would fail if that behavior regressed (name the test, or say none exists).
3. Output exactly three lists, no padding:
   - **Contradicts**: changes that conflict with a spec/ADR/mandate (quote the clause).
   - **Could not verify**: claims in comments, commit messages or docs that the code/tests don't demonstrate.
   - **Untested behavior**: changed behavior with no failing-if-broken test.
4. Report only; do not edit files, commit, or push.
