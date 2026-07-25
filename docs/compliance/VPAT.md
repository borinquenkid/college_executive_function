# Voluntary Product Accessibility Template (VPAT®) 2.5 — INT

**Name of Product/Version:** College Executive Function — Web Client, v3.0.8

**Report Date:** 2026-07-24

**Product Description:** A web application that helps college students turn course syllabi into
an actionable academic calendar and study plan — ingesting syllabus documents (PDF/DOCX/ICS/URL),
using an AI pipeline to extract deadlines and generate study-block events, and syncing the result to
Google Calendar. This VPAT covers **the web client only** (the React single-page application served
at the app's web URL), not the native Android, iOS, or Desktop applications, which are separate
codebases evaluated separately.

**Contact information:** privacy@borinquenterrier.com

**Evaluation Methods Used:** This report reflects an internal self-assessment, not a third-party
audit (see ADR 0011's "Alternatives Considered" — a third-party audit is deferred, not rejected,
pending a real external ask). Evidence backing each row below comes from one or more of:

- **Automated testing**: [axe-core](https://github.com/dequelabs/axe-core) run two ways — inside a
  fast Vitest/jsdom suite (`web/src/App.test.tsx`) and against a real Chromium browser via Playwright
  (`web/e2e/accessibility.spec.ts`) — across all 4 primary views (Calendar, Sources, Studio Panel,
  Settings) and both modal dialogs (task decomposition, create-calendar), in their default and open
  states.
- **Manual color-contrast audit**: every text/UI-component color pair against WCAG 1.4.3, using
  axe-core injected into the live running app plus hand-computed WCAG relative-luminance ratios for
  pairs axe couldn't reliably composite (`backdrop-filter` layers). Full table in
  `docs/ops/accessibility-manual-audit-findings.md`.
- **Manual keyboard-only walkthrough**: every primary flow (login, upload a source, view/sync the
  calendar, decompose a task, chat in the Studio Panel, edit settings) driven by keyboard alone
  against the real running app.
- **Automated real screen-reader testing**: [Guidepup](https://github.com/guidepup/guidepup) driving
  actual VoiceOver (macOS/WebKit) and NVDA (Windows/Firefox) instances against the real app in CI,
  capturing literal announced text — not a simulation. Scoped to 3 targeted checks (main heading on
  load, a modal dialog's announced title, a previously-broken control's fixed announcement), not
  every element on every view — see `docs/ops/accessibility-manual-audit-findings.md` for the full
  run history and per-reader findings.
- **Static analysis**: `eslint-plugin-jsx-a11y` linting, run on every commit (predates this VPAT,
  from ADR 0009).
- **Direct code/content inspection**: for criteria the above testing didn't directly exercise
  (e.g. confirming there is no video/audio content, no imposed time limits, no flashing content),
  checked by reading the actual source rather than assumed.

Where none of the above produced real evidence for a criterion, it is marked **Not Evaluated** below
rather than guessed at.

**Applicable Standards/Guidelines:** This report covers the degree of conformance for the following
accessibility standard/guidelines:

| Standard/Guideline | Included In Report |
|---|---|
| Web Content Accessibility Guidelines 2.1 | Level A (yes), Level AA (yes), Level AAA (not evaluated — WCAG 2.1 AA is this project's adopted conformance target, see [ADR 0011](../adr/0011-accessibility-conformance-target-and-vpat.md); AAA was not tested) |
| Revised Section 508 standards published January 18, 2017 and corrected January 22, 2018 | Yes |
| EN 301 549 Accessibility requirements for ICT products and services | Yes (referenced via its WCAG 2.1-based clause 9; hardware- and non-web-ICT-specific clauses are Not Applicable — this product is a web application) |

## Terms

The terms used in the Conformance Level information are defined as follows:

- **Supports**: The functionality of the product has at least one method that meets the criterion
  without known defects, or is not applicable.
- **Partially Supports**: Some functionality of the product does not meet the criterion.
- **Does Not Support**: The majority of product functionality does not meet the criterion.
- **Not Applicable**: The criterion is not relevant to the product.
- **Not Evaluated**: The product has not been evaluated against the criterion. This can be used only
  in WCAG 2.x Level AAA.

## WCAG 2.1 Report

*Tables 1 and 2 also apply to Section 508 Chapter 5 and 6 (via their WCAG 2.0 Level A/AA reference)
and EN 301 549 clause 9 (via its WCAG 2.1 Level A/AA reference); their remarks are not repeated for
those standards.*

### Table 1: Success Criteria, Level A

| Criteria | Conformance Level | Remarks and Explanations |
|---|---|---|
| **1.1.1 Non-text Content** | Supports | Icons throughout carry `aria-label`/`contentDescription`-equivalent accessible names (enforced by `eslint-plugin-jsx-a11y` since ADR 0009); confirmed via zero axe violations across all 4 views + both modals in both the jsdom and real-browser suites. No informational images beyond icons; no decorative-image mislabeling found. |
| **1.2.1 Audio-only and Video-only (Prerecorded)** | Not Applicable | The product contains no audio or video content. |
| **1.2.2 Captions (Prerecorded)** | Not Applicable | No video content. |
| **1.2.3 Audio Description or Media Alternative (Prerecorded)** | Not Applicable | No video content. |
| **1.3.1 Info and Relationships** | Supports | Semantic HTML and ARIA roles/labels used throughout (form labels, `role="dialog"`/`aria-modal`/`aria-labelledby` on both modals, `role="status"` on live status text). Zero axe violations across all views/modals. A real `heading-order` (WCAG 1.3.1) violation was found and fixed during AC-2 (the Calendar stat cards and Settings tab jumped from `<h1>` to `<h3>`, skipping `<h2>`; the Chronological Agenda's date headers were `<h4>` directly under `<h2>`) — fixed by promoting heading levels; regression-covered by the axe suite. |
| **1.3.2 Meaningful Sequence** | Supports | DOM order matches visual/reading order throughout; confirmed incidentally during the real screen-reader testing (VoiceOver/NVDA browse-mode navigation through the Calendar view and the create-calendar modal encountered content in a sensible order) and the keyboard-only pass (Tab order matches visual layout). |
| **1.3.3 Sensory Characteristics** | Supports | Instructions do not rely on shape, size, color, or position alone (e.g. buttons are labeled with text, not just described by position/color). |
| **1.4.1 Use of Color** | Supports | Status/state (e.g. Google Calendar linked/not linked, error messages) is conveyed with text and icons in addition to color. |
| **1.4.2 Audio Control** | Not Applicable | No audio content that plays automatically. |
| **2.1.1 Keyboard** | Partially Supports | A full keyboard-only walkthrough of every primary flow was completed and found one real failure: the Sources tab's file-upload dropzone was a `<label>` wrapping a `display:none` file input, completely unreachable by keyboard. **Fixed** — rewritten as a real `<button>`, with a regression test (`App.test.tsx`) since axe-core does not flag this specific pattern on its own. Marked "Partially Supports" rather than "Supports" because this was a real, user-facing defect found and fixed within the evaluation period covered by this report, not because a known defect remains open. |
| **2.1.2 No Keyboard Trap** | Supports | Both modal dialogs implement a real focus trap (`useFocusTrap.ts`, ADR 0009) that wraps Tab/Shift+Tab at the first/last focusable element and restores focus to the invoking control on close — verified by the keyboard-only pass; no traps found outside the two intentional modal traps. |
| **2.1.4 Character Key Shortcuts** | Supports | The product defines no single-character keyboard shortcuts. |
| **2.2.1 Timing Adjustable** | Not Applicable | The product imposes no time limits on user interaction. The authentication session cookie has a 180-day expiry, which is not an interactive time limit within the meaning of this criterion. |
| **2.2.2 Pause, Stop, Hide** | Supports | The only auto-updating content is the source-digestion progress indicator (an SSE-driven status message shown while an uploaded syllabus is processed by the AI pipeline). This falls under the criterion's exception for content where the movement/update "is part of an activity where it is essential" — genuine, real-time processing-status feedback, not decorative or independently-informative moving content (e.g. not a carousel or ticker). |
| **2.3.1 Three Flashes or Below Threshold** | Supports | No content flashes. The only animations in the product (a loading spinner, a subtle "pulse" warning highlight, a chat "typing" indicator) cycle at 1–1.5 second intervals — well under the three-flashes-per-second threshold. Confirmed by direct inspection of `web/src/index.css`. |
| **2.4.1 Bypass Blocks** | Not Evaluated | Not specifically tested for a skip-to-main-content mechanism; the automated axe suite did not flag a violation here, but this was not independently verified with a real screen reader in browse mode across the full page. |
| **2.4.2 Page Titled** | Supports | `<title>College Executive Function</title>` is set. The product is a single-page application — content changes are handled as in-page tab switches (Calendar/Sources/Studio Panel/Settings) rather than separate page loads, so a single descriptive title is appropriate; confirmed via direct inspection of `index.html`. |
| **2.4.3 Focus Order** | Supports | Verified during the keyboard-only pass: focus order follows visual/reading order across all primary flows, and both modals move focus into themselves on open per WAI-ARIA's modal dialog pattern. |
| **2.4.4 Link Purpose (In Context)** | Supports | The product uses very few text links (most navigation is via labeled buttons); the links present (e.g. the accessibility statement's contact link) have descriptive text, not "click here." |
| **2.5.1 Pointer Gestures** | Supports | The Sources tab's file dropzone supports drag-and-drop, but a real `<button>` click (a single-pointer alternative) performs the same function — confirmed as part of the AC-4 keyboard-reachability fix. No other multipoint/path-based gestures exist in the product. |
| **2.5.2 Pointer Cancellation** | Supports | All interactive controls are standard HTML buttons/inputs using native click semantics (activation on `up`-event, cancelable by moving off-target before release); no custom `pointerdown`-triggered actions were found. |
| **2.5.3 Label in Name** | Supports | Confirmed via the automated screen-reader testing and axe accessible-name checks: visible button labels (e.g. "Settings", "+ Create New Calendar") match their accessible names. |
| **2.5.4 Motion Actuation** | Not Applicable | The product has no functionality triggered by device motion or user motion. |
| **3.1.1 Language of Page** | Supports | `<html lang="en">` is set; confirmed via direct inspection of `index.html`. |
| **3.2.1 On Focus** | Supports | No component triggers a change of context (e.g. form submission, navigation) simply on receiving focus, confirmed during the keyboard-only pass. |
| **3.2.2 On Input** | Supports | Changing a form control's value (e.g. the Settings preferences, the target-calendar dropdown) does not trigger an unexpected context change without the user also activating a control (e.g. "Save Configurations"). |
| **3.3.1 Error Identification** | Supports | Form errors (e.g. calendar-creation failure) are presented as visible, associated text near the relevant control, not by color alone. |
| **3.3.2 Labels or Instructions** | Supports | Every form input has an associated `<label>`; confirmed by zero axe `label`-related violations across all views/modals in both suites. |
| **4.1.1 Parsing** | Supports | Content is rendered by React from JSX with no observed duplicate `id` attributes or malformed markup; the `App.test.tsx`/`e2e/accessibility.spec.ts` axe suites would flag most parsing-related accessibility issues, and none surfaced. |
| **4.1.2 Name, Role, Value** | Supports | Custom interactive elements use appropriate ARIA (`role="dialog"`, `aria-modal`, `aria-labelledby`, `aria-label`, `aria-current`); verified by zero axe violations and by the real screen-reader tests correctly announcing role and accessible name for the elements checked (e.g. a button reachable via the AC-4 keyboard fix announces as "button" with its label to both VoiceOver and NVDA). |

### Table 2: Success Criteria, Level AA

| Criteria | Conformance Level | Remarks and Explanations |
|---|---|---|
| **1.2.4 Captions (Live)** | Not Applicable | No live audio/video content. |
| **1.2.5 Audio Description (Prerecorded)** | Not Applicable | No video content. |
| **1.3.4 Orientation** | Supports | The product is a responsive web layout with no orientation lock; confirmed by direct inspection (no `orientation` CSS media-query restrictions or JS orientation locks found). |
| **1.3.5 Identify Input Purpose** | Not Evaluated | Common input types (e.g. `autocomplete` attributes for user-identity fields) were not specifically audited. The product's forms are mostly domain-specific (study-hour preferences, calendar names) rather than common personal-data fields this criterion targets, but this was not independently confirmed. |
| **1.4.3 Contrast (Minimum)** | Supports | Full manual audit against the WCAG 1.4.3 formula (4.5:1 normal text, 3:1 large text/UI) across every text/UI-color pair in use. One real violation found and fixed (`.btn-primary`/`.chat-msg.user` white-on-`#a855f7`, 3.95:1 — fixed via a new `--color-primary-solid` token at 5.38:1) plus `--text-muted` lightened from a failing 3.4–4.0:1 to a passing 4.9–5.7:1. Full ratio table in `docs/ops/accessibility-manual-audit-findings.md`. Re-verified live post-fix across all 4 views with zero contrast violations. |
| **1.4.4 Resize text** | Not Evaluated | Not specifically tested at 200% browser zoom. The viewport meta tag does not disable user scaling (`<meta name="viewport" content="width=device-width, initial-scale=1.0">`, no `maximum-scale`/`user-scalable=no`), which is a positive sign but not a substitute for actually verifying no content is lost or overlapping at 200% zoom. |
| **1.4.5 Images of Text** | Supports | The product uses no images of text; all text (including icon labels) is real, styleable text. |
| **1.4.10 Reflow** | Not Evaluated | Not specifically tested at a 320px-equivalent viewport width. |
| **1.4.11 Non-text Contrast** | Partially Supports | The manual contrast audit (AC-3) covered text-on-background pairs comprehensively but was not exhaustively extended to every UI-component boundary/state (e.g. input borders, focus-indicator outlines) against this criterion's 3:1 threshold. One relevant finding did surface: `--color-primary` (#a855f7) used as a UI/icon color against composited `bg-card-hover`/`bg-input` backgrounds measures 4.2–4.3:1 (actually passing 3:1, though below the stricter 4.5:1 text threshold it would need if used as text there) — not used as text on those backgrounds today, and documented as a constraint on future design changes in the in-app accessibility statement. |
| **1.4.12 Text Spacing** | Not Evaluated | Not specifically tested with user style overrides for line-height/letter-spacing/word-spacing. |
| **1.4.13 Content on Hover or Focus** | Not Evaluated | The product has minimal hover-triggered content (mainly native `title` tooltips); not specifically audited against this criterion's dismissible/hoverable/persistent requirements. |
| **2.4.5 Multiple Ways** | Not Applicable | The product is a single-page application where the primary views (Calendar, Sources, Studio Panel, Settings) are reachable via a single persistent navigation bar, not separate pages in a traditional multi-page site; this criterion explicitly does not require multiple ways to reach a page that is one step in a process. |
| **2.4.6 Headings and Labels** | Supports | Headings and form labels are descriptive; verified via the real heading-order fix in AC-2 and zero axe `label`/heading-related violations across all views/modals. |
| **2.4.7 Focus Visible** | Supports | A `:focus-visible`-based focus indicator is present throughout; an apparent issue flagged during the keyboard-only pass was investigated and confirmed to be a false alarm (documented in `docs/ops/accessibility-manual-audit-findings.md`), not a real missing-focus-indicator defect. |
| **3.1.2 Language of Parts** | Not Applicable | All product content is in English; no passages in a different language occur. |
| **3.2.3 Consistent Navigation** | Supports | The single persistent navigation bar (Calendar/Sources/Studio Panel/Settings) is identical across every view. |
| **3.2.4 Consistent Identification** | Supports | Icons and controls with the same function (e.g. the modal close "✕" button, save/cancel buttons) are labeled consistently across both modal dialogs and throughout the product. |
| **3.3.3 Error Suggestion** | Supports | Where a specific fix is knowable (e.g. calendar-creation failures return a server-provided reason), it is shown as visible error text near the relevant control. |
| **3.3.4 Error Prevention (Legal, Financial, Data)** | Not Applicable | The product performs no legal, financial, or user-data-deletion transactions with irreversible consequences that fall within this criterion's scope (legal commitments, financial transactions, or test-answer submissions). |
| **4.1.3 Status Messages** | Partially Supports | The source-digestion progress indicator and other transient status text use `role="status"` (implicit `aria-live="polite"`), which is the correct pattern for this criterion. However, this specific async-progress flow was not one of the 3 targeted checks in AC-4's real screen-reader test suite, so live announcement of these particular status updates has been confirmed by code inspection, not by an actual screen-reader observation. |

### Table 3: Success Criteria, Level AAA

Not evaluated. WCAG 2.1 Level AAA is outside this project's adopted conformance target (see
[ADR 0011](../adr/0011-accessibility-conformance-target-and-vpat.md), which explicitly adopts Level
AA as the more universally referenced baseline in current ADA/Section 508 guidance).

## Revised Section 508 Report

| Criteria | Conformance Level | Remarks and Explanations |
|---|---|---|
| **Chapter 3: Functional Performance Criteria (302.1 – 302.9)** | Not Evaluated | Functional Performance Criteria describe outcomes for users with a range of disabilities (vision, hearing, cognitive, etc.) rather than testable technical criteria; the WCAG 2.1 A/AA evaluation above is the substantive basis for this report, and a dedicated Functional Performance Criteria walkthrough was not separately performed. |
| **Chapter 4: Hardware (402 – 407)** | Not Applicable | This product is a web application, not a hardware product. |
| **Chapter 5: Software (501 – 503)** | | |
| 501.1 Scope | Not Applicable | Software-specific chapter scoping statement; see individual criteria below. |
| 502 Interoperability with Assistive Technology | Supports | The product is a standard web application rendered in a standards-compliant browser; it does not implement closed/custom UI toolkits that would bypass the platform accessibility API, and the automated VoiceOver/NVDA testing (AC-4) confirms real assistive technology can read and interact with it. |
| 503.2 User Preferences | Not Applicable | The product does not disable or override platform/OS-level accessibility settings (e.g. does not lock text size, contrast mode, or reduced motion). |
| 503.3 Alternative User Interfaces | Not Applicable | The product does not provide alternative user interfaces that function as assistive technology. |
| 503.4 Authoring Tools | Not Applicable | The product is not an authoring tool. |
| **Chapter 6: Support Documentation and Services (601 – 603)** | | |
| 601.1 Scope | Supports | The in-app accessibility statement (Settings tab) documents the product's conformance target, real known limitations, and a contact path — see AC-5 in [`docs/ops/accessibility-manual-audit-findings.md`](../ops/accessibility-manual-audit-findings.md) and [ROADMAP.md](../../ROADMAP.md). |
| 602.2/602.3 Accessibility and Compatibility Features / Electronic Support Documentation | Supports | Covered by the in-app accessibility statement described above. |
| 603.2/603.3 Support Services / Information on Request | Supports | The in-app accessibility statement provides `privacy@borinquenterrier.com` as a contact for reporting accessibility issues, the same address already used for privacy/support in the product's store listings. |
| **Chapters 1, 2, 7–11 (application, definitions, and non-web-software chapters)** | Not Applicable | This product is a single web application; chapters covering non-web software categories (e.g. real-time text products, closed products) do not apply. |

## EN 301 549 Report

| Criteria | Conformance Level | Remarks and Explanations |
|---|---|---|
| **Clause 4: Functional Performance Statements** | Not Evaluated | Same basis as Section 508 Chapter 3 above — the WCAG 2.1 A/AA tables are the substantive evidence in this report. |
| **Clause 5: Generic Requirements** | Not Applicable | Covers hardware/physical-product requirements (e.g. biometrics, keys and controls) not relevant to a web application. |
| **Clause 6: ICT with Two-Way Voice Communication** | Not Applicable | The product has no voice-communication functionality. |
| **Clause 7: ICT with Video Capabilities** | Not Applicable | The product has no video functionality. |
| **Clause 8: Hardware** | Not Applicable | The product is a web application, not hardware. |
| **Clause 9: Web** | See WCAG 2.1 Tables 1 and 2 above | EN 301 549 clause 9 incorporates WCAG 2.1 Level A and AA by reference; the same evaluation applies. |
| **Clause 10: Non-web Documents** | Not Applicable | The product does not produce non-web documents (e.g. PDF reports) as part of its own output; it *ingests* PDF/DOCX syllabus files supplied by the user as input, which is outside this clause's scope (documents the product itself creates for users). |
| **Clause 11: Software** | See Section 508 Chapter 5 above | Same evaluation and rationale. |
| **Clause 12: Documentation and Support Services** | See Section 508 Chapter 6 above | Same evaluation and rationale. |
| **Clause 13: ICT Providing Relay or Emergency Service Access** | Not Applicable | The product provides no relay or emergency-service functionality. |

## Notes

- **Scope**: This VPAT covers the web client only, per [ADR 0011](../adr/0011-accessibility-conformance-target-and-vpat.md). The native Android, iOS, and Desktop (Compose Multiplatform) applications are separate codebases and are not covered here. A related, real finding from the same evaluation pass: Compose Multiplatform Desktop's Windows build needs the `jdk.accessibility` JDK module (added to `composeApp/build.gradle.kts` during this work) for NVDA/Java Access Bridge support, and Linux currently has no Compose Desktop accessibility bridge at all — a genuine upstream framework gap, not something fixable from this app. Neither of these affects the web client covered by this report.
- **What "Not Evaluated" means here, concretely**: for any WCAG 2.1 row marked Not Evaluated (Level A: 2.4.1; Level AA: 1.3.5, 1.4.4, 1.4.10, 1.4.12, 1.4.13), this report is stating plainly that no test was run — not implying a pass. Combined with the two "Partially Supports" rows that identify a specific, real, already-known gap rather than a defect (1.4.11, 4.1.3), these are the concrete next candidates for a future evaluation pass, in rough priority order: 4.1.3 (a real, already-used pattern that just needs a targeted screen-reader check), 1.4.11 (extend the existing contrast audit to non-text UI boundaries), 1.4.4/1.4.10 (a real 200%-zoom/320px-width pass), 2.4.1 (confirm skip-navigation behavior with a real screen reader), 1.4.12/1.4.13 (text-spacing and hover-content checks).
- **This is a living document.** It should be re-evaluated whenever AC-2/AC-3/AC-4's underlying test suites materially change, or at minimum before any external claim of WCAG/ADA/Section 508 conformance is made based on it.

---

*VPAT® is a registered trademark of the Information Technology Industry Council (ITI). This
document was produced using the VPAT® 2.5 INT template made available by ITI, used here under its
terms; ITI does not endorse this document or the product it describes. This report is based on an
internal self-assessment, not a third-party audit — see the Evaluation Methods section above.*
