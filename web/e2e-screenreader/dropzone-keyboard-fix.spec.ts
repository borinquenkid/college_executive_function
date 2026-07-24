import { screenReaderTest as test } from '@guidepup/playwright';
import { expect } from '@playwright/test';
import { mockApi } from '../e2e/mockApi';
import { navigateUntilItemTextIncludes } from './navigateUntil';

// Real screen-reader regression check for the AC-4 keyboard-only-pass finding (ADR 0011): the
// Sources tab's file-upload dropzone was a <label> wrapping a hidden input — reachable by mouse
// only, invisible to keyboard/screen-reader users. Fixed as a real <button> (see App.tsx). This
// confirms the fix is ALSO correctly exposed to a real screen reader's accessibility tree, not
// just reachable via raw DOM focus (which the jsdom/axe suites already cover) — a screen reader
// could in principle still announce a focusable element with no meaningful name.
//
// The two screen readers need different navigation mechanisms here, confirmed by real CI runs:
// NVDA's live lastSpokenPhrase() reliably reflected real Tab-driven focus changes, but
// VoiceOver's didn't; conversely VoiceOver's next()-loop reliably reached the target, but NVDA's
// got stuck on an unrelated empty item within budget. Each reader uses whichever mechanism was
// actually observed to work for it (same NVDA-vs-VoiceOver split found on the modal-open check
// in calendar-and-modal.spec.ts).
test.describe('Sources dropzone — real screen reader announcement', () => {
  test('announces as a button with its label when reached', async ({ page, screenReader }) => {
    await mockApi(page);
    await page.goto('/');
    await page.getByText('Academic Calendar').waitFor();

    await screenReader.navigateToWebContent();

    await page.getByRole('button', { name: 'Sources' }).click();
    await page.getByText('Sources Panel').waitFor();

    let announced: string;

    if (screenReader.name === 'NVDA') {
      const MAX_TAB_PRESSES = 20;
      announced = '';
      let presses = 0;

      while (!announced.includes('click or drag file here') && presses < MAX_TAB_PRESSES) {
        await screenReader.press('Tab');
        await page.waitForTimeout(300);
        announced = (await screenReader.lastSpokenPhrase()).toLowerCase();
        presses++;
      }
    } else {
      await screenReader.navigateToWebContent();
      await navigateUntilItemTextIncludes(screenReader, 'click or drag file here', 40);
      announced = (await screenReader.lastSpokenPhrase()).toLowerCase();
    }

    expect(announced).toContain('button');
    expect(announced).toContain('click or drag file here');
  });
});
