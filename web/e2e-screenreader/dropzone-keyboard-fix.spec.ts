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
test.describe('Sources dropzone — real screen reader announcement', () => {
  test('announces as a button with its label when reached', async ({ page, screenReader }) => {
    await mockApi(page);
    await page.goto('/');
    await page.getByText('Academic Calendar').waitFor();

    await screenReader.navigateToWebContent();

    await page.getByRole('button', { name: 'Sources' }).click();
    await page.getByText('Sources Panel').waitFor();

    // Real Tab-based keyboard reachability of the dropzone is already covered by the fast jsdom
    // regression test (App.test.tsx) — this confirms the screen reader announces the fixed
    // control correctly (role + label) once reached, using the same next()-loop navigation as the
    // other checks in this suite (a real Tab-press loop worked for NVDA but VoiceOver's live
    // lastSpokenPhrase() query didn't reliably reflect Tab-driven focus changes in CI — see
    // calendar-and-modal.spec.ts for the same finding on the modal-open check).
    await screenReader.navigateToWebContent();
    await navigateUntilItemTextIncludes(screenReader, 'click or drag file here', 40);

    const announced = (await screenReader.lastSpokenPhrase()).toLowerCase();
    expect(announced).toContain('button');
    expect(announced).toContain('click or drag file here');
  });
});
