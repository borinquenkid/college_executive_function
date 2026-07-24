import { devices, type PlaywrightTestConfig } from '@playwright/test';
import { screenReaderConfig } from '@guidepup/playwright';

// Real screen-reader automation (ADR 0011 AC-4) — separate from playwright.config.ts (the fast,
// headless axe-core suite in e2e/) because screen readers cannot operate against headless
// browsers at all (every official Guidepup example sets headless: false), and each real
// OS-automation step takes real wall-clock time, unlike simulated DOM events. Different browser
// engine per screen reader matches Guidepup's own reference configs exactly: VoiceOver pairs with
// WebKit (the native macOS browser engine), NVDA pairs with Firefox — not Chromium for either.
//
// Requires local setup this repo does NOT run for you: `npx @guidepup/setup setup` configures
// macOS Accessibility/TCC permissions (or the NVDA portable install on Windows) — that's an
// interactive, machine-specific step intentionally left to whoever runs these tests, not something
// to script unattended. In CI, `guidepup/setup-action` handles this in an isolated, disposable VM
// (see .github/workflows/pr-check.yml's voiceover-a11y/nvda-a11y jobs).
const config: PlaywrightTestConfig = {
  ...screenReaderConfig,
  testDir: './e2e-screenreader',
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
  use: {
    baseURL: 'http://localhost:5173',
  },
  reportSlowTests: null,
  timeout: 5 * 60 * 1000,
  retries: 2,
  projects: [
    {
      name: 'voiceover',
      // Take care to ensure all usage is headed - screen readers cannot operate against
      // headless browsers.
      use: { ...devices['Desktop Safari'], headless: false },
    },
    {
      name: 'nvda',
      use: { ...devices['Desktop Firefox'], headless: false },
    },
  ],
};

export default config;
