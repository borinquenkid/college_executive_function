import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// Not using vitest's `globals: true`, so @testing-library/react's own auto-cleanup detection
// (which hooks a jest-like global afterEach) never fires — without this, each render() piles up
// in the same jsdom document across tests in a file instead of being torn down between them.
afterEach(() => {
  cleanup();
});
