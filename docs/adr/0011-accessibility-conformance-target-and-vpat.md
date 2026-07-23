# ADR 0011: Accessibility Conformance Target (WCAG 2.1 AA) and VPAT

## Status
Accepted

## Context
ADR 0009 fixed real, concrete accessibility defects in the web client (keyboard operability, ARIA
labeling, focus management, label association) and wired `eslint-plugin-jsx-a11y` into CI as a
static regression guardrail. That work is real, but it does not add up to a claim the project can
currently defend: there is no explicit conformance level (no stated "WCAG 2.1 AA" or similar
target), no VPAT (Voluntary Product Accessibility Template / Accessibility Conformance Report), no
automated accessibility regression tests beyond static linting, and no documented color-contrast or
assistive-technology audit.

This gap surfaced directly: a request to write a short "for disability offices" marketing script
wanted to say "ADA compliant." That specific audience — university disability-services and
procurement offices — routinely asks for a VPAT as the standard artifact before adoption. Saying
"ADA compliant" today would be an unbacked claim; asked for paperwork, the honest answer is
currently none. The marketing script itself was changed to avoid the term until this gap is closed
(see chat/commit history around 2026-07-23) — this ADR is the plan to actually close it, not just
avoid the word.

## Decision
1. **Adopt WCAG 2.1 Level AA as the explicit, documented conformance target** for the web client.
   This is the level referenced by DOJ ADA Title II guidance and Section 508, and the one
   disability-services offices most commonly ask about — a concrete, checkable target rather than
   the vague "accessible" ADR 0009 shipped with.
2. **Add automated accessibility regression testing**, not just static linting. `eslint-plugin-
   jsx-a11y` (ADR 0009) catches JSX-shape issues (missing labels, non-keyboard handlers) but not
   runtime issues like color contrast, dynamic ARIA state, or live-region behavior. This requires
   frontend test infrastructure the project doesn't have yet (ADR 0009 point 9 explicitly deferred
   this) — Vitest + React Testing Library + an axe integration (`vitest-axe` or `jest-axe`).
3. **Run a real manual assistive-technology pass**: full keyboard-only walkthrough of every primary
   flow, plus a screen-reader smoke test with both VoiceOver (macOS/iOS, since two of four platforms
   are Apple) and NVDA (Windows, the most common combination in US higher-ed IT). Automated tooling
   (axe, jsx-a11y) structurally cannot catch everything — announcement quality and focus order under
   real assistive tech still need a human pass.
4. **Audit and fix color contrast** against WCAG 1.4.3 (4.5:1 normal text, 3:1 large text/UI
   components) across the dark theme, which is a common failure mode for muted/secondary text
   colors specifically (`var(--text-secondary)` and similar) that look fine to a sighted engineer at
   full brightness but fail the ratio.
5. **Publish an in-app, public accessibility statement** — conformance target, known limitations,
   and a contact path for reporting issues. This is itself a standard expectation in institutional
   procurement, independent of the VPAT.
6. **Produce a real VPAT** (ITI's VPAT 2.5, WCAG Edition or the combined INT edition covering
   Section 508/EN 301 549 too, given the target audience) once 2-4 above have actually run — the
   VPAT must reflect real findings (including "Partially Supports" / "Does Not Support" rows where
   true), not be back-filled to look clean. A VPAT that oversells is worse than no VPAT once a real
   disability-services reviewer starts testing.

## Alternatives Considered
* **Target WCAG 2.2 AA instead of 2.1.** Rejected for now — 2.1 AA is still the more universally
  referenced baseline in current US ADA/Section 508 guidance and by the offices this is aimed at;
  revisit once 2.2 sees wider procurement-side adoption.
* **Self-declare conformance without a VPAT.** Rejected — a VPAT/ACR is the artifact this specific
  audience expects to ask for; showing up without one reads as not having done the work, regardless
  of actual conformance.
* **Commission a third-party audit immediately.** Deferred, not rejected — real cost for a claim
  nobody has asked to see proof of yet. Revisit as AC-7 (see ROADMAP.md) if/when an actual customer
  or procurement process requires third-party attestation rather than a self-authored VPAT.

## Consequences

### Positive
* Closes the actual gap that blocked an honest "ADA compliant"-style marketing claim, with a
  concrete, checkable target (WCAG 2.1 AA) instead of vague language.
* Frontend test infrastructure (Vitest + RTL), once added for AC-2, is reusable for general web
  test coverage beyond accessibility — the project currently has zero frontend tests of any kind.
* A real VPAT is an artifact the sales/outreach side can hand directly to a disability-services or
  procurement office, which is the whole point of the original marketing-script request.

### Negative
* This is real, multi-step work (new test infra, a manual AT pass, a contrast audit, a VPAT
  document) — not a quick relabeling. See ROADMAP.md's AC-1..AC-7 for the breakdown; nothing here
  ships instantly.
* A VPAT is a point-in-time document — every future frontend change risks silently regressing a
  conformance row the VPAT claims, unless AC-2's automated coverage stays a required CI gate (same
  "regressions need a guardrail" lesson ADR 0009 already learned once with jsx-a11y).
