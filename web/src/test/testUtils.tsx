import { render, waitFor } from '@testing-library/react';
import { expect, vi } from 'vitest';
import type { AxeCore } from 'vitest-axe';
import App from '../App';

/**
 * `vitest-axe`'s own `toHaveNoViolations` matcher's type augmentation (v0.1.0) doesn't match
 * Vitest 4's actual `Assertion` interface shape, so `tsc -b` rejects it even though it works at
 * runtime — asserting against the well-typed `violations` array directly sidesteps that instead
 * of fighting a stale library's types. Failure message lists each violation's rule id and the
 * offending selector so a failure is actionable without re-running with a debugger attached.
 */
export function expectNoAxeViolations(results: AxeCore.AxeResults) {
  const summary = results.violations
    .map((v: AxeCore.Result) => `${v.id}: ${v.description} (${v.nodes.map(n => n.target.join(' ')).join(', ')})`)
    .join('\n');
  expect(results.violations, summary ? `Axe violations found:\n${summary}` : undefined).toHaveLength(0);
}

/** Minimal duck-typed Fetch Response — App.tsx only ever reads .ok/.status and awaits .json(). */
function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  };
}

export interface FetchOverrides {
  sources?: unknown[];
  events?: unknown[];
  hasApiKey?: boolean;
  studyPreferences?: Record<string, unknown> | null;
  googleLinked?: boolean;
  calendars?: unknown[];
  decomposedTasks?: unknown[];
}

/**
 * Stubs global.fetch to answer every request App.tsx fires on mount
 * (checkSession -> GET /api/settings, then fetchSources/fetchEvents/fetchSettings/
 * fetchGoogleAuthStatus once sessionReady flips true — see App.tsx's mount effects) plus the
 * on-demand endpoints (/api/tasks/decompose, /api/calendars) exercised by modal tests.
 */
function stubFetch(overrides: FetchOverrides = {}) {
  const {
    sources = [],
    events = [],
    hasApiKey = false,
    studyPreferences = null,
    googleLinked = false,
    calendars = [],
    decomposedTasks = [],
  } = overrides;

  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input.toString();

    if (url.startsWith('/api/settings')) {
      return jsonResponse({ hasApiKey, studyPreferences });
    }
    if (url.startsWith('/api/sources')) {
      return jsonResponse(sources);
    }
    if (url.startsWith('/api/events')) {
      return jsonResponse(events);
    }
    if (url.startsWith('/api/auth/google/status')) {
      return jsonResponse({ linked: googleLinked });
    }
    if (url.startsWith('/api/calendars')) {
      return jsonResponse(calendars);
    }
    if (url.startsWith('/api/tasks/decompose')) {
      return jsonResponse(decomposedTasks);
    }

    throw new Error(`testUtils.stubFetch: no mock configured for ${url}`);
  });

  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

/**
 * Renders the real <App /> with fetch stubbed, and waits past the initial "needs LTI launch"
 * session-check gate so the actual tabbed UI (not the loading/error placeholder) is on screen.
 */
export async function renderApp(overrides: FetchOverrides = {}) {
  const fetchMock = stubFetch(overrides);
  const result = render(<App />);

  await waitFor(() => {
    expect(result.getByText('Academic Calendar')).toBeInTheDocument();
  });

  return { ...result, fetchMock };
}
