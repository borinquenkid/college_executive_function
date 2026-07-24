# Accessibility Manual Audit Findings (ADR 0011 AC-4)

Status (2026-07-24): **keyboard-only pass done** (live browser automation against the real running
app) and **real automated VoiceOver + NVDA coverage now exists** via Guidepup — see "Automated
screen-reader coverage" below. A genuine human listen-through (for prosody/naturalness judgment,
which text-capture can't substitute for) is still valuable but no longer a hard blocker — see
"What's still open" for the honest boundary of what the automated layer does and doesn't cover.

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

## Automated screen-reader coverage (Guidepup)

The user pushed back twice on assumptions made here in sequence — first that Compose Desktop
couldn't target Windows (it does, via the JVM), then that a real screen-reader pass needed a human
at all. Both corrections were right, and the second one led somewhere concrete:
[Guidepup](https://github.com/guidepup/guidepup) is a real, actively maintained (MIT) screen-reader
test-automation library supporting VoiceOver (macOS) and NVDA (Windows) with a single API, capturing
the actual **announced text** (via `spokenPhraseLog()`/`lastSpokenPhrase()`) rather than audio — so
no human ear needed to check *what* gets announced, even though judging *how natural it sounds* in
sequence still benefits from one. Verified directly from the real READMEs
(`guidepup/guidepup`, `guidepup/guidepup-playwright`, `guidepup/setup-action`) before committing to
anything, not assumed from training data — in particular that screen readers cannot run against
headless browsers at all (every official example sets `headless: false`), and that VoiceOver pairs
with WebKit while NVDA pairs with Firefox in Guidepup's own reference configs (not Chromium for
either).

**Built**: `web/playwright.screenreader.config.ts` (separate from the fast, headless
`web/playwright.config.ts` used by AC-3's axe suite — screen readers need a real, headed browser
session), `web/e2e-screenreader/dropzone-keyboard-fix.spec.ts` and `calendar-and-modal.spec.ts`
(using the cross-platform `screenReaderTest`/`screenReader` fixture from `@guidepup/playwright`,
which resolves to VoiceOver on macOS and NVDA on Windows automatically — one set of test files
covers both), and `.github/workflows/screen-reader-a11y.yml` (new, separate workflow — see below for
why not folded into `pr-check.yml`).

**Scope, deliberately not exhaustive**: 3 targeted checks, not a repeat of every view/modal already
covered by `App.test.tsx`/`e2e/accessibility.spec.ts`, since each real screen-reader step drives
actual OS automation (real wall-clock cost) — Guidepup's own reference configs budget 5 minutes and
2 retries per test class:
1. **The Sources dropzone button — this session's actual AC-4 keyboard-fix finding** — confirms it
   announces as a real button with its label to a real screen reader, not just reachable via raw DOM
   focus (which the jsdom/axe suites already cover, but can't confirm what a screen reader actually
   *says*).
2. Calendar view's main heading announces on load.
3. The create-calendar modal announces as a dialog with its title.

Broader per-view coverage can be added incrementally; this is a proportionate first real pass, not
the ceiling.

**Manual trigger only (`workflow_dispatch`), not on every PR/push** — a real, asymmetric GitHub
Actions cost consideration flagged to and confirmed by the user before building: `macos-latest`
bills at ~10x and `windows-latest` at ~2x the Linux-runner-minute rate. Run it from the Actions tab
when a real screen-reader check is wanted (e.g. before a release, or after touching interactive
controls in `web/src/App.tsx`).

**Not run locally in this session, and shouldn't be by an agent unilaterally**: the one-time
`npx @guidepup/setup setup` step configures macOS Accessibility/TCC permissions for automation —
this either triggers an interactive System Settings permission dialog (needs a human to click
through) or implies bypassing TCC via SIP-related settings in unattended environments (the setup
action's own `ignoreTccDb` escape hatch). Both are real system-permission changes on the user's
actual machine that shouldn't happen without them directly driving it. First real functional
verification happened by manually triggering the workflow in GitHub Actions — an isolated,
disposable VM is exactly the right place for this kind of setup, not a real local machine.

### Real CI verification (5 runs, iterated against actual failures — not guessed)

The first real trigger failed both jobs immediately, and it took several more real runs to reach a
working state — recorded here because each failure taught something concrete about how Guidepup
actually behaves in CI, not just in its docs:

1. **`guidepup/setup-action` alone isn't enough.** It only runs the machine-level `setup` step.
   `@guidepup/setup`'s own quick-start is a three-step sequence — `setup` → `npm install` →
   `npx @guidepup/setup install` (reads the project's installed `@guidepup/guidepup` version to
   fetch matching screen-reader assets) — and the third step was missing entirely. Fixed by adding
   an explicit `npx @guidepup/setup install voiceover`/`nvda` step after `npm ci` in
   `screen-reader-a11y.yml`. This alone fixed NVDA's "NVDA is not supported" error outright.
2. **`navigateToWebContent()` doesn't land on the target — it lands wherever the browse cursor
   starts** (a landmark region, in our case), matching Guidepup's own reference example
   (`headerNavigation.ts`, shared across their VoiceOver/NVDA/generic example suites), which loops
   `next()` until the target text is reached rather than asserting immediately. Our specs made the
   same wrong assumption Guidepup's own docs implicitly warn against. Added
   `navigateUntilItemTextIncludes()` (`web/e2e-screenreader/navigateUntil.ts`) matching that pattern.
3. **`lastSpokenPhrase()` is a genuinely live query** (AppleScript on macOS, a live NVDA query on
   Windows) — it does NOT reliably reflect a screen reader's live announcement following a passive,
   non-Guidepup-driven DOM event (a focus-trap's native `.focus()` call, or a real Tab keypress) in
   CI, even though the same event would audibly announce for an interactive human user. Confirmed
   this is genuinely reader-specific, not a single fixable bug:
   - Dropzone check: NVDA's real Tab-press loop worked; VoiceOver's didn't (stuck on stale content).
     Switching to `next()`-loop fixed VoiceOver but broke NVDA (got stuck on an empty item).
     **Final fix: branch on `screenReader.name` and give each reader the mechanism proven to work
     for it** — real Tab presses for NVDA, `next()`-loop for VoiceOver.
   - Modal-open check: same passive-announcement gap on both readers. `next()`-loop fixed VoiceOver
     once given enough step budget (a real run got within one button of the target at 40 steps;
     100 reliably reaches it). **NVDA's modal check still fails** — see "What's still open" below.

**Final verified state (run `30128387849`, commit `92e85f5`): 5 of 6 checks pass for real.**
VoiceOver (macOS): all 3 pass. NVDA (Windows): heading + dropzone pass, modal-open check fails.

## What's still open

**NVDA's modal-open check reproducibly stalls, root cause not yet found.** After opening the
create-calendar modal, NVDA's `next()`-driven browse cursor gets stuck on an item whose text is
`"blank"` and never progresses further — confirmed as a genuine stall, not a step-budget shortfall,
by raising the budget from 40 to 100 steps and getting the exact same stopping point both times. Two
real, most-likely explanations neither confirmed nor ruled out yet: (a) NVDA's virtual browse buffer
is a known category with staleness bugs after dynamic ARIA changes (e.g. background content going
`aria-hidden` when a modal opens) and may need an explicit buffer-reload trigger rather than more
`next()` calls; (b) the "blank" item itself (likely an empty text input in the Settings panel
`next()` has to pass through en route to the modal) may be intercepting focus or otherwise blocking
forward navigation in NVDA's browse mode specifically. This is a screen-reader-automation quirk
under investigation, not a confirmed accessibility defect in the app — VoiceOver's real pass through
the same modal succeeds, and the manual keyboard-only pass and axe suite didn't flag this modal
either. Next step if picked back up: read `error-context.md` from a real run's test-results artifact
(not yet fetched) for the actual accessibility-tree snapshot at the stall point, rather than guessing
further from step-level logs alone.



**A full human listen-through (VoiceOver and NVDA) for prosody/naturalness judgment is still not
done** — this is qualitatively different from what Guidepup's text capture above gives: judging
whether a *sequence* of announcements sounds natural, non-redundant, and non-confusing when actually
heard is not the same as confirming the right text is exposed line-by-line, and remains something
only a human ear does well. Not a hard blocker anymore, though — the automated layer above already
covers the more common failure mode (wrong or missing accessible names/roles) for the flows it
checks.

**NVDA was never a hard "wrong OS" blocker for this project** — this session runs on macOS and has no
Windows machine available *right now*, but Compose Multiplatform Desktop (`composeApp`) already
targets Windows natively via the JVM, so a real NVDA pass against the Desktop build is a legitimate
task on an actual Windows machine (the user's, a VM, whatever), not something structurally
impossible for the project. Checked what that actually requires against JetBrains' own
[Compose Desktop accessibility docs](https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html)
before assuming, since Compose Multiplatform's Desktop accessibility support is a fast-moving,
often-limited part of the framework:

| Platform | Status |
|---|---|
| macOS | Fully supported natively — nothing to configure |
| Windows | Supported via Java Access Bridge, but Access Bridge ships **disabled by default** on Windows |
| Linux | **Not supported at all** — no accessibility bridge exists in Compose Multiplatform Desktop for Linux as of this writing (upstream framework gap, not fixable from this app) |

Two concrete things followed from this, done in this same pass:

1. **Fixed**: `composeApp/build.gradle.kts`'s `nativeDistributions { modules(...) }` list explicitly
   replaces the plugin's default JDK module set (per its own existing comment) — `jdk.accessibility`
   wasn't in it, so the Windows MSI's bundled jlink runtime never had the module Java Access Bridge
   needs. Added it; verified with `./gradlew :composeApp:createDistributable` (macOS-buildable, but
   confirms the module resolves and doesn't break runtime-image creation — the actual Windows MSI
   itself can only be built and tested on Windows).
2. **Still required, not something the app can do for the user**: Access Bridge itself is an
   OS-level toggle, disabled by default — a real NVDA pass needs `%JAVA_HOME%\bin\jabswitch.exe
   /enable` run once on the Windows machine first. This is a one-time IT/user setup step, not a
   bug — but it means "install the app" alone isn't enough for NVDA to see anything; worth a line in
   deployment docs for anyone setting up a Windows instance.
3. **Linux gets nothing from this fix** — genuinely no accessibility bridge exists for Compose
   Desktop on Linux today. Recorded here so nobody re-discovers this from scratch or spends time
   trying to configure something that doesn't exist yet.

The good news underneath all this: the shared Compose UI code (`composeApp/src/commonMain`,
used by Android, iOS, and Desktop alike) already has real `contentDescription`s on icons throughout
(`Icon(Icons.Default.Sync, contentDescription = "Sync Now")` and similar, confirmed via a repo-wide
grep) — this isn't new work needed, since Compose Multiplatform shares its semantics tree
architecture across platforms per JetBrains' own docs. Once Access Bridge is enabled on a given
Windows machine, this same semantic content should already surface reasonably to NVDA/JAWS without
further app-side changes — worth confirming with a real listen-through, not assuming.

This Desktop/Compose section is a separate concern from the web client's automated Guidepup
coverage above (ADR 0011 scopes the WCAG/VPAT target to *the web client* specifically) — a real
human listen-through against the Desktop app on an actual Windows machine (once Access Bridge is
enabled there) is still open, and would need its own pass, not covered by the web-scoped Guidepup
workflow.
