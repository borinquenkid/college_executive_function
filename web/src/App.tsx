import React, { useState, useEffect, useRef } from 'react';
import { useAgentStream } from './useAgentStream';

// API Interface Mappings matching KMP/Ktor Server backend
interface WebSource {
  id: string;
  title: string;
  originUri: string | null;
  type: string;
  category: string;
  metadata: string | null;
  updatedAt: number;
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

interface StudyPreferences {
  studyStartHour: number;
  studyEndHour: number;
  lunchStartHour: number;
  lunchEndHour: number;
  dinnerStartHour: number;
  dinnerEndHour: number;
  maxStudyBlockHours: number;
  preferredBreakMinutes: number;
  shareAnonymousBugReports: boolean;
}

export default function App() {
  const [activeTab, setActiveTab] = useState<'calendar' | 'sources' | 'chat' | 'settings'>('calendar');
  const [sources, setSources] = useState<WebSource[]>([]);
  const [events, setEvents] = useState<Event[]>([]);
  const [chatHistory, setChatHistory] = useState<WebChatMessage[]>([
    { author: 'AI', content: 'Hello! Ask me anything about your syllabi, textbooks, or schedule context.' }
  ]);
  
  // Settings State
  const [apiKey, setApiKey] = useState('');
  const [preferences, setPreferences] = useState<StudyPreferences>({
    studyStartHour: 9,
    studyEndHour: 21,
    lunchStartHour: 12,
    lunchEndHour: 13,
    dinnerStartHour: 17,
    dinnerEndHour: 19,
    maxStudyBlockHours: 2,
    preferredBreakMinutes: 30,
    shareAnonymousBugReports: false
  });

  // Action/Loading States
  const [isSyncing, setIsSyncing] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [isDecomposing, setIsDecomposing] = useState(false);

  // Form inputs
  const [sourceUrl, setSourceUrl] = useState('');
  const [chatQuery, setChatQuery] = useState('');
  const [selectedEventForDecompose, setSelectedEventForDecompose] = useState<Event | null>(null);
  const [decomposedTasks, setDecomposedTasks] = useState<DecomposedTask[]>([]);
  
  const fileInputRef = useRef<HTMLInputElement>(null);
  const chatEndRef = useRef<HTMLDivElement>(null);

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

  // Fetch initial data
  useEffect(() => {
    fetchSources();
    fetchEvents();
    fetchSettings();
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

  const fetchSettings = async () => {
    try {
      const res = await fetch('/api/settings');
      const data = await res.json();
      if (data.apiKey !== undefined) setApiKey(data.apiKey);
      if (data.studyPreferences) setPreferences(data.studyPreferences);
    } catch (e) {
      console.error('Failed to fetch settings:', e);
    }
  };

  const saveSettings = async (newApiKey: string) => {
    try {
      await fetch('/api/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ apiKey: newApiKey })
      });
      setApiKey(newApiKey);
      alert('Settings updated successfully!');
    } catch (e) {
      console.error('Failed to save settings:', e);
    }
  };

  const triggerCalendarSync = async () => {
    setIsSyncing(true);
    try {
      await fetch('/api/events/sync', { method: 'POST' });
      await fetchEvents();
      alert('Calendar sync completed successfully!');
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
        fetchSources();
        fetchEvents();
        alert('URL successfully ingested and processed!');
      } else {
        alert('Failed to process URL source.');
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
        fetchSources();
        fetchEvents();
        alert('File successfully uploaded and processed!');
      } else {
        alert('Failed to process file upload.');
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

  return (
    <div className="app-container">
      {/* Sidebar */}
      <aside className="sidebar">
        <div className="logo-container">
          <div className="logo-icon">C</div>
          <span className="logo-text">CEF Planner</span>
        </div>
        <nav className="nav-links">
          <div className={`nav-item ${activeTab === 'calendar' ? 'active' : ''}`} onClick={() => setActiveTab('calendar')}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line></svg>
            Calendar
          </div>
          <div className={`nav-item ${activeTab === 'sources' ? 'active' : ''}`} onClick={() => setActiveTab('sources')}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path></svg>
            Sources
          </div>
          <div className={`nav-item ${activeTab === 'chat' ? 'active' : ''}`} onClick={() => setActiveTab('chat')}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
            Studio Panel
          </div>
          <div className={`nav-item ${activeTab === 'settings' ? 'active' : ''}`} onClick={() => setActiveTab('settings')}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg>
            Settings
          </div>
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
                <h3>Semester Health</h3>
                <p style={{ fontSize: '28px', fontWeight: 'bold', color: 'var(--color-success)', marginTop: '8px' }}>92%</p>
                <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>Proactive completion rate</p>
              </div>
              <div className="card">
                <h3>Upcoming Tasks</h3>
                <p style={{ fontSize: '28px', fontWeight: 'bold', color: 'var(--color-primary)', marginTop: '8px' }}>
                  {events.filter(e => e.category === 'DEADLINE').length}
                </p>
                <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>Deliverables to finish</p>
              </div>
              <div className="card">
                <h3>Active Study Blocks</h3>
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
                      <h4 style={{ color: 'var(--color-primary)', marginBottom: '8px' }}>
                        {new Date(date).toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' })}
                      </h4>
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
                <div 
                  className="dropzone" 
                  onClick={() => fileInputRef.current?.click()}
                  style={{ marginBottom: '24px' }}
                >
                  <input 
                    type="file" 
                    ref={fileInputRef} 
                    onChange={uploadFile} 
                    style={{ display: 'none' }}
                    accept=".pdf,.docx,.ics,.txt"
                  />
                  <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ marginBottom: '12px', color: 'var(--color-primary)' }}><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                  <p style={{ fontWeight: 600 }}>{isUploading ? 'Uploading and parsing...' : 'Click or Drag File Here'}</p>
                  <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Supports PDF, DOCX, and ICS calendar files</p>
                </div>

                <form onSubmit={addSourceUrl}>
                  <div className="form-group">
                    <label>Or Ingest URL / Calendar Feed</label>
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <input 
                        type="url" 
                        className="form-control" 
                        placeholder="https://example.com/syllabus.pdf or webcal://feed.ics"
                        value={sourceUrl}
                        onChange={e => setSourceUrl(e.target.value)}
                        disabled={isUploading}
                      />
                      <button type="submit" disabled={isUploading} className="btn btn-primary">
                        Add
                      </button>
                    </div>
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
                          </div>
                        </div>
                        <button onClick={() => deleteSource(src.title)} className="btn" style={{ background: 'transparent', padding: '4px' }}>
                          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--color-danger)" strokeWidth="2"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line></svg>
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
                <label>Gemini API Key</label>
                <input 
                  type="password" 
                  className="form-control" 
                  placeholder="AI Studio API Key"
                  value={apiKey}
                  onChange={e => setApiKey(e.target.value)}
                />
                <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '6px' }}>
                  A private, local API key keeps your syllabus processing inside your personal environment.
                </p>
              </div>

              <div style={{ borderTop: '1px solid var(--border-color)', margin: '24px 0' }} />

              <h3>Study Hour Allocation Constraints</h3>
              <div className="grid-2" style={{ marginTop: '16px' }}>
                <div className="form-group">
                  <label>Study Hours start</label>
                  <input type="number" className="form-control" value={preferences.studyStartHour} onChange={e => setPreferences({...preferences, studyStartHour: parseInt(e.target.value)})} />
                </div>
                <div className="form-group">
                  <label>Study Hours end</label>
                  <input type="number" className="form-control" value={preferences.studyEndHour} onChange={e => setPreferences({...preferences, studyEndHour: parseInt(e.target.value)})} />
                </div>
              </div>

              <div className="grid-2">
                <div className="form-group">
                  <label>Lunch Start</label>
                  <input type="number" className="form-control" value={preferences.lunchStartHour} onChange={e => setPreferences({...preferences, lunchStartHour: parseInt(e.target.value)})} />
                </div>
                <div className="form-group">
                  <label>Lunch End</label>
                  <input type="number" className="form-control" value={preferences.lunchEndHour} onChange={e => setPreferences({...preferences, lunchEndHour: parseInt(e.target.value)})} />
                </div>
              </div>

              <div className="grid-2">
                <div className="form-group">
                  <label>Dinner Start</label>
                  <input type="number" className="form-control" value={preferences.dinnerStartHour} onChange={e => setPreferences({...preferences, dinnerStartHour: parseInt(e.target.value)})} />
                </div>
                <div className="form-group">
                  <label>Dinner End</label>
                  <input type="number" className="form-control" value={preferences.dinnerEndHour} onChange={e => setPreferences({...preferences, dinnerEndHour: parseInt(e.target.value)})} />
                </div>
              </div>

              <button onClick={() => saveSettings(apiKey)} className="btn btn-primary" style={{ marginTop: '12px' }}>
                Save Configurations
              </button>
            </div>
          </div>
        )}
      </main>

      {/* Decomposition Modal / Dialog */}
      {selectedEventForDecompose && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div className="card" style={{ width: '90%', maxWidth: '600px', maxHeight: '80vh', overflowY: 'auto', background: '#131520' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <h2>Break Down: {selectedEventForDecompose.title}</h2>
              <button onClick={() => setSelectedEventForDecompose(null)} style={{ background: 'transparent', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer', fontSize: '18px' }}>✕</button>
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
              <button onClick={() => setSelectedEventForDecompose(null)} className="btn btn-secondary">
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
