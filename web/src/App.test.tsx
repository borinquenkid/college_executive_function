import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'vitest-axe';
import { renderApp, expectNoAxeViolations } from './test/testUtils';

// ADR 0011 (AC-2): runtime accessibility coverage beyond eslint-plugin-jsx-a11y's static JSX-shape
// linting (ADR 0009) — dynamic ARIA state, focus behavior, and rendered markup that only exist
// once the app actually runs. The app has no separable view components (see the AC-2 plan) — every
// "view" here is the real <App /> after switching its single activeTab state.

describe('Calendar — default rendered state (initial tab)', () => {
  it('has no axe violations', async () => {
    await renderApp();

    const results = await axe(document.body);
    expectNoAxeViolations(results);
  });
});

describe('Sources Panel — default rendered state', () => {
  it('has no axe violations', async () => {
    await renderApp();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Sources' }));
    await screen.findByText('Sources Panel');

    const results = await axe(document.body);
    expectNoAxeViolations(results);
  });
});

describe('Sources Panel — file dropzone keyboard reachability (ADR 0011 AC-4)', () => {
  it('is reachable via Tab/focus and activatable via Enter', async () => {
    // Found during AC-4's keyboard-only walkthrough: a <label> wrapping a display:none file
    // input is never keyboard-focusable on its own — axe-core doesn't flag this pattern, so this
    // regression wouldn't be caught by the axe-violation tests above.
    await renderApp();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Sources' }));
    await screen.findByText('Sources Panel');

    const dropzone = screen.getByRole('button', { name: /Click or Drag File Here/ });
    const fileInput = document.getElementById('sourceFileInput') as HTMLInputElement;
    const clickSpy = vi.fn();
    fileInput.addEventListener('click', clickSpy);

    dropzone.focus();
    expect(document.activeElement).toBe(dropzone);

    await user.keyboard('{Enter}');
    expect(clickSpy).toHaveBeenCalledTimes(1);
  });
});

describe('Studio Panel — default rendered state', () => {
  it('has no axe violations', async () => {
    await renderApp();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Studio Panel' }));
    await screen.findByText(/Ask me anything about your syllabi/);

    const results = await axe(document.body);
    expectNoAxeViolations(results);
  });
});

describe('Settings — default rendered state', () => {
  it('has no axe violations', async () => {
    await renderApp();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Settings' }));
    await screen.findByRole('heading', { name: 'Settings' });

    const results = await axe(document.body);
    expectNoAxeViolations(results);
  });
});

describe('Task decomposition modal — open state', () => {
  it('has no axe violations', async () => {
    await renderApp({
      events: [
        {
          id: 'evt-1',
          title: 'Midterm Essay',
          source: 'AI_GENERATED',
          category: 'DEADLINE',
          syncStatus: 'SYNCED',
          date: '2026-08-15',
          updatedAt: 0,
          warning: null,
          studyPlanStart: null,
          gradeWeight: null,
          completionStatus: 'INCOMPLETE',
        },
      ],
      decomposedTasks: [],
    });
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Break it Down' }));
    await screen.findByText('Break Down: Midterm Essay');

    const results = await axe(document.body);
    expectNoAxeViolations(results);
  });
});

describe('Create calendar modal — open state', () => {
  it('has no axe violations', async () => {
    await renderApp({ googleLinked: true, calendars: [{ id: 'cal-1', name: 'Primary' }] });
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Settings' }));
    await user.click(await screen.findByRole('button', { name: '+ Create New Calendar' }));
    await screen.findByText('Create New Google Calendar');

    const results = await axe(document.body);
    expectNoAxeViolations(results);
  });
});
