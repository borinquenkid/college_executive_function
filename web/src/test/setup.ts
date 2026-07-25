import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// Not using vitest's `globals: true`, so @testing-library/react's own auto-cleanup detection
// (which hooks a jest-like global afterEach) never fires — without this, each render() piles up
// in the same jsdom document across tests in a file instead of being torn down between them.
afterEach(() => {
  cleanup();
});

// jsdom doesn't implement EventSource, so any test that reaches the app's SSE-backed hooks
// (useAgentStream, useSourceStream, useDecomposeStream) throws a ReferenceError on `new
// EventSource(...)`. This stub is just enough for those hooks to mount and hold a connection
// object without crashing — no test today asserts on live message delivery.
class FakeEventSource {
  url: string;
  onmessage: ((event: MessageEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  constructor(url: string) {
    this.url = url;
  }
  close() {}
}

if (typeof globalThis.EventSource === 'undefined') {
  (globalThis as unknown as { EventSource: typeof EventSource }).EventSource =
    FakeEventSource as unknown as typeof EventSource;
}
