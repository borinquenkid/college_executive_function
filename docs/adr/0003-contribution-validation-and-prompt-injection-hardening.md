# ADR 0003: Contribution Content Validation & Prompt-Injection Hardening

## Status
Accepted

## Context
Two external AI-generated security reviews (`LLM_IMPROVEMENT_1.md`, `LLM_IMPROVEMENT_2.md`, not part of this repo's tracked docs) raised concerns about LLM poisoning / prompt-injection risk via the `contributions/**` corpus and the source-ingestion prompt pipeline (`AiPrompts` → `GeminiAIService`). On verification against the current code, several of their specific claims did not hold up:

- Claimed `scripts/validate_contributions.py` is missing `import re`/`import os` and would crash. **False** — both imports are present and the script fails closed (`sys.exit(1)`) on any violation.
- Claimed there's no semantic allowlist on model output. **False** — `AcademicCategory.valueOf(raw.category)` (`GeminiResponseParser.kt`, `CriticJsonCodec.kt`) strictly validates category against the enum, falling back safely on anything else. Dates parse with a safe fallback too.
- Claimed confabulated/fabricated events aren't guarded against. **False** — AGENTS.md's "Confabulation Gate Protocol" mandates `GroundingGuardAIService` gating for every structured-output `AIService` method, already implemented as year-grounding + `SourceDateGrounder` (date-must-appear-in-source) + `StudyPlanGrounder` (anchor grounding) + `SourceFactGrounder` for free-text chat/analysis output.

However, verifying the reviews also surfaced a **real, currently-broken** defect they missed: `validate_contributions.py`'s `PATH_PATTERN` only matches file names ending in `.txt`, but every file actually committed under `contributions/` is a `.pdf` (16 real syllabi/calendars from STLCC and UT Austin). Running the validator against the real repo confirms every contribution fails path validation, and the last recorded run of `pr-check.yml` on `main` (`2026-06-30`) is `failure`. This means:
- CI's `validate-contributions` job is red on `main` right now.
- The poison-content regex scan (`POISON_PATTERNS`) has **never actually executed** against real corpus content — PDFs fail the path check before content is opened, and would fail the UTF-8 plain-text decode step even if the path check were fixed, since they're binary.

Separately confirmed gaps the reviews correctly identified:
- No `CODEOWNERS` file — no required review gate on `contributions/**`, the prompt-builder/parser Kotlin files, or `.github/workflows/**`.
- `POISON_PATTERNS` only covers technical exploit strings (shell/SQL/script injection), not natural-language instruction-injection phrasing (e.g. "ignore previous instructions").
- The prompt builders (`EventBuilder`, `ChatBuilder`, `CategorizationBuilder`, `StudyPlanBuilder`) already delimit untrusted source text with XML-style tags (`<source_fragment>`, `<source_syllabus_document>`) and forbid inventing events not in the source, but never explicitly tell the model that content inside those tags is inert data, not instructions to follow.

Also confirmed: neither the Python CI tooling nor the Kotlin app itself does local PDF text extraction anywhere — `GeminiFileUploader` sends raw PDF bytes straight to Gemini's file/vision API. Adding real poison-content scanning of PDF text in CI would require introducing a new dependency (e.g. `pypdf`) purely for that purpose, duplicating parsing logic the app doesn't otherwise have.

## Decision
1. **Fix `PATH_PATTERN`** in `validate_contributions.py` to accept the file types the corpus actually uses (`.pdf`, `.txt`, `.ics`) instead of `.txt` only, restoring a passing CI gate.
2. **Scope content-level `POISON_PATTERNS` scanning to text-decodable formats** (`.txt`, `.ics`) only. For binary formats (`.pdf`), skip the content scan explicitly and log why, rather than silently failing UTF-8 decode. The runtime **Confabulation Gate Protocol** (year/date/anchor/fact grounding in `GroundingGuardAIService`) remains the actual defense against malicious or fabricated content reaching a student's calendar — this CI check is a namespace/size/format gate, not a content-safety gate, for binary contributions.
3. **Extend `POISON_PATTERNS`** with a small set of natural-language instruction-injection phrases (e.g. "ignore previous instructions", "disregard the above", "system prompt", "you are now") for the text formats that are actually scanned.
4. **Add an explicit "untrusted data, not instructions" line** inside each prompt builder's existing XML-delimited source block (`EventBuilder`, `ChatBuilder`, `CategorizationBuilder`, `StudyPlanBuilder`) — one line added to instructions already in place, not a redesign.
5. **Add a `CODEOWNERS` file** requiring review on `contributions/**`, the prompt-builder/parser Kotlin sources, and `.github/workflows/**`.

## Alternatives Considered
- **Add `pypdf`-based text extraction to the CI validator for real PDF content scanning.** Rejected for now: new dependency + duplicate parsing logic solely for CI, when the runtime grounding pipeline already validates extracted content downstream and is unit-tested (`ConfabulationDefenseTest`). Revisit if PDF-borne prompt injection is ever observed in practice.
- **Leave the validator broken / treat the red CI as acceptable.** Rejected: trains contributors to ignore failing checks and blocks legitimate contribution PRs (this is an OSS repo accepting external corpus contributions).
- **Rewrite the poisoning validator from scratch with a broader detection framework.** Rejected: scope creep relative to what's actually missing; the existing exploit-pattern scanning is sound, it just needs to (a) actually run and (b) cover a few more phrases.

## Consequences

### Positive
- CI's contributions gate is green again and actually exercises the poison-pattern scan against the text-based formats it can read.
- A small set of NL instruction-injection phrases are now caught pre-merge for `.txt`/`.ics` contributions.
- The highest-risk paths (corpus, prompt builders, parsers, workflows) require review via CODEOWNERS.
- Prompt builders now explicitly state source content is data, closing a cheap, low-risk gap.

### Negative
- PDF content itself remains unscanned pre-merge by the CI validator; defense-in-depth for PDF-borne payloads still rests entirely on the runtime grounding guards and strict output-schema validation, not a static pre-merge check. If PDF-specific poisoning becomes a real observed threat, revisit the rejected `pypdf` alternative.
- CODEOWNERS review adds friction to legitimate contribution PRs from new contributors (mitigated by scoping it to security/architecture-relevant paths only, not the whole repo).
