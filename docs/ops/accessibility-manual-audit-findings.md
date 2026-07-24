# Accessibility Manual Audit Findings (ADR 0011 AC-4)

Status: **keyboard-only pass done** (2026-07-24, performed by Claude via live browser automation
against the real running app). **VoiceOver and NVDA passes not done** — see "What's still open"
below; these genuinely need a human, not automated tooling.

## Keyboard-only pass

Performed against the real app (`:server:runDemoLtiPlatform` + `:server:run` + `npm run dev`),
driving the browser via keyboard only (Tab / Shift+Tab / Enter / Escape), reading
`document.activeElement` directly rather than relying on visual inspection, to distinguish real
focus-order/reachability problems from screenshot-only false positives.

### Flows covered

- LTI login (student role) → lands on Calendar tab
- Calendar tab (stat cards, Chronological Agenda, Sync Calendar button)
- Sources tab (dropzone, URL ingest form, delete buttons)
- Studio Panel (chat input, Ask button)
- Settings tab (all form fields, Google Calendar connection, checkbox, Save button)
- Task decomposition modal (opened via a mocked DEADLINE event, since Gemini isn't configured in
  the local demo environment — see [ADR 0012](../adr/0012-decouple-upload-from-processing.md)'s
  context for why; reused the Playwright infra from AC-3 for this one, not the live app, since it
  needed the same route-mocking approach `web/e2e/mockApi.ts` already provides)
- Create-calendar modal (same reasoning — needs `googleLinked: true`, unavailable without real
  Google OAuth in this environment)

**Not covered**: the staff console (`web/staff/`). The mock LTI platform's `&role=instructor` query
param didn't produce a session the staff console's own access check accepted (still showed "Staff
access required..." after login) within the time reasonably spent on it. ROADMAP's AC-4 wording
already scopes this as "if applicable" rather than a required flow — flagging it as **not verified**
rather than silently skipping it. Revisit if/when the staff console gets its own dedicated test pass.

### Findings

1. **[Fixed] File upload dropzone was completely unreachable by keyboard (WCAG 2.1.1, Level A).**
   `web/src/App.tsx`'s Sources tab wrapped a `display:none` `<input type="file">` in a `<label>`
   styled as the big "Click or Drag File Here" dropzone. A `<label>` is never keyboard-focusable on
   its own, even when it wraps a hidden form control — clicking it with a mouse works (native
   label-for behavior), but Tab skipped straight from the "Settings" nav item to the URL-ingest
   input, with no way to reach the file picker at all via keyboard. This is the single largest,
   most prominent call-to-action on the page, and the primary way most sources actually get added.
   **Fixed**: replaced the `<label>` with a real `<button type="button">` that calls `.click()` on
   a `ref` to the (still-hidden) file input — gets full native keyboard operability (Tab-reachable,
   Enter/Space both activate it) for free, matches the pattern the app's other buttons already use
   correctly, and needs no custom ARIA (an earlier attempt at `role="button"` on the `<label>` itself
   was rejected by axe's `aria-allowed-role` rule — invalid ARIA-in-HTML combination; the `<button>`
   rewrite avoids the problem entirely rather than working around it). Verified: visible native
   focus ring, reachable via Tab, Enter opens the file picker. Added a regression test
   (`web/src/App.test.tsx`, "file dropzone keyboard reachability") since this exact pattern isn't
   something axe-core's static ruleset flags on its own — nothing else in the AC-2/AC-3 suites would
   have caught a silent regression back to a `<label>`-only implementation.
2. **[Not fixed, minor] Both modals' initial focus lands on the header's "Close" (✕) button, not
   the first meaningful field.** `useFocusTrap.ts` (ADR 0009) focuses whichever focusable element
   is first in DOM order inside the modal container, and both modals render their close button
   before their main content. Not a WCAG failure (2.4.3 Focus Order requires a sensible order, not a
   specific starting element — landing on "close" first is a defensible, if not optimal, choice),
   but landing on e.g. the calendar-name input in the create-calendar modal would be a nicer default.
   Left as a documented enhancement opportunity, not a blocking fix.
3. **[Verified, not a bug] `.form-control` inputs' custom `:focus-visible` style (border-color +
   box-shadow glow, replacing the suppressed native `outline`) does render correctly** — an initial
   read of computed styles immediately after a synthetic Tab event showed a near-transparent
   box-shadow, which looked like a missing-focus-indicator bug at first. Re-checked after
   `--transition-smooth`'s 300ms CSS transition had time to settle: the glow renders as intended
   (`border-color: rgb(168, 85, 247)`, `box-shadow: rgba(168, 85, 247, 0.15) 0 0 0 2px`). Recorded
   here so the false-positive isn't rediscovered from scratch next time.
4. **Everything else checked clean**: Calendar tab, Studio Panel, Settings (aside from finding 1/3
   above), both modals' Tab/Shift+Tab cycling and Escape-to-close-and-restore-focus behavior, all
   matched expected, sensible order with no unintended traps outside the two intentional modal traps
   ADR 0009 already established.

## What's still open

**VoiceOver (macOS) and NVDA (Windows) passes are not done.** These are qualitatively different
from the keyboard pass above: they require a human actually listening to real screen-reader speech
output and judging whether announcements are clear, non-redundant, and non-confusing — not an
objective reachability check a script can perform. Additionally:

- Enabling VoiceOver system-wide is a real macOS accessibility/system-settings change with an
  actual audible side effect on whoever's using the machine — not something to flip on
  unilaterally in an unattended automation session.
- NVDA requires an actual Windows machine, which isn't available in this environment at all.

Both need the user's own time (or another human's) to run for real. Options going forward, once
decided: either the user runs these passes directly against a running local instance, or this is
scheduled as a follow-up with a concrete written script per flow so the pass is repeatable and
comparable across the app's four platforms in the future.
