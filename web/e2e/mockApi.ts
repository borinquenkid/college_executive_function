import type { Page } from '@playwright/test';

export interface ApiOverrides {
  sources?: unknown[];
  events?: unknown[];
  hasApiKey?: boolean;
  studyPreferences?: Record<string, unknown> | null;
  googleLinked?: boolean;
  calendars?: unknown[];
  decomposedTasks?: unknown[];
}

/**
 * Intercepts every /api/* request at the browser's network layer, before it would otherwise reach
 * Vite's dev-server proxy (vite.config.ts's server.proxy['/api'] -> localhost:8080) — so these
 * tests never need the real Ktor server or Gemini running. Mirrors
 * src/test/testUtils.tsx's stubFetch shape for the same set of endpoints App.tsx calls on mount
 * (checkSession -> GET /api/settings, then fetchSources/fetchEvents/fetchSettings/
 * fetchGoogleAuthStatus once sessionReady flips true) plus the on-demand endpoints exercised by
 * modal tests.
 */
export async function mockApi(page: Page, overrides: ApiOverrides = {}) {
  const {
    sources = [],
    events = [],
    hasApiKey = false,
    studyPreferences = null,
    googleLinked = false,
    calendars = [],
    decomposedTasks = [],
  } = overrides;

  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;

    if (path.startsWith('/api/settings')) {
      return route.fulfill({ json: { hasApiKey, studyPreferences } });
    }
    if (path.startsWith('/api/sources')) {
      return route.fulfill({ json: sources });
    }
    if (path.startsWith('/api/events')) {
      return route.fulfill({ json: events });
    }
    if (path.startsWith('/api/auth/google/status')) {
      return route.fulfill({ json: { linked: googleLinked } });
    }
    if (path.startsWith('/api/calendars')) {
      return route.fulfill({ json: calendars });
    }
    if (path.startsWith('/api/tasks/decompose')) {
      return route.fulfill({ json: decomposedTasks });
    }

    return route.fulfill({ status: 404, json: { error: `mockApi: no mock configured for ${path}` } });
  });
}
