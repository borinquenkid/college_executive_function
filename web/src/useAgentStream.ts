import { useState, useEffect, useCallback, useRef } from 'react';

export interface ToolCallState {
  toolName: string;
  arguments: string;
  success?: boolean;
}

export interface AgentStreamState {
  isActive: boolean;
  runId: string | null;
  reasoning: string;
  toolCalls: ToolCallState[];
  responseText: string;
  error: string | null;
}

export function useAgentStream() {
  const [state, setState] = useState<AgentStreamState>({
    isActive: false,
    runId: null,
    reasoning: '',
    toolCalls: [],
    responseText: '',
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

  const startStream = useCallback((query: string) => {
    // Close any active stream first
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    // Reset state
    setState({
      isActive: true,
      runId: null,
      reasoning: '',
      toolCalls: [],
      responseText: '',
      error: null,
    });

    const url = `/api/agent/stream?query=${encodeURIComponent(query)}`;
    const eventSource = new EventSource(url);
    eventSourceRef.current = eventSource;

    eventSource.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        const { type, data } = payload;

        switch (type) {
          case 'RUN_STARTED':
            setState((prev) => ({
              ...prev,
              runId: data?.runId || 'unknown-run',
            }));
            break;

          case 'REASONING_DELTA':
            setState((prev) => ({
              ...prev,
              reasoning: prev.reasoning + (data?.text || ''),
            }));
            break;

          case 'TOOL_CALL_START':
            setState((prev) => ({
              ...prev,
              toolCalls: [
                ...prev.toolCalls,
                {
                  toolName: data?.toolName || 'unknown-tool',
                  arguments: data?.arguments || '',
                },
              ],
            }));
            break;

          case 'TOOL_CALL_RESULT':
            setState((prev) => {
              const updatedCalls = [...prev.toolCalls];
              if (updatedCalls.length > 0) {
                const lastIdx = updatedCalls.length - 1;
                updatedCalls[lastIdx] = {
                  ...updatedCalls[lastIdx],
                  success: data?.success ?? true,
                };
              }
              return {
                ...prev,
                toolCalls: updatedCalls,
              };
            });
            break;

          case 'TEXT_MESSAGE_DELTA':
            setState((prev) => ({
              ...prev,
              responseText: prev.responseText + (data?.text || ''),
            }));
            break;

          case 'ERROR':
            setState((prev) => ({
              ...prev,
              error: data?.message || 'An error occurred during execution.',
            }));
            break;

          case 'RUN_FINISHED':
            eventSource.close();
            setState((prev) => ({ ...prev, isActive: false }));
            break;

          default:
            console.warn('Unknown event type received:', type);
        }
      } catch (err) {
        console.error('Failed to parse SSE payload:', err);
      }
    };

    eventSource.onerror = (err) => {
      console.error('SSE Error:', err);
      setState((prev) => ({
        ...prev,
        error: 'Connection to the agent stream was interrupted.',
        isActive: false,
      }));
      eventSource.close();
    };
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
    };
  }, []);

  return {
    ...state,
    startStream,
    stopStream,
  };
}
