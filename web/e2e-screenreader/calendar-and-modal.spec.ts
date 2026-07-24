import { screenReaderTest as test } from '@guidepup/playwright';
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

    // useFocusTrap.ts (ADR 0009) moves focus into the modal on open — the screen reader should
    // pick that up and announce the dialog/title without any extra navigation on our part.
    // lastSpokenPhrase() is a live query (AppleScript/NVDA), so give the speech pipeline a beat
    // to catch up with the just-fired native focus() call before reading it.
    await page.waitForTimeout(1000);

    const announced = (await screenReader.lastSpokenPhrase()).toLowerCase();
    expect(announced).toContain('create new google calendar');
  });
});
