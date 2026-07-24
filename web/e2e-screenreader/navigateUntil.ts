import type { ScreenReaderPlaywright } from '@guidepup/playwright';

const MAX_NAVIGATION_STEPS = 20;

// Guidepup's own reference tests (headerNavigation.ts, used across their screenreader/NVDA/VoiceOver
// example suites) never assert on spoken content immediately after navigateToWebContent() — the
// browse cursor lands wherever the OS/browser puts it first (often a landmark region), not the item
// under test. This drives the cursor forward with next() until the target text is reached, matching
// that documented pattern instead of assuming the first item is the one we want.
export async function navigateUntilItemTextIncludes(screenReader: ScreenReaderPlaywright, target: string) {
  const normalizedTarget = target.toLowerCase();
  let steps = 0;
  let itemText = (await screenReader.itemText()).toLowerCase();

  while (!itemText.includes(normalizedTarget) && steps < MAX_NAVIGATION_STEPS) {
    await screenReader.next();
    itemText = (await screenReader.itemText()).toLowerCase();
    steps++;
  }

  if (!itemText.includes(normalizedTarget)) {
    throw new Error(
      `Screen reader did not reach an item containing "${target}" within ${MAX_NAVIGATION_STEPS} steps. Last item text: "${itemText}"`
    );
  }
}
