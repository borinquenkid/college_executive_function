import { defineConfig, devices } from '@playwright/test';

// Real-browser accessibility layer (ADR 0011 AC-3) — complements src/App.test.tsx's Vitest/jsdom
// suite, which structurally cannot run axe's color-contrast rule (jsdom has no layout engine, so
// every element's bounding box is 0x0 and axe treats everything as invisible). page.route() in
// e2e/mockApi.ts intercepts every /api/* request before it reaches Vite's dev-server proxy, so no
// real backend (Ktor server, Gemini) is needed to run these — webServer below only starts Vite.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
  use: {
    baseURL: 'http://localhost:5173',
  },
  projects: [
    // Accessibility-focused suite, not cross-browser compatibility testing — chromium only.
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
