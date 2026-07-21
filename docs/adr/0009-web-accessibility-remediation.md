# ADR 0009: Web Accessibility Remediation

## Status
Accepted

## Context
The web client (`web/src/App.tsx`) had essentially no accessibility support: no `aria-*`/`role`
attributes, only 1 of ~10 `<label>`s correctly associated with its control via `htmlFor`, the
primary sidebar nav was built from `onClick` `<div>`s (not keyboard-operable, not in the tab
order), three icon-only buttons had no accessible name, a custom calendar-picker dropdown had no
keyboard support at all, and neither modal trapped focus or supported Escape-to-close. No
accessibility lint tooling was installed, and `npm run lint` had never been wired into CI at all —
nothing would have caught a regression even after fixing the above. This matters more than usual
for this project specifically: it's a tool being handed to university disability-services offices
(see the LTI/staff-console handover work in ADRs 0006-0008), so shipping it with unaddressed
accessibility gaps would be a real credibility problem, not just a nice-to-have.

## Decision
1. **`eslint-plugin-jsx-a11y` installed as the automated guardrail, added first**, before any
   manual fix, specifically so it could catch regressions in every fix that followed. It found 29
   real issues on the first run (12 unassociated labels, 8 non-keyboard-operable click handlers ×
   2 rules, 1 disruptive `autoFocus`), more than the original manual audit had.
2. **A real, current peer-dependency gap: `eslint-plugin-jsx-a11y@6.10.2` only declares
   `peerDependencies: { eslint: "^3 || ... || ^9" }`, and this project is on `eslint@10`**, released
   too recently for the plugin to have caught up. Verified it works correctly in practice (ran the
   full lint suite, inspected real findings) despite the conservative peer range — this is a
   peer-range lag, not an actual incompatibility. Added `web/.npmrc` with
   `legacy-peer-deps=true` so `npm install`/`npm ci` don't fail on it, in both local dev and CI.
   Remove this once jsx-a11y ships an eslint@10-compatible release.
3. **`react-hooks/set-state-in-effect` (a newer, stricter rule bundled with the current
   `eslint-plugin-react-hooks`) downgraded to `off`, with a comment explaining why**, rather than
   restructuring the app's data-fetching pattern. It flags `useEffect(() => { fetchThing() }, [])`
   purely because `fetchThing` eventually calls `setState` — the React-docs-recommended shape for
   effect-driven data fetching, used throughout this app (`fetchSources`, `checkSession`,
   `fetchStudents` in the staff console) and not a real bug. Fixing it "properly" everywhere would
   mean rewriting the app's fetch-on-mount pattern, well outside an accessibility pass's scope.
4. **The custom div-based calendar-selection dropdown was replaced with a native `<select>`**
   rather than hand-building `role="listbox"`/roving-tabindex/keyboard handling — same behavior,
   fully accessible for free, and it deleted the `calendarDropdownOpen` state entirely (one less
   thing to maintain, not just an accessibility win).
5. **A small hand-rolled `useFocusTrap` hook (`web/src/useFocusTrap.ts`, ~50 lines)** instead of a
   focus-trap library — traps Tab/Shift+Tab within a modal, moves focus in on open, restores it on
   close, and handles Escape. Applied to both modals (task decomposition, create-calendar) via
   `role="dialog"`/`aria-modal="true"`/`aria-labelledby` pointing at each modal's own `<h2>`.
6. **File upload dropzone converted from a clickable `<div>` wrapping a hidden file input to a
   `<label htmlFor>` wrapping it directly** — native label-for-file-input click-to-open behavior,
   no JS ref-triggered click needed, deleted `fileInputRef` entirely.
7. **The four sidebar nav items became `<button type="button">`s** with `aria-current="page"` on
   the active tab and `aria-label="Primary"` on the `<nav>` — native keyboard operability, no
   manual `onKeyDown` handling required. `.nav-item`'s CSS gained a small button-chrome reset
   (`border: none; background: none; font: inherit; text-align: left; width: 100%`) since it now
   targets a `<button>` as often as the visually-identical prior `<div>`.
8. **`.form-control:focus` → `.form-control:focus-visible`** — shows the focus ring for keyboard
   navigation without showing it on a mouse click, the modern convention.
9. **No new frontend test infrastructure added** (no vitest/RTL/jest-axe) — the project has none
   today. `eslint-plugin-jsx-a11y` in CI is the automated regression guardrail; manual Tab-through
   and a screen-reader smoke pass remain the verification step for anything the linter can't catch
   (visual focus order, screen-reader announcement quality). Flagged as a bigger, additive
   follow-up if the team wants real automated a11y regression tests later.

## Alternatives Considered
* **Fix `react-hooks/set-state-in-effect` findings too, for a fully clean `npm run lint`.**
  Rejected — see point 3; the "fix" would be a behavioral React refactor unrelated to
  accessibility, not a mechanical one.
* **`focus-trap-react` (or similar) instead of hand-rolling `useFocusTrap`.** Considered; the
  hand-rolled version is small enough (~50 lines, two well-understood behaviors: Tab-wrapping and
  Escape) that a dependency wasn't worth it, consistent with this project's general preference for
  a small dependency surface.
* **Pin `eslint` back to `^9` instead of accepting the jsx-a11y peer-range gap.** Rejected: a
  bigger, riskier change than the actual problem warranted, and `eslint@10` was itself a recent,
  presumably intentional upgrade unrelated to this work.

## Consequences

### Positive
* `npm run lint` runs in CI for the first time ever (`build-web` job in `pr-check.yml`) — this
  alone catches more than the accessibility-specific findings; it's the same lint config the
  project already had, just never invoked automatically before.
* Zero known jsx-a11y violations at time of writing; the tooling stays in place to catch new ones.
* Two real, non-accessibility bugs fixed as a side effect: `fetchSources`/`fetchEvents`/
  `fetchSettings`/`fetchGoogleAuthStatus`/`fetchCalendars` were referenced before their `const`
  declarations (harmless at runtime given effect timing, but confusing to read and flagged by
  `react-hooks/immutability`) — reordered rather than suppressed.

### Negative
* `web/.npmrc`'s `legacy-peer-deps=true` is a standing exception, not scoped to just the one
  plugin — any future dependency with a genuine (not just lagging) peer conflict would also
  silently install instead of failing loudly. Acceptable given the alternative (blocking on an
  upstream release with no ETA), but worth revisiting once jsx-a11y catches up to eslint@10.
* No automated test coverage for the accessibility fixes themselves (focus trap behavior, tab
  order, screen-reader announcements) — verified manually once, not continuously guarded beyond
  what jsx-a11y's static rules catch.
