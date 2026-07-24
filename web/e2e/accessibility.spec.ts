import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mockApi } from './mockApi';

// Real-browser accessibility coverage (ADR 0011 AC-3) — closes the gap src/App.test.tsx's
// Vitest/jsdom suite structurally can't cover (color-contrast and any other paint-dependent rule;
// see playwright.config.ts's comment). Mirrors App.test.tsx's own coverage: same 4 views + 2
// modals, same tab-switching/button-click approach, since the app has no separable view
// components. Runs axe's full default ruleset rather than restricting to color-contrast — overlap
// with the jsdom suite on structural rules is harmless, and a real browser is authoritative for
// anything else paint-dependent this suite wasn't specifically written to anticipate.

test.describe('Calendar — default rendered state (initial tab)', () => {
  test('has no axe violations', async ({ page }) => {
    await mockApi(page);
    await page.goto('/');
    await expect(page.getByText('Academic Calendar')).toBeVisible();

    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations).toEqual([]);
  });
});

test.describe('Sources Panel — default rendered state', () => {
  test('has no axe violations', async ({ page }) => {
    await mockApi(page);
    await page.goto('/');
    await page.getByRole('button', { name: 'Sources' }).click();
    await expect(page.getByText('Sources Panel')).toBeVisible();

    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations).toEqual([]);
  });
});

test.describe('Studio Panel — default rendered state', () => {
  test('has no axe violations', async ({ page }) => {
    await mockApi(page);
    await page.goto('/');
    await page.getByRole('button', { name: 'Studio Panel' }).click();
    await expect(page.getByText(/Ask me anything about your syllabi/)).toBeVisible();

    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations).toEqual([]);
  });
});

test.describe('Settings — default rendered state', () => {
  test('has no axe violations', async ({ page }) => {
    await mockApi(page);
    await page.goto('/');
    await page.getByRole('button', { name: 'Settings' }).click();
    await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();

    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations).toEqual([]);
  });
});

test.describe('Task decomposition modal — open state', () => {
  test('has no axe violations', async ({ page }) => {
    await mockApi(page, {
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
    await page.goto('/');

    await page.getByRole('button', { name: 'Break it Down' }).click();
    await expect(page.getByText('Break Down: Midterm Essay')).toBeVisible();

    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations).toEqual([]);
  });
});

test.describe('Create calendar modal — open state', () => {
  test('has no axe violations', async ({ page }) => {
    await mockApi(page, { googleLinked: true, calendars: [{ id: 'cal-1', name: 'Primary' }] });
    await page.goto('/');

    await page.getByRole('button', { name: 'Settings' }).click();
    await page.getByRole('button', { name: '+ Create New Calendar' }).click();
    await expect(page.getByText('Create New Google Calendar')).toBeVisible();

    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations).toEqual([]);
  });
});
