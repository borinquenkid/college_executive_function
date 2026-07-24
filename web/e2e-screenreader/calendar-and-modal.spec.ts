import { screenReaderTest as test } from '@guidepup/playwright';
import { NVDAKeyCodeCommands } from '@guidepup/guidepup';
import { expect } from '@playwright/test';
import { mockApi } from '../e2e/mockApi';
import { navigateUntilItemTextIncludes } from './navigateUntil';

// Real screen-reader coverage (ADR 0011 AC-4), scoped to two representative checks rather than
// repeating every view/modal from the fast jsdom (App.test.tsx) and headless-Playwright/axe
// (e2e/accessibility.spec.ts) suites — each real screen-reader step takes real wall-clock time
// (driving actual OS automation), so this stays proportionate to a first real pass.

test.describe('Calendar — main heading announces on load', () => {
  test('announces the page heading', async ({ page, screenReader }) => {
    await mockApi(page);
    await page.goto('/');
    await page.getByText('Academic Calendar').waitFor();

    await screenReader.navigateToWebContent();

    // navigateToWebContent() lands wherever the browse cursor starts (often a landmark, not the
    // heading itself) — drive forward to the heading rather than asserting on the first item.
    await navigateUntilItemTextIncludes(screenReader, 'academic calendar');
  });
});

test.describe('Create calendar modal — dialog announces on open', () => {
  test('announces as a dialog with its title', async ({ page, screenReader }) => {
    await mockApi(page, { googleLinked: true, calendars: [{ id: 'cal-1', name: 'Primary' }] });
    await page.goto('/');
    await page.getByText('Academic Calendar').waitFor();

    await screenReader.navigateToWebContent();

    await page.getByRole('button', { name: 'Settings' }).click();
    await page.getByRole('button', { name: '+ Create New Calendar' }).click();
    await page.getByText('Create New Google Calendar').waitFor();

    // useFocusTrap.ts (ADR 0009) moves real DOM focus into the modal on open. Two different fixes
    // per reader, both confirmed against real CI runs:
    //
    // VoiceOver: passive focus-driven announcements weren't observed, but re-syncing with
    // navigateToWebContent() and driving forward with next() reliably reaches the dialog title.
    //
    // NVDA: navigateToWebContent() is NOT safe to call again here — its internal re-sync sends a
    // real Escape keypress (NVDAKeyCodeCommands.exitFocusMode), which our focus trap treats as
    // "close the dialog" globally regardless of what's focused, so it was silently closing our own
    // modal (confirmed via a real run's error-context.md: the accessibility snapshot at failure
    // showed the Settings panel with focus back on the "+ Create New Calendar" button — exactly
    // what useFocusTrap's cleanup does on close — with no modal anywhere in the tree). NVDA's
    // lastSpokenPhrase() also isn't a live OS query like VoiceOver's — it's the last entry of
    // Guidepup's own capture log (see NVDA.ts) — so a passive focus change is never captured
    // without an explicit command. NVDA-Tab (reportCurrentFocus) re-announces whatever currently
    // has system focus without moving it or touching Escape — with that fixed, a real run showed
    // NVDA correctly announcing "close, button, focused": accurate (useFocusTrap.ts focuses the
    // first focusable element on open, per the W3C ARIA APG modal dialog pattern's own default
    // recommendation), but it doesn't include the dialog's own name — a real, known NVDA
    // per-object-report behavior, not a bug in this app. Confirm that separately with next() — but
    // a real run showed forward-only next() walking straight through the rest of the modal
    // (Calendar Name field, Cancel, the disabled Create button — "button, unavailable, create")
    // and never finding the title, because the <h2> sits BEFORE the Close button in DOM order
    // (see App.tsx) — the very first focusable element focus lands on. Go backward instead: the
    // title is the immediately preceding item.
    if (screenReader.name === 'NVDA') {
      await screenReader.perform(NVDAKeyCodeCommands.reportCurrentFocus);
      const focusAnnouncement = (await screenReader.lastSpokenPhrase()).toLowerCase();
      expect(focusAnnouncement).toContain('button');

      await navigateUntilItemTextIncludes(screenReader, 'create new google calendar', 5, 'previous');
    } else {
      await screenReader.navigateToWebContent();
      await navigateUntilItemTextIncludes(screenReader, 'create new google calendar', 100);
    }
  });
});
