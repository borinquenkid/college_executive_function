import { screenReaderTest as test } from '@guidepup/playwright';
import { expect } from '@playwright/test';
import { mockApi } from '../e2e/mockApi';

// Real screen-reader regression check for the AC-4 keyboard-only-pass finding (ADR 0011): the
// Sources tab's file-upload dropzone was a <label> wrapping a hidden input — reachable by mouse
// only, invisible to keyboard/screen-reader users. Fixed as a real <button> (see App.tsx). This
// confirms the fix is ALSO correctly exposed to a real screen reader's accessibility tree, not
// just reachable via raw DOM focus (which the jsdom/axe suites already cover) — a screen reader
// could in principle still announce a focusable element with no meaningful name.
test.describe('Sources dropzone — real screen reader announcement', () => {
  test('announces as a button with its label when tabbed to', async ({ page, screenReader }) => {
    await mockApi(page);
    await page.goto('/');
    await page.getByText('Academic Calendar').waitFor();

    await screenReader.navigateToWebContent();

    await page.getByRole('button', { name: 'Sources' }).click();
    await page.getByText('Sources Panel').waitFor();

    // Tab forward until reaching the dropzone rather than assuming a fixed tab-stop count — the
    // exact number of stops before it isn't part of the contract under test, real keyboard
    // reachability is. lastSpokenPhrase() is a live query, so give speech a beat after each Tab.
    const MAX_TAB_PRESSES = 20;
    let announced = '';
    let presses = 0;

    while (!announced.includes('click or drag file here') && presses < MAX_TAB_PRESSES) {
      await screenReader.press('Tab');
      await page.waitForTimeout(300);
      announced = (await screenReader.lastSpokenPhrase()).toLowerCase();
      presses++;
    }

    expect(announced).toContain('button');
    expect(announced).toContain('click or drag file here');
  });
});
