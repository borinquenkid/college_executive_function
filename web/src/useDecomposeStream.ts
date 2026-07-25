import { useCallback, useRef, useState } from 'react';

// Mirrors DecomposedTask (composeApp/src/commonMain/kotlin/.../DecomposedTask.kt) — the shape
// the server's GET /api/events/{id}/decompose/stream SSE endpoint emits in its STATE_SNAPSHOT
// payload (Phase 6.5, ROADMAP.md).
export interface DecomposedTask {
  title: string;
  daysBeforeDue: number;
  description: string;
}

export interface DecomposeStreamState {
  isActive: boolean;
  tasks: DecomposedTask[];
  error: string | null;
}

export function useDecomposeStream() {
  const [state, setState] = useState<DecomposeStreamState>({
    isActive: false,
    tasks: [],
    error: null,
  });

  const eventSourceRef = useRef<EventSource | null>(null);

  const stopStream = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    setState((prev) => ({ ...prev, isActive: false }));
  }, []);

  const startStream = useCallback((eventId: string) => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    setState({ isActive: true, tasks: [], error: null });

    const url = `/api/events/${encodeURIComponent(eventId)}/decompose/stream`;
    const eventSource = new EventSource(url);
    eventSourceRef.current = eventSource;

    eventSource.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        const { type, data } = payload;

        switch (type) {
          case 'STATE_SNAPSHOT':
            setState((prev) => ({
              ...prev,
              tasks: Array.isArray(data?.decomposedTasks) ? data.decomposedTasks : prev.tasks,
            }));
            break;

          case 'ERROR':
            setState((prev) => ({
              ...prev,
              error: data?.message || 'Decomposition stream error.',
              isActive: false,
            }));
            eventSource.close();
            break;

          case 'RUN_FINISHED':
            eventSource.close();
            setState((prev) => ({ ...prev, isActive: false }));
            break;

          default:
            // RUN_STARTED / TOOL_CALL_START / TOOL_CALL_RESULT — no dedicated UI state yet,
            // isActive already covers the "decomposing" indicator.
            break;
        }
      } catch (err) {
        console.error('Failed to parse decompose stream payload:', err);
      }
    };

    eventSource.onerror = () => {
      setState((prev) => ({
        ...prev,
        error: 'Connection to the decomposition stream was interrupted.',
        isActive: false,
      }));
      eventSource.close();
    };
  }, []);

  return {
    ...state,
    startStream,
    stopStream,
  };
}
