import { useEffect, useState } from 'react';
import type { StudentSummary } from '../cef-types';

// Staff console — reached only via an LTI launch with an Instructor/Administrator role (see
// docs/adr/0007-staff-console-via-lti-roles.md). Deliberately its own small bundle (no router is
// installed in this project) rather than a route inside App.tsx: keeps staff-only code out of the
// student bundle and vice versa, and gives a cleaner security boundary between the two.
export default function StaffApp() {
  const [students, setStudents] = useState<StudentSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [resettingId, setResettingId] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  const fetchStudents = async () => {
    setError(null);
    try {
      const res = await fetch('/api/staff/students', { credentials: 'same-origin' });
      if (res.status === 403 || res.status === 401) {
        setError('Staff access required — launch this tool as an instructor or administrator from your course.');
        return;
      }
      if (!res.ok) throw new Error(`Failed to load students: ${res.status}`);
      const data: StudentSummary[] = await res.json();
      setStudents(data);
    } catch (e) {
      console.error('Failed to fetch students:', e);
      setError('Could not connect. Check your connection and try again.');
    }
  };

  useEffect(() => {
    fetchStudents();
  }, []);

  const resetSession = async (studentId: string) => {
    setResettingId(studentId);
    setStatusMessage(null);
    try {
      const res = await fetch(`/api/staff/students/${encodeURIComponent(studentId)}/reset-session`, {
        method: 'POST',
        credentials: 'same-origin',
      });
      if (!res.ok) throw new Error(`Reset failed: ${res.status}`);
      setStatusMessage(`Session reset. That student will need to relaunch from your course to get back in.`);
    } catch (e) {
      console.error('Failed to reset session:', e);
      setStatusMessage('Could not reset that session. Try again.');
    } finally {
      setResettingId(null);
    }
  };

  const formatDate = (millis: number | null) =>
    millis == null ? '—' : new Date(millis).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });

  return (
    <div className="app-container" style={{ padding: '2rem', maxWidth: '48rem', margin: '0 auto' }}>
      <h1>Staff Console</h1>
      <p style={{ color: 'var(--color-text-secondary, #666)' }}>
        Account-level status only — no calendar, source, or chat content is visible here.
      </p>

      {error && <p style={{ color: 'var(--color-error, #c00)' }}>{error}</p>}
      {statusMessage && <p>{statusMessage}</p>}

      {students && (
        <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: '1rem' }}>
          <thead>
            <tr>
              <th style={{ textAlign: 'left', borderBottom: '1px solid currentColor', padding: '0.5rem' }}>Student</th>
              <th style={{ textAlign: 'left', borderBottom: '1px solid currentColor', padding: '0.5rem' }}>First seen</th>
              <th style={{ textAlign: 'left', borderBottom: '1px solid currentColor', padding: '0.5rem' }}>Last active</th>
              <th style={{ textAlign: 'left', borderBottom: '1px solid currentColor', padding: '0.5rem' }}></th>
            </tr>
          </thead>
          <tbody>
            {students.length === 0 && (
              <tr>
                <td colSpan={4} style={{ padding: '0.5rem' }}>No students have launched this tool yet.</td>
              </tr>
            )}
            {students.map(student => (
              <tr key={student.studentId}>
                <td style={{ padding: '0.5rem', fontFamily: 'monospace', fontSize: '0.85em' }}>{student.studentId}</td>
                <td style={{ padding: '0.5rem' }}>{formatDate(student.createdAtMillis)}</td>
                <td style={{ padding: '0.5rem' }}>{formatDate(student.lastActiveMillis)}</td>
                <td style={{ padding: '0.5rem' }}>
                  <button
                    onClick={() => resetSession(student.studentId)}
                    disabled={resettingId === student.studentId}
                  >
                    {resettingId === student.studentId ? 'Resetting…' : 'Reset session'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
