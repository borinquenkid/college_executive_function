// AUTO-GENERATED — do not edit manually.
// Source: Kotlin @Serializable classes in :composeApp and :server modules.
// Regenerate: ./gradlew :server:generateTypescript

export interface StudyPreferences {
  studyStartHour: number;
  studyEndHour: number;
  lunchStartHour: number;
  lunchEndHour: number;
  dinnerStartHour: number;
  dinnerEndHour: number;
  maxStudyBlockHours: number;
  preferredBreakMinutes: number;
  shareAnonymousBugReports: boolean;
  googleCalendarId: string;
  googleCalendarName: string;
  semesterStart: string | null;
  semesterEnd: string | null;
}

export interface RemoteCalendarMetadata {
  id: string;
  name: string;
}

export interface WebSettings {
  apiKey: string | null;
  hasApiKey: boolean;
  studyPreferences: StudyPreferences | null;
}

export interface StudentSummary {
  studentId: string;
  createdAtMillis: number;
  lastActiveMillis: number | null;
}

