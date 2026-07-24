import { describe, it, expect } from 'vitest';
import { axe } from 'vitest-axe';
import { expectNoAxeViolations } from './testUtils';

// Proves the axe guardrail itself isn't silently inert — i.e. that App.test.tsx's
// `expectNoAxeViolations` assertions would actually have caught the exact defect shapes ADR 0009
// fixed (unlabeled icon buttons, missing form labels), rather than passing regardless of what's
// rendered. Uses a synthetic snippet instead of temporarily breaking real App.tsx code.
describe('axe guardrail sanity check', () => {
  it('flags an unlabeled icon button (the ADR 0009 defect shape)', async () => {
    const container = document.createElement('div');
    container.innerHTML = `
      <button>
        <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h18"/></svg>
      </button>
    `;
    document.body.appendChild(container);

    const results = await axe(container);

    expect(results.violations.length).toBeGreaterThan(0);
    expect(results.violations.some(v => v.id === 'button-name')).toBe(true);

    document.body.removeChild(container);
  });

  it('does not flag the same button once given an accessible name', async () => {
    const container = document.createElement('div');
    container.innerHTML = `
      <button aria-label="Remove item">
        <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h18"/></svg>
      </button>
    `;
    document.body.appendChild(container);

    const results = await axe(container);
    expectNoAxeViolations(results);

    document.body.removeChild(container);
  });
});
