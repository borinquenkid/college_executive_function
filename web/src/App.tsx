import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useAgentStream } from './useAgentStream';
import { useSourceStream, describeSourceStatus, type SourceDigestionStatus } from './useSourceStream';
import { useFocusTrap } from './useFocusTrap';
import type { StudyPreferences, RemoteCalendarMetadata } from './cef-types';
export type { StudyPreferences, RemoteCalendarMetadata } from './cef-types';

// API Interface Mappings matching KMP/Ktor Server backend
interface WebSource {
  id: string;
  title: string;
  originUri: string | null;
  type: string;
  category: string;
  metadata: string | null;
  updatedAt: number;
  // ADR 0012 — present once the server includes it on SourceItem; optional so older cached
  // responses (or a server not yet on this version) don't break the type.
  status?: SourceDigestionStatus;
}

interface Event {
  id: string | null;
  title: string;
  source: string;
  category: string;
  syncStatus: string;
  date: string;
  updatedAt: number;
  warning: string | null;
  studyPlanStart: string | null;
  gradeWeight: number | null;
  completionStatus: string;
  startTime?: string;
  endTime?: string;
}

interface WebChatMessage {
  author: string;
  content: string;
}

interface DecomposedTask {
  title: string;
  daysBeforeDue: number;
  description: string;
}

// StudyPreferences and RemoteCalendarMetadata are imported from ./cef-types
// (auto-generated from Kotlin @Serializable classes via ./gradlew :server:generateTypescript)

export default function App() {
  const [activeTab, setActiveTab] = useState<'calendar' | 'sources' | 'chat' | 'settings'>('calendar');
  const [sources, setSources] = useState<WebSource[]>([]);
  const [events, setEvents] = useState<Event[]>([]);
  const [chatHistory, setChatHistory] = useState<WebChatMessage[]>([
    { author: 'AI', content: 'Hello! Ask me anything about your syllabi, textbooks, or schedule context.' }
  ]);
  
  // Settings State
  // apiKey only ever holds text the user just typed in this session — the server
  // never sends the stored key back to the client. hasApiKey reflects whether one
  // is already configured server-side, so the UI can show that without the secret.
  const [apiKey, setApiKey] = useState('');
  const [hasApiKey, setHasApiKey] = useState(false);
  const [preferences, setPreferences] = useState<StudyPreferences>({
    studyStartHour: 9,
    studyEndHour: 21,
    lunchStartHour: 12,
    lunchEndHour: 13,
    dinnerStartHour: 17,
    dinnerEndHour: 19,
    maxStudyBlockHours: 2,
    preferredBreakMinutes: 30,
    shareAnonymousBugReports: false,
    googleCalendarId: 'default',
    googleCalendarName: 'CEF Academic',
    semesterStart: null,
    semesterEnd: null
  });

  // Session bootstrap — every route requires a session cookie now (see Application.kt's
  // resolveStudentId). A session can only be minted by a verified LTI launch (docs/adr/0006), so
  // unlike the old model there's nothing this client can POST to create one — it can only check
  // whether a launch already happened and, if not, tell the student to go relaunch via their LMS.
  const [sessionReady, setSessionReady] = useState(false);
  const [sessionError, setSessionError] = useState<string | null>(null);
  const [needsLtiLaunch, setNeedsLtiLaunch] = useState(false);

  // Action/Loading States
  const [isSyncing, setIsSyncing] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [isDecomposing, setIsDecomposing] = useState(false);

  // Toast notifications — a plain alert()/confirm() blocks the JS main thread until a human
  // dismisses it, which froze the tab (and any CDP-driven automation) after long-running
  // fetches like the syllabus upload. A toast never blocks, so it's used for anything fired
  // automatically after an await rather than from a direct user click.
  const [toast, setToast] = useState<{ message: string; kind: 'success' | 'error' } | null>(null);
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(timer);
  }, [toast]);

  // Google Calendar state
  const [googleLinked, setGoogleLinked] = useState(false);
  const [availableCalendars, setAvailableCalendars] = useState<RemoteCalendarMetadata[]>([]);
  const [isLoadingCalendars, setIsLoadingCalendars] = useState(false);
  const [showCreateCalendarModal, setShowCreateCalendarModal] = useState(false);
  const [newCalendarName, setNewCalendarName] = useState('');
  const [isCreatingCalendar, setIsCreatingCalendar] = useState(false);
  const [createCalendarError, setCreateCalendarError] = useState<string | null>(null);
  const [calendarLoadError, setCalendarLoadError] = useState<string | null>(null);

  // Form inputs
  const [sourceUrl, setSourceUrl] = useState('');
  const [chatQuery, setChatQuery] = useState('');
  const [selectedEventForDecompose, setSelectedEventForDecompose] = useState<Event | null>(null);
  const [decomposedTasks, setDecomposedTasks] = useState<DecomposedTask[]>([]);

  const closeDecomposeModal = useCallback(() => setSelectedEventForDecompose(null), []);
  const decomposeModalRef = useFocusTrap<HTMLDivElement>(!!selectedEventForDecompose, closeDecomposeModal);

  const closeCreateCalendarModal = useCallback(() => {
    setShowCreateCalendarModal(false);
    setNewCalendarName('');
    setCreateCalendarError(null);
  }, []);
  const createCalendarModalRef = useFocusTrap<HTMLDivElement>(showCreateCalendarModal, closeCreateCalendarModal);

  const chatEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const {
    isActive: isDigesting,
    status: digestionStatus,
    error: digestionError,
    startStream: startSourceStream
  } = useSourceStream();

  const prevIsDigestingRef = useRef(false);

  const {
    isActive: isStreaming,
    reasoning: streamReasoning,
    toolCalls: streamToolCalls,
    responseText: streamResponseText,
    error: streamError,
    startStream,
    stopStream
  } = useAgentStream();

  const prevIsStreamingRef = useRef(false);

  useEffect(() => {
    if (prevIsStreamingRef.current && !isStreaming) {
      if (streamResponseText) {
        setChatHistory(prev => [...prev, { author: 'AI', content: streamResponseText }]);
      } else if (streamError) {
        setChatHistory(prev => [...prev, { author: 'AI', content: `Error: ${streamError}` }]);
      }
    }
    prevIsStreamingRef.current = isStreaming;
  }, [isStreaming, streamResponseText, streamError]);

  // Keep chat scrolled to bottom when streaming updates arrive
  useEffect(() => {
    if (isStreaming) {
      chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [isStreaming, streamReasoning, streamToolCalls, streamResponseText]);

  const checkSession = async () => {
    setSessionError(null);
    setNeedsLtiLaunch(false);
    try {
      const res = await fetch('/api/settings', { credentials: 'same-origin' });
      if (res.status === 401) {
        setNeedsLtiLaunch(true);
        return;
      }
      if (!res.ok) throw new Error(`Session check failed: ${res.status}`);
      setSessionReady(true);
    } catch (e) {
      console.error('Failed to check session:', e);
      setSessionError('Could not connect. Check your connection and try again.');
    }
  };

  // Check for an existing session (from an earlier LTI launch) before anything else, then fetch
  // initial data.
  useEffect(() => {
    checkSession();
  }, []);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [chatHistory]);

  const fetchSources = async () => {
    try {
      const res = await fetch('/api/sources');
      const data = await res.json();
      if (Array.isArray(data)) setSources(data);
    } catch (e) {
      console.error('Failed to fetch sources:', e);
    }
  };

  const fetchEvents = async () => {
    try {
      const res = await fetch('/api/events');
      const data = await res.json();
      if (Array.isArray(data)) setEvents(data);
    } catch (e) {
      console.error('Failed to fetch events:', e);
    }
  };

  // Fires once when digestion (the SSE stream started right after a 202 upload response)
  // finishes — separate from isUploading, which now only covers the fast persist step (AU-1/AU-2).
  useEffect(() => {
    if (prevIsDigestingRef.current && !isDigesting) {
      fetchSources();
      fetchEvents();
      if (digestionStatus === 'DONE') {
        setToast({ message: 'Source successfully processed!', kind: 'success' });
      } else if (digestionStatus === 'FAILED') {
        setToast({ message: 'Failed to process source.', kind: 'error' });
      } else if (digestionError) {
        setToast({ message: digestionError, kind: 'error' });
      }
    }
    prevIsDigestingRef.current = isDigesting;
  }, [isDigesting, digestionStatus, digestionError]);

  const fetchSettings = async () => {
    try {
      const res = await fetch('/api/settings');
      const data = await res.json();
      setHasApiKey(!!data.hasApiKey);
      if (data.studyPreferences) setPreferences(data.studyPreferences);
    } catch (e) {
      console.error('Failed to fetch settings:', e);
    }
  };

  const fetchCalendars = async () => {
    setIsLoadingCalendars(true);
    setCalendarLoadError(null);
    try {
      const res = await fetch('/api/calendars');
      if (!res.ok) {
        const err = await res.json();
        setCalendarLoadError(err.error || 'Failed to load calendars');
        return;
      }
      const data = await res.json();
      if (Array.isArray(data)) setAvailableCalendars(data);
    } catch {
      setCalendarLoadError('Could not reach calendar service');
    } finally {
      setIsLoadingCalendars(false);
    }
  };

  const fetchGoogleAuthStatus = async () => {
    try {
      const res = await fetch('/api/auth/google/status');
      const data = await res.json();
      const linked = !!data.linked;
      setGoogleLinked(linked);
      if (linked) fetchCalendars();
    } catch (e) {
      console.error('Failed to fetch Google auth status:', e);
    }
  };

  useEffect(() => {
    if (!sessionReady) return;
    fetchSources();
    fetchEvents();
    fetchSettings();
    fetchGoogleAuthStatus();
  }, [sessionReady]);

  const createCalendar = async () => {
    if (!newCalendarName.trim()) return;
    setIsCreatingCalendar(true);
    setCreateCalendarError(null);
    try {
      const res = await fetch('/api/calendars', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: newCalendarName })
      });
      if (!res.ok) {
        const err = await res.json();
        setCreateCalendarError(err.error || 'Failed to create calendar');
        return;
      }
      const data = await res.json();
      setPreferences(prev => ({ ...prev, googleCalendarId: data.id, googleCalendarName: data.name }));
      setShowCreateCalendarModal(false);
      setNewCalendarName('');
      await fetchCalendars();
    } catch {
      setCreateCalendarError('Network error — could not create calendar');
    } finally {
      setIsCreatingCalendar(false);
    }
  };

  const saveSettings = async (newApiKey: string, newPrefs: StudyPreferences) => {
    try {
      // Omit apiKey entirely when the field was left blank, so an empty input never
      // overwrites a key already configured on the server.
      const body: { apiKey?: string; studyPreferences: StudyPreferences } = { studyPreferences: newPrefs };
      if (newApiKey.trim()) body.apiKey = newApiKey;

      await fetch('/api/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
      if (body.apiKey) {
        setHasApiKey(true);
        setApiKey('');
      }
      setPreferences(newPrefs);
      setToast({ message: 'Settings updated successfully!', kind: 'success' });
    } catch (e) {
      console.error('Failed to save settings:', e);
    }
  };

  const triggerCalendarSync = async () => {
    setIsSyncing(true);
    try {
      await fetch('/api/events/sync', { method: 'POST' });
      await fetchEvents();
      setToast({ message: 'Calendar sync completed successfully!', kind: 'success' });
    } catch (e) {
      console.error('Failed to sync calendar:', e);
    } finally {
      setIsSyncing(false);
    }
  };

  const deleteSource = async (id: string) => {
    if (!confirm('Are you sure you want to delete this source? This will remove all associated parsed events.')) return;
    try {
      await fetch(`/api/sources/${id}`, { method: 'DELETE' });
      fetchSources();
      fetchEvents();
    } catch (e) {
      console.error('Failed to delete source:', e);
    }
  };

  const addSourceUrl = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!sourceUrl.trim()) return;
    setIsUploading(true);
    try {
      const formData = new FormData();
      formData.append('url', sourceUrl);
      const res = await fetch('/api/sources', {
        method: 'POST',
        body: formData
      });
      if (res.ok) {
        setSourceUrl('');
        const sourceItem = await res.json();
        setToast({ message: 'URL uploaded — processing in the background.', kind: 'success' });
        fetchSources();
        if (sourceItem?.id) startSourceStream(sourceItem.id);
      } else {
        setToast({ message: 'Failed to process URL source.', kind: 'error' });
      }
    } catch (e) {
      console.error('Failed to add URL source:', e);
    } finally {
      setIsUploading(false);
    }
  };

  const uploadFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setIsUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const res = await fetch('/api/sources', {
        method: 'POST',
        body: formData
      });
      if (res.ok) {
        const sourceItem = await res.json();
        setToast({ message: 'File uploaded — processing in the background.', kind: 'success' });
        fetchSources();
        if (sourceItem?.id) startSourceStream(sourceItem.id);
      } else {
        setToast({ message: 'Failed to process file upload.', kind: 'error' });
      }
    } catch (e) {
      console.error('Failed to upload file:', e);
    } finally {
      setIsUploading(false);
    }
  };

  const sendChatMessage = (e: React.FormEvent) => {
    e.preventDefault();
    if (!chatQuery.trim() || isStreaming) return;
    const userMsg = chatQuery;
    setChatQuery('');
    setChatHistory(prev => [...prev, { author: 'User', content: userMsg }]);
    startStream(userMsg);
  };

  const runTaskDecomposition = async (event: Event) => {
    setSelectedEventForDecompose(event);
    setDecomposedTasks([]);
    setIsDecomposing(true);
    try {
      const res = await fetch('/api/tasks/decompose', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ eventId: event.id, depth: 3 })
      });
      const data = await res.json();
      if (Array.isArray(data)) {
        setDecomposedTasks(data);
      }
    } catch (e) {
      console.error('Failed to decompose task:', e);
    } finally {
      setIsDecomposing(false);
    }
  };

  // Group events by date for a simple chronological list view
  const eventsByDate = events.reduce((acc, event) => {
    if (!acc[event.date]) acc[event.date] = [];
    acc[event.date].push(event);
    return acc;
  }, {} as Record<string, Event[]>);

  const sortedDates = Object.keys(eventsByDate).sort();

  if (!sessionReady) {
    return (
      <div className="app-container" style={{ alignItems: 'center', justifyContent: 'center' }}>
        {needsLtiLaunch ? (
          <div style={{ textAlign: 'center', maxWidth: '28rem' }}>
            <p>Access this tool from your course in your institution's learning management system (Canvas, Blackboard, etc.) — there's no direct sign-in here.</p>
          </div>
        ) : sessionError ? (
          <div style={{ textAlign: 'center' }}>
            <p>{sessionError}</p>
            <button onClick={checkSession}>Retry</button>
          </div>
        ) : (
          <p>Connecting…</p>
        )}
      </div>
    );
  }

  return (
    <div className="app-container">
      {toast && (
        <div
          role="status"
          style={{
            position: 'fixed',
            top: '20px',
            right: '20px',
            zIndex: 1000,
            padding: '12px 20px',
            borderRadius: '10px',
            fontSize: '14px',
            fontWeight: 600,
            maxWidth: '360px',
            background: toast.kind === 'success' ? 'rgba(34,197,94,0.15)' : 'rgba(239,68,68,0.15)',
            border: `1px solid ${toast.kind === 'success' ? 'var(--color-success)' : 'var(--color-danger)'}`,
            color: toast.kind === 'success' ? 'var(--color-success)' : 'var(--color-danger)'
          }}
        >
          {toast.message}
        </div>
      )}
      {/* Sidebar */}
      <aside className="sidebar">
        <div className="logo-container">
          <div className="logo-icon">C</div>
          <span className="logo-text">CEF Planner</span>
        </div>
        <nav className="nav-links" aria-label="Primary">
          <button type="button" className={`nav-item ${activeTab === 'calendar' ? 'active' : ''}`} onClick={() => setActiveTab('calendar')} aria-current={activeTab === 'calendar' ? 'page' : undefined}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line></svg>
            Calendar
          </button>
          <button type="button" className={`nav-item ${activeTab === 'sources' ? 'active' : ''}`} onClick={() => setActiveTab('sources')} aria-current={activeTab === 'sources' ? 'page' : undefined}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path></svg>
            Sources
          </button>
          <button type="button" className={`nav-item ${activeTab === 'chat' ? 'active' : ''}`} onClick={() => setActiveTab('chat')} aria-current={activeTab === 'chat' ? 'page' : undefined}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
            Studio Panel
          </button>
          <button type="button" className={`nav-item ${activeTab === 'settings' ? 'active' : ''}`} onClick={() => setActiveTab('settings')} aria-current={activeTab === 'settings' ? 'page' : undefined}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg>
            Settings
          </button>
        </nav>
      </aside>

      {/* Main Content Area */}
      <main className="main-content">
        {activeTab === 'calendar' && (
          <div>
            <header className="page-header">
              <div className="page-title">
                <h1>Academic Calendar</h1>
                <p>Consolidated view of your assignments, exams, and proactive study plans.</p>
              </div>
              <button onClick={triggerCalendarSync} disabled={isSyncing} className="btn btn-primary">
                {isSyncing ? 'Syncing...' : 'Sync Calendar'}
              </button>
            </header>

            <div className="grid-3" style={{ marginBottom: '24px' }}>
              <div className="card">
                <h2 style={{ fontSize: '18px' }}>Semester Health</h2>
                <p style={{ fontSize: '28px', fontWeight: 'bold', color: 'var(--color-success)', marginTop: '8px' }}>92%</p>
                <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>Proactive completion rate</p>
              </div>
              <div className="card">
                <h2 style={{ fontSize: '18px' }}>Upcoming Tasks</h2>
                <p style={{ fontSize: '28px', fontWeight: 'bold', color: 'var(--color-primary)', marginTop: '8px' }}>
                  {events.filter(e => e.category === 'DEADLINE').length}
                </p>
                <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>Deliverables to finish</p>
              </div>
              <div className="card">
                <h2 style={{ fontSize: '18px' }}>Active Study Blocks</h2>
                <p style={{ fontSize: '28px', fontWeight: 'bold', color: 'var(--color-warning)', marginTop: '8px' }}>
                  {events.filter(e => e.category === 'STUDY_BLOCK').length}
                </p>
                <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>Suggested AI sessions</p>
              </div>
            </div>

            <div className="card">
              <h2>Chronological Agenda</h2>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '20px', marginTop: '20px' }}>
                {sortedDates.length === 0 ? (
                  <p style={{ color: 'var(--text-muted)' }}>No academic events found. Add a syllabus or calendar file under Sources.</p>
                ) : (
                  sortedDates.map(date => (
                    <div key={date} style={{ borderLeft: '3px solid var(--color-primary)', paddingLeft: '16px' }}>
                      <h3 style={{ color: 'var(--color-primary)', marginBottom: '8px', fontSize: '15px' }}>
                        {new Date(date).toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' })}
                      </h3>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                        {eventsByDate[date].map(event => (
                          <div key={event.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(255,255,255,0.02)', padding: '10px 14px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                            <div>
                              <span style={{ fontWeight: 600 }}>{event.title}</span>
                              <div style={{ display: 'flex', gap: '8px', marginTop: '4px', alignItems: 'center' }}>
                                <span className={`event-chip ${event.category.toLowerCase()}`} style={{ display: 'inline-block' }}>
                                  {event.category}
                                </span>
                                {event.startTime && (
                                  <span style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                                    {event.startTime} - {event.endTime}
                                  </span>
                                )}
                                {event.gradeWeight && (
                                  <span style={{ fontSize: '12px', color: 'var(--color-success)' }}>
                                    Weight: {(event.gradeWeight * 100).toFixed(0)}%
                                  </span>
                                )}
                              </div>
                            </div>
                            {(event.category === 'DEADLINE' || event.category === 'FINALS') && (
                              <button onClick={() => runTaskDecomposition(event)} className="btn btn-secondary" style={{ padding: '6px 12px', fontSize: '12px' }}>
                                Break it Down
                              </button>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        )}

        {activeTab === 'sources' && (
          <div>
            <header className="page-header">
              <div className="page-title">
                <h1>Sources Panel</h1>
                <p>Manage raw inputs like syllabi PDFs, ICS feeds, or project rubrics.</p>
              </div>
            </header>

            <div className="grid-2">
              <div className="card">
                <h2>Add New Source</h2>
                <input
                  ref={fileInputRef}
                  type="file"
                  id="sourceFileInput"
                  onChange={uploadFile}
                  style={{ display: 'none' }}
                  accept=".pdf,.docx,.ics,.txt"
                  disabled={isUploading || isDigesting}
                />
                {/* A <label> wrapping a display:none input is never keyboard-focusable on its
                    own — a keyboard-only user had no way to reach file upload at all (found
                    during the AC-4 keyboard-only walkthrough, ADR 0011). A real <button>
                    triggering the hidden input via ref gets keyboard operability natively,
                    unlike retrofitting role="button" onto a <label> (invalid ARIA combination —
                    axe's aria-allowed-role rule correctly flags that). */}
                <button
                  type="button"
                  className="dropzone"
                  style={{ marginBottom: '24px', width: '100%' }}
                  disabled={isUploading || isDigesting}
                  onClick={() => fileInputRef.current?.click()}
                >
                  <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true" style={{ marginBottom: '12px', color: 'var(--color-primary)' }}><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                  <p style={{ fontWeight: 600 }}>
                    {isUploading
                      ? 'Uploading…'
                      : isDigesting
                        ? `Uploaded — ${describeSourceStatus(digestionStatus)}`
                        : 'Click or Drag File Here'}
                  </p>
                  <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Supports PDF, DOCX, and ICS calendar files</p>
                </button>

                <form onSubmit={addSourceUrl}>
                  <div className="form-group">
                    <label htmlFor="sourceUrl">Or Ingest URL / Calendar Feed</label>
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <input
                        type="url"
                        id="sourceUrl"
                        className="form-control"
                        placeholder="https://example.com/syllabus.pdf or webcal://feed.ics"
                        value={sourceUrl}
                        onChange={e => setSourceUrl(e.target.value)}
                        disabled={isUploading || isDigesting}
                      />
                      <button type="submit" disabled={isUploading || isDigesting} className="btn btn-primary">
                        Add
                      </button>
                    </div>
                    {isDigesting && (
                      <p role="status" style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '6px' }}>
                        {describeSourceStatus(digestionStatus)}
                      </p>
                    )}
                  </div>
                </form>
              </div>

              <div className="card">
                <h2>Active Documents & Feeds</h2>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginTop: '16px' }}>
                  {sources.length === 0 ? (
                    <p style={{ color: 'var(--text-muted)' }}>No sources active. Ingest a file to see it here.</p>
                  ) : (
                    sources.map(src => (
                      <div key={src.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(255,255,255,0.02)', padding: '12px 16px', borderRadius: '10px', border: '1px solid var(--border-color)' }}>
                        <div>
                          <p style={{ fontWeight: 600, fontSize: '14px' }}>{src.title}</p>
                          <div style={{ display: 'flex', gap: '8px', marginTop: '4px' }}>
                            <span className={`badge badge-${src.category.toLowerCase()}`}>
                              {src.category}
                            </span>
                            <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                              Type: {src.type}
                            </span>
                            {src.status && src.status !== 'DONE' && (
                              <span style={{ fontSize: '12px', color: src.status === 'FAILED' ? 'var(--color-danger)' : 'var(--text-muted)' }}>
                                {describeSourceStatus(src.status)}
                              </span>
                            )}
                          </div>
                        </div>
                        <button onClick={() => deleteSource(src.title)} aria-label={`Delete ${src.title}`} className="btn" style={{ background: 'transparent', padding: '4px' }}>
                          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--color-danger)" strokeWidth="2" aria-hidden="true"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line></svg>
                        </button>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'chat' && (
          <div>
            <header className="page-header">
              <div className="page-title">
                <h1>Studio Panel</h1>
                <p>Chat with your ContextAgent to extract answers across all your active syllabi and materials.</p>
              </div>
            </header>

            <div className="card">
              <div className="chat-window">
                <div className="chat-history">
                  {chatHistory.map((msg, idx) => (
                    <div key={idx} className={`chat-msg ${msg.author.toLowerCase()}`}>
                      <p style={{ fontWeight: 700, fontSize: '11px', marginBottom: '4px', textTransform: 'uppercase' }}>
                        {msg.author}
                      </p>
                      <p style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</p>
                    </div>
                  ))}

                  {/* Live Streaming Indicator & State */}
                  {isStreaming && (
                    <div className="chat-msg ai streaming-container">
                      <p style={{ fontWeight: 700, fontSize: '11px', marginBottom: '8px', textTransform: 'uppercase', color: 'var(--color-primary)' }}>
                        AI (Agent Streaming...)
                      </p>
                      
                      {/* Live Reasoning */}
                      {streamReasoning && (
                        <div className="stream-reasoning-box">
                          <div className="stream-reasoning-title">
                            <span className="pulse-dot"></span>
                            <span>Reasoning Chain:</span>
                          </div>
                          <p className="stream-reasoning-text">{streamReasoning}</p>
                        </div>
                      )}

                      {/* Live Tool Calls */}
                      {streamToolCalls.length > 0 && (
                        <div className="stream-tools-box">
                          <p className="stream-tools-title">Tools Invoked:</p>
                          <ul className="stream-tools-list">
                            {streamToolCalls.map((call, idx) => (
                              <li key={idx} className={`stream-tool-item ${call.success !== undefined ? (call.success ? 'success' : 'failed') : 'running'}`}>
                                <span className="tool-status-icon">
                                  {call.success !== undefined ? (call.success ? '✓' : '✗') : '⚡'}
                                </span>
                                <span className="tool-name">{call.toolName}</span>
                                {call.arguments && <span className="tool-args">{call.arguments}</span>}
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}

                      {/* Streaming Response */}
                      {streamResponseText && (
                        <div className="stream-response-box">
                          <p style={{ whiteSpace: 'pre-wrap' }}>{streamResponseText}</p>
                        </div>
                      )}

                      {/* Typing indicator until we get response text or reasoning */}
                      {!streamResponseText && !streamReasoning && (
                        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', marginTop: '8px' }}>
                          <div className="loader-dot"></div>
                          <div className="loader-dot" style={{ animationDelay: '0.2s' }}></div>
                          <div className="loader-dot" style={{ animationDelay: '0.4s' }}></div>
                        </div>
                      )}
                    </div>
                  )}

                  <div ref={chatEndRef} />
                </div>

                <form onSubmit={sendChatMessage} className="chat-input-bar">
                  <input
                    type="text"
                    className="form-control"
                    aria-label="Ask a question"
                    placeholder="Ask a question about grading weights, homework policies, or deadlines..."
                    value={chatQuery}
                    onChange={e => setChatQuery(e.target.value)}
                    disabled={isStreaming}
                  />
                  <button type="submit" disabled={isStreaming} className="btn btn-primary">
                    {isStreaming ? 'Streaming...' : 'Ask'}
                  </button>
                  {isStreaming && (
                    <button type="button" onClick={stopStream} className="btn btn-secondary">
                      Stop
                    </button>
                  )}
                </form>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'settings' && (
          <div>
            <header className="page-header">
              <div className="page-title">
                <h1>Settings</h1>
                <p>Configure academic constraints, study break schedules, and AI API keys.</p>
              </div>
            </header>

            <div className="card" style={{ maxWidth: '600px' }}>
              <div className="form-group">
                <label htmlFor="geminiApiKey">Gemini API Key</label>
                <input
                  type="password"
                  id="geminiApiKey"
                  className="form-control"
                  placeholder={hasApiKey ? '•••••••• (key configured — enter a new one to replace it)' : 'AI Studio API Key'}
                  value={apiKey}
                  onChange={e => setApiKey(e.target.value)}
                />
                <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '6px' }}>
                  A private, local API key keeps your syllabus processing inside your personal environment.
                </p>
              </div>

              <div style={{ borderTop: '1px solid var(--border-color)', margin: '24px 0' }} />

              <h2>Study Hour Allocation Constraints</h2>
              <div className="grid-2" style={{ marginTop: '16px' }}>
                <div className="form-group">
                  <label htmlFor="studyStartHour">Study Hours start</label>
                  <input id="studyStartHour" type="number" className="form-control" value={preferences.studyStartHour} onChange={e => setPreferences({...preferences, studyStartHour: parseInt(e.target.value)})} />
                </div>
                <div className="form-group">
                  <label htmlFor="studyEndHour">Study Hours end</label>
                  <input id="studyEndHour" type="number" className="form-control" value={preferences.studyEndHour} onChange={e => setPreferences({...preferences, studyEndHour: parseInt(e.target.value)})} />
                </div>
              </div>

              <div className="grid-2">
                <div className="form-group">
                  <label htmlFor="lunchStartHour">Lunch Start</label>
                  <input id="lunchStartHour" type="number" className="form-control" value={preferences.lunchStartHour} onChange={e => setPreferences({...preferences, lunchStartHour: parseInt(e.target.value)})} />
                </div>
                <div className="form-group">
                  <label htmlFor="lunchEndHour">Lunch End</label>
                  <input id="lunchEndHour" type="number" className="form-control" value={preferences.lunchEndHour} onChange={e => setPreferences({...preferences, lunchEndHour: parseInt(e.target.value)})} />
                </div>
              </div>

              <div className="grid-2">
                <div className="form-group">
                  <label htmlFor="dinnerStartHour">Dinner Start</label>
                  <input id="dinnerStartHour" type="number" className="form-control" value={preferences.dinnerStartHour} onChange={e => setPreferences({...preferences, dinnerStartHour: parseInt(e.target.value)})} />
                </div>
                <div className="form-group">
                  <label htmlFor="dinnerEndHour">Dinner End</label>
                  <input id="dinnerEndHour" type="number" className="form-control" value={preferences.dinnerEndHour} onChange={e => setPreferences({...preferences, dinnerEndHour: parseInt(e.target.value)})} />
                </div>
              </div>

              <div className="grid-2">
                <div className="form-group">
                  <label htmlFor="maxStudyBlockHours">Max Study Block Hours</label>
                  <input id="maxStudyBlockHours" type="number" className="form-control" value={preferences.maxStudyBlockHours} onChange={e => setPreferences({...preferences, maxStudyBlockHours: parseInt(e.target.value)})} />
                </div>
                <div className="form-group">
                  <label htmlFor="preferredBreakMinutes">Preferred Break Minutes</label>
                  <input id="preferredBreakMinutes" type="number" className="form-control" value={preferences.preferredBreakMinutes} onChange={e => setPreferences({...preferences, preferredBreakMinutes: parseInt(e.target.value)})} />
                </div>
              </div>

              <div style={{ borderTop: '1px solid var(--border-color)', margin: '24px 0' }} />

              {/* Google Calendar Section */}
              <h2>Google Calendar Connection</h2>
              <div style={{ marginTop: '12px', padding: '16px', borderRadius: '10px', border: `1px solid ${googleLinked ? 'var(--color-success)' : 'var(--border-color)'}`, background: googleLinked ? 'rgba(34,197,94,0.06)' : 'rgba(255,255,255,0.02)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '10px' }}>
                  <span style={{ fontSize: '18px' }}>{googleLinked ? '✅' : '🔒'}</span>
                  <div>
                    <p style={{ fontWeight: 600, fontSize: '14px' }}>{googleLinked ? 'Google Account Connected' : 'Google Account Not Linked'}</p>
                    <p style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                      {googleLinked ? 'Calendars are available for sync.' : 'Connect your Google account to sync events to your calendar.'}
                    </p>
                  </div>
                  {googleLinked ? (
                    <button onClick={fetchCalendars} disabled={isLoadingCalendars} className="btn btn-secondary" style={{ marginLeft: 'auto', padding: '4px 10px', fontSize: '12px' }}>
                      {isLoadingCalendars ? 'Refreshing...' : 'Refresh'}
                    </button>
                  ) : (
                    <button
                      onClick={() => { window.location.href = '/api/auth/google/start'; }}
                      className="btn btn-primary"
                      style={{ marginLeft: 'auto', padding: '6px 12px', fontSize: '12px' }}
                    >
                      Connect Google Calendar
                    </button>
                  )}
                </div>

                {googleLinked && (
                  <div>
                    <label htmlFor="targetCalendar" style={{ fontSize: '13px', fontWeight: 600, color: 'var(--color-primary)' }}>Target Google Calendar</label>
                    {isLoadingCalendars ? (
                      <p style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '8px' }}>Loading available calendars…</p>
                    ) : calendarLoadError ? (
                      <p style={{ fontSize: '12px', color: 'var(--color-danger)', marginTop: '8px' }}>{calendarLoadError}</p>
                    ) : (
                      <select
                        id="targetCalendar"
                        value={preferences.googleCalendarId === 'default' ? 'default' : preferences.googleCalendarId}
                        onChange={e => {
                          const id = e.target.value;
                          if (id === 'default') {
                            setPreferences(p => ({ ...p, googleCalendarId: 'default', googleCalendarName: 'CEF Academic' }));
                          } else {
                            const cal = availableCalendars.find(c => c.id === id);
                            setPreferences(p => ({ ...p, googleCalendarId: id, googleCalendarName: cal?.name ?? p.googleCalendarName }));
                          }
                        }}
                        style={{ marginTop: '8px', width: '100%', padding: '10px 14px', borderRadius: '8px', border: '1px solid var(--border-color)', background: 'var(--bg-input, rgba(255,255,255,0.05))', fontSize: '14px', color: 'inherit' }}
                      >
                        <option value="default">CEF Academic (Default)</option>
                        {availableCalendars.map(cal => (
                          <option key={cal.id} value={cal.id}>{cal.name}</option>
                        ))}
                      </select>
                    )}
                    <button
                      onClick={() => { setShowCreateCalendarModal(true); setCreateCalendarError(null); }}
                      className="btn btn-secondary"
                      style={{ marginTop: '10px', fontSize: '12px', padding: '5px 12px' }}
                    >+ Create New Calendar</button>
                  </div>
                )}
              </div>

              <div className="form-group" style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '12px' }}>
                <input 
                  type="checkbox" 
                  id="shareReports"
                  checked={preferences.shareAnonymousBugReports} 
                  onChange={e => setPreferences({...preferences, shareAnonymousBugReports: e.target.checked})} 
                />
                <label htmlFor="shareReports" style={{ cursor: 'pointer' }}>Share Anonymous Bug Reports</label>
              </div>

              <button onClick={() => saveSettings(apiKey, preferences)} className="btn btn-primary" style={{ marginTop: '16px' }}>
                Save Configurations
              </button>
            </div>

            <div className="card" style={{ maxWidth: '600px', marginTop: '24px' }}>
              <h2>Accessibility</h2>
              <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '8px' }}>
                This app targets <strong>WCAG 2.1 Level AA</strong> conformance for the web client.
                That target is backed by automated testing (axe-core, both in-memory and in a real
                browser), a manual color-contrast audit, a full keyboard-only walkthrough, and
                automated real screen-reader testing with VoiceOver and NVDA — not just a claim.
              </p>
              <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '12px' }}>
                Known limitations, reported honestly rather than left out:
              </p>
              <ul style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '8px', paddingLeft: '20px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <li>
                  A full human listen-through of VoiceOver and NVDA hasn't been done yet.
                  Automated testing confirms the correct text and roles are announced, but hasn't
                  been judged by ear for how natural a full sequence of announcements sounds.
                </li>
                <li>
                  The accent color used as text/icon color can fall slightly below the contrast
                  threshold against certain hover/input backgrounds. It isn't used that way
                  anywhere today, but is flagged here as a constraint on future design changes.
                </li>
                <li>
                  This statement covers the web client only. The native Android, iOS, and Desktop
                  apps aren't covered here; on Desktop, NVDA requires Windows' Java Access Bridge to
                  be enabled by an administrator, and Linux currently has no accessibility bridge at
                  all in the underlying UI framework (an upstream limitation, not specific to this
                  app).
                </li>
              </ul>
              <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '12px' }}>
                Found an accessibility issue? Report it to{' '}
                <a href="mailto:privacy@borinquenterrier.com" style={{ color: 'var(--color-primary)' }}>
                  privacy@borinquenterrier.com
                </a>.
              </p>
            </div>
          </div>
        )}
      </main>

      {/* Decomposition Modal / Dialog */}
      {selectedEventForDecompose && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div
            ref={decomposeModalRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby="decomposeModalTitle"
            className="card"
            style={{ width: '90%', maxWidth: '600px', maxHeight: '80vh', overflowY: 'auto', background: '#131520' }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <h2 id="decomposeModalTitle">Break Down: {selectedEventForDecompose.title}</h2>
              <button onClick={closeDecomposeModal} aria-label="Close" style={{ background: 'transparent', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer', fontSize: '18px' }}>✕</button>
            </div>

            {isDecomposing ? (
              <div style={{ textAlign: 'center', padding: '40px 0' }}>
                <div style={{ width: '40px', height: '40px', border: '4px solid var(--color-primary-glow)', borderTopColor: 'var(--color-primary)', borderRadius: '50%', animation: 'spin 1s linear infinite', margin: '0 auto 16px' }} />
                <p>AI Agent is decomposing task dynamically into micro-deliverables...</p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {decomposedTasks.length === 0 ? (
                  <p>Decomposition resulted in no subtasks. Make sure your API key is configured.</p>
                ) : (
                  decomposedTasks.map((task, idx) => (
                    <div key={idx} style={{ background: 'rgba(255,255,255,0.02)', padding: '12px 16px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                      <p style={{ fontWeight: 600 }}>{task.title}</p>
                      <p style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '4px' }}>{task.description}</p>
                      <p style={{ fontSize: '11px', color: 'var(--color-warning)', marginTop: '6px' }}>Due: {task.daysBeforeDue} days before main event</p>
                    </div>
                  ))
                )}
              </div>
            )}
            
            <div style={{ marginTop: '24px', textAlign: 'right' }}>
              <button onClick={closeDecomposeModal} className="btn btn-secondary">
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create New Calendar Modal */}
      {showCreateCalendarModal && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.65)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1001 }}>
          <div
            ref={createCalendarModalRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby="createCalendarModalTitle"
            className="card"
            style={{ width: '90%', maxWidth: '460px', background: '#131520' }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <h2 id="createCalendarModalTitle">Create New Google Calendar</h2>
              <button onClick={closeCreateCalendarModal} aria-label="Close" style={{ background: 'transparent', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer', fontSize: '18px' }}>✕</button>
            </div>
            <div className="form-group">
              <label htmlFor="newCalendarName">Calendar Name</label>
              <input
                type="text"
                id="newCalendarName"
                className="form-control"
                placeholder="e.g., Study Calendar"
                value={newCalendarName}
                onChange={e => setNewCalendarName(e.target.value)}
                disabled={isCreatingCalendar}
              />
            </div>
            {createCalendarError && (
              <p style={{ fontSize: '12px', color: 'var(--color-danger)', marginBottom: '12px' }}>{createCalendarError}</p>
            )}
            <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end', marginTop: '16px' }}>
              <button onClick={closeCreateCalendarModal} className="btn btn-secondary" disabled={isCreatingCalendar}>
                Cancel
              </button>
              <button onClick={createCalendar} className="btn btn-primary" disabled={!newCalendarName.trim() || isCreatingCalendar}>
                {isCreatingCalendar ? 'Creating…' : 'Create'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>

  );
}
