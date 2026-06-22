package com.borinquenterrier.cef

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Builder for event extraction and critique prompts.
 * Handles source fragment parsing and event quality assurance.
 */
object EventBuilder {

    private val currentYear = Clock.System.todayIn(TimeZone.currentSystemDefault()).year

    private val SOURCE_FRAGMENT_INSTRUCTIONS = """
        # Source Fragment Interpretation Instructions
        You are receiving a JSON object representing a 'SourceFragment'. 
        
        ## Fields:
        - `text`: The raw content to analyze.
        - `pageNumber`: (Optional) The page of the document this text came from.
        - `sectionTitle`: (Optional) The title of the document section.
        - `type`: Either `TEXT` (general material) or `CALENDAR` (RFC 5545 format).
        - `metadata`: A map of additional context. Key `weekAnchors` (when present) contains
          the full week-to-date anchor table extracted from the rest of the document, e.g.:
            Week 1: June 8–14
            Week 4: June 29–July 5, 2026
          Use this table to resolve "Week N" references in the text to calendar dates.

        ## Instructions:
        1. If `type` is `CALENDAR`, the `text` is an iCalendar VEVENT. Map it directly to the target schema.
        2. If `type` is `TEXT`, perform extraction from the `text` field using any provided `pageNumber` or `sectionTitle` as additional context for locating the event in time or importance.
        3. If `metadata.weekAnchors` is present, use it as the authoritative week-to-date mapping when the text contains "Week N" references without explicit calendar dates.
    """.trimIndent()

    fun getSourceEventExtractionPrompt(fragmentJson: String): String {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val semesterLabel = when {
            today.monthNumber <= 5 -> "Spring ${today.year}"
            today.monthNumber <= 8 -> "Summer ${today.year}"
            else -> "Fall ${today.year}"
        }
        return """
            $SOURCE_FRAGMENT_INSTRUCTIONS

            # Objective
            Analyze the provided SourceFragment and extract EVERY academic event, deadline, or deliverable a student would need to track.

            ## What to extract
            Extract ALL of the following when a date can be determined:
            - Exams: midterms, finals, quizzes, tests
            - Assignment due dates: papers, essays, problem sets, lab reports, projects, presentations
            - Reading deadlines and participation requirements with a due date
            - Class sessions: every scheduled in-person or synchronous meeting listed in the weekly schedule under a specific day (e.g. "Monday: Discuss controlling idea", "Wednesday: WC visit & brainstorming", "Wednesday: Assign Issue Brief #3") → category CLASS. A class session that launches an assignment ("Assign X") or screens content still takes place in person on that day — it is always a CLASS event, NOT a DEADLINE. A note like "(online)" on a video title means the video is online homework, not that the class session is online or should be omitted.
            - Semester boundaries: first day, last day, add/drop deadline, withdrawal deadline
            - Holidays and breaks that affect this course

            ## Date rules
            - Use EXPLICIT dates when the document states them directly (e.g. "October 14" or "July 3, 2026").
            - Day-within-week pattern (preferred): If a weekly schedule provides per-week date ranges (e.g. "Week 4: June 29–July 5") and an event is listed under a named class day within that week (e.g. under "Wednesday"), compute the exact calendar date. Monday of the range = Day 1, Tuesday = +1, Wednesday = +2, Thursday = +3, Friday = +4. Example: "Issue Brief #1 due" under "Wednesday" in "Week 4: June 29–July 5" → July 1, 2026. Getting this right matters — a student who shows up Monday with work due Wednesday submits late.
            - Week-anchor fallback (only when day is unknown): If the document provides a Week 1 date range and an assignment ONLY appears in a summary table with "Due Week N" and does NOT appear anywhere in the weekly session body, use the Wednesday of that week as the due date (e.g. "Due Week 4" with "Week 4: June 29–July 5" → July 1). Wednesday is the default because most academic deadlines fall mid-week.
            - Deduplication: When the same assignment appears in BOTH a summary/grade table (with only a week number) AND the detailed weekly session table (with an explicit day), extract it ONLY from the session table entry. Do NOT produce a separate event for the summary table row — that would create a duplicate with a wrong date.
            - If a time is given (e.g. "due at 11:59 pm"), produce a TIME event; otherwise produce a DAY event.
            - Do NOT invent events not mentioned in the document. Do NOT use your training data to add typical course events. Only extract what is stated.

            ## Category rules (use exactly one per event)
            - DEADLINE  → any graded submission due date: papers, essays, projects, lab reports, presentations, quizzes, drafts, discussion posts, homework assignments. If a student must submit or turn something in for a grade, it is DEADLINE.
            - FINALS    → final exams or capstone assessments that close the semester
            - CLASS     → in-person or synchronous class meeting on a specific weekday
            - REGULAR   → one-time academic task that is NOT graded and does NOT have a due date (e.g. "attend Writing Center", "peer review workshop")
            - HOLIDAY   → official break or day when classes do not meet
            - SEMESTER_BOUND → first/last day of term, add/drop deadline, withdrawal deadline
            - STUDY_BLOCK → do NOT use during extraction; this is reserved for AI-generated study suggestions

            # Output Format
            Return ONLY a raw JSON array of objects. No filler.
            Structure:
            [
              {
                "title": "Title",
                "type": "TIME" or "DAY",
                "category": "CLASS", "REGULAR", "HOLIDAY", "DEADLINE", "FINALS", or "SEMESTER_BOUND",
                "date": "YYYY-MM-DD",
                "startTime": "HH:mm" (optional),
                "endTime": "HH:mm" (optional),
                "gradeWeight": 0.15 (Optional. Float value representing the grade percentage, e.g., 0.15 for 15%. Search for grade weight in text nearby, like 'Midterm Exam - 15%'),
                "warning": "String" (Optional. Use this if the source text is contradictory, e.g., 'Document says Monday Jan 1st, but Jan 1st is a Thursday'. Or if a date was calculated from a week number rather than stated explicitly.)
              }
            ]

            # Context
            Current Year: $currentYear
            Today's Date: $today
            Active Semester: $semesterLabel
            Extract ALL events found in the document regardless of which semester they belong to. The student may be planning ahead for future semesters or reviewing past ones.

            # Data to Process (SourceFragment JSON)
            $fragmentJson
        """.trimIndent()
    }

    fun getEventCritiquePrompt(sourceText: String, eventsJson: String): String {
        return """
            You are a strict data auditor and quality control agent.
            
            Below is a raw source document followed by a list of events/deadlines that were extracted from it.
            
            # Source Document:
            $sourceText
            
            # Extracted Events (JSON):
            $eventsJson
            
            # Task:
            Critique the extracted events. Check each event against the source document.
            Identify any:
            1. Hallucinated/invented events: events not mentioned anywhere in the source document.
            2. Incorrect dates, times, titles, or categories.

            NOTE: Events with dates calculated from week numbers (e.g. "Week 4" computed from a "Week 1: June 8–14" anchor) are VALID. Do not flag or remove them — their warning field will already note the calculation. Only remove events whose title or existence is not supported by the source at all.

            NOTE: CLASS events (in-person/synchronous class meetings) are VALID even if the class session launches an assignment ("Assign Issue Brief #3") or involves watching an online video — the in-class meeting still occurs on that weekday. A note like "(online)" refers to the video being hosted online, not to the class session being remote. Never remove or recategorize a CLASS event solely because its topic involves assigning homework or viewing content.
            
            CATEGORY RULES (apply these when correcting):
            - DEADLINE  → any graded submission (papers, essays, projects, quizzes, drafts, discussion posts, homework)
            - FINALS    → final exams / capstone assessments
            - CLASS     → in-person or synchronous class meeting
            - REGULAR   → non-graded one-time academic task
            - HOLIDAY   → official break / no-class day
            - SEMESTER_BOUND → term start/end, add/drop, withdrawal deadlines
            Never use STUDY_BLOCK here — that is reserved for the study plan phase.

            Return a refined JSON array of objects following the EXACT same schema as the input:
            [
              {
                "title": "Title",
                "type": "TIME" or "DAY",
                "category": "CLASS", "REGULAR", "HOLIDAY", "DEADLINE", "FINALS", or "SEMESTER_BOUND",
                "date": "YYYY-MM-DD",
                "startTime": "HH:mm" (optional),
                "endTime": "HH:mm" (optional),
                "warning": "Optional string describing what was corrected or why it is flagged"
              }
            ]
            
            Ensure you ONLY output the corrected JSON array. If all events are valid, return the original JSON array unchanged. Do not include any explanation or markdown formatting.
        """.trimIndent()
    }
}
