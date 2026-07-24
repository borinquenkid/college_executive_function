import { useCallback, useRef, useState } from 'react';

// Mirrors SourceStatus (composeApp/src/commonMain/kotlin/.../SourceItem.kt) — the values the
// server's GET /api/sources/{id}/stream SSE endpoint emits (ADR 0012).
export type SourceDigestionStatus =
  | 'PENDING'
  | 'ANALYZING_CONTEXT'
  | 'EXTRACTING_DELIVERABLES'
  | 'RESOLVING_CONFLICTS'
  | 'DONE'
  | 'FAILED';

const TERMINAL_STATUSES = new Set<SourceDigestionStatus>(['DONE', 'FAILED']);

export interface SourceStreamState {
  isActive: boolean;
  status: SourceDigestionStatus | null;
  error: string | null;
}

export function useSourceStream() {
  const [state, setState] = useState<SourceStreamState>({
    isActive: false,
    status: null,
    error: null,
  });

  const eventSourceRef = useRef<EventSource | null>(null);

  const stopStream = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
  }, []);

  const startStream = useCallback((sourceId: string) => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    setState({ isActive: true, status: null, error: null });

    const url = `/api/sources/${encodeURIComponent(sourceId)}/stream`;
    const eventSource = new EventSource(url);
    eventSourceRef.current = eventSource;

    eventSource.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        const { type, data } = payload;

        switch (type) {
          case 'SOURCE_STATUS': {
            const status = data?.status as SourceDigestionStatus | undefined;
            setState((prev) => ({ ...prev, status: status ?? prev.status }));
            if (status && TERMINAL_STATUSES.has(status)) {
              stopStream();
              setState((prev) => ({ ...prev, isActive: false }));
            }
            break;
          }

          case 'ERROR':
            setState((prev) => ({
              ...prev,
              error: data?.message || 'Digestion stream error.',
              isActive: false,
            }));
            stopStream();
            break;

          case 'RUN_FINISHED':
            // Belt-and-suspenders close: the server also stops emitting after a terminal
            // SOURCE_STATUS, but this covers the no-status-ever-recorded (ERROR-only) case too.
            stopStream();
            setState((prev) => ({ ...prev, isActive: false }));
            break;

          default:
            console.warn('Unknown source stream event type received:', type);
        }
      } catch (err) {
        console.error('Failed to parse source stream payload:', err);
      }
    };

    eventSource.onerror = () => {
      setState((prev) => ({
        ...prev,
        error: 'Connection to the digestion stream was interrupted.',
        isActive: false,
      }));
      stopStream();
    };
  }, [stopStream]);

  return {
    ...state,
    startStream,
    stopStream,
  };
}

/** Short, human-readable label for each digestion phase — used by the sources dropzone/list. */
export function describeSourceStatus(status: SourceDigestionStatus | null): string {
  switch (status) {
    case 'PENDING':
      return 'Queued…';
    case 'ANALYZING_CONTEXT':
      return 'Reading document…';
    case 'EXTRACTING_DELIVERABLES':
      return 'Extracting deadlines…';
    case 'RESOLVING_CONFLICTS':
      return 'Resolving conflicts & writing to calendar…';
    case 'DONE':
      return 'Done';
    case 'FAILED':
      return 'Failed';
    default:
      return 'Processing…';
  }
}
