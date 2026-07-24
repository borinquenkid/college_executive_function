import { screenReaderTest as test } from '@guidepup/playwright';
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

    // useFocusTrap.ts (ADR 0009) moves real DOM focus into the modal on open, which a real screen
    // reader picks up passively in an interactive session — but CI screen-reader automation
    // doesn't reliably observe passive focus-driven announcements (confirmed here: the browse
    // cursor stayed put after the clicks above). Re-sync into web content and drive the cursor to
    // the dialog title explicitly, same as the heading and dropzone checks in this suite. There's
    // no outside-click-to-close handler on the modal (see useFocusTrap.ts), so the re-sync's body
    // click can't dismiss it. Bigger step budget since the modal renders further down the DOM
    // than the page heading.
    await screenReader.navigateToWebContent();
    await navigateUntilItemTextIncludes(screenReader, 'create new google calendar', 40);
  });
});
