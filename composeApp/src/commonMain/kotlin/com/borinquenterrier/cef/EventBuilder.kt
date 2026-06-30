package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
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
        1. If `metadata.contextOnly = 'true'`, this fragment is a carry-over from the previous
           batch included for reading continuity ONLY. Do NOT extract any events from it.
        2. If `type` is `CALENDAR`, the `text` is an iCalendar VEVENT. Map it directly to the target schema.
        3. If `type` is `TEXT`, perform extraction from the `text` field using any provided `pageNumber` or `sectionTitle` as additional context for locating the event in time or importance.
        4. If `metadata.weekAnchors` is present, use it as the authoritative week-to-date mapping when the text contains "Week N" references without explicit calendar dates.
    """.trimIndent()

    fun getSourceEventExtractionPrompt(fragmentJson: String): String {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val semesterLabel = when {
            today.month.number <= 5 -> "Spring ${today.year}"
            today.month.number <= 8 -> "Summer ${today.year}"
            else -> "Fall ${today.year}"
        }
        return """
            # MEMORANDUM BRIEF: EVENT EXTRACTION

            ## 1. TOPIC CLARIFICATION
            This brief instructs you to extract academic events, deadlines, and deliverables from a source document fragment.

            ## 2. STRUCTURED REFERENCE MATERIAL
            $SOURCE_FRAGMENT_INSTRUCTIONS

            <context_metadata>
            Current Year: $currentYear
            Today's Date: $today
            Active Semester: $semesterLabel
            </context_metadata>

            <source_fragment>
            $fragmentJson
            </source_fragment>

            ## 3. TASK PROMPT
            Analyze the content inside <source_fragment> and extract EVERY academic event, deadline, or deliverable that a student would need to track.
            
            Extract all of the following when a date can be determined:
            - Exams: midterms, finals, quizzes, tests
            - Assignment due dates: papers, essays, problem sets, lab reports, projects, presentations
            - Reading deadlines and participation requirements with a due date
            - Class sessions: every scheduled in-person or synchronous meeting listed in the weekly schedule under a specific day (e.g. "Monday: Discuss controlling idea", "Wednesday: WC visit & brainstorming", "Wednesday: Assign Issue Brief #3") → category CLASS. A class session that launches an assignment ("Assign X") or screens content still takes place in person on that day — it is always a CLASS event, NOT a DEADLINE. A note like "(online)" on a video title means the video is online homework, not that the class session is online or should be omitted.
            - Semester boundaries: first day, last day, add/drop deadline, withdrawal deadline
            - Holidays and breaks that affect this course

            Date resolution rules:
            - Use EXPLICIT dates when the document states them directly (e.g. "October 14" or "July 3, 2026").
            - Day-within-week pattern (preferred): If a weekly schedule provides per-week date ranges (e.g. "Week 4: June 29–July 5") and an event is listed under a named class day within that week (e.g. under "Wednesday"), compute the exact calendar date. Monday of the range = Day 1, Tuesday = +1, Wednesday = +2, Thursday = +3, Friday = +4. Example: "Issue Brief #1 due" under "Wednesday" in "Week 4: June 29–July 5" → July 1, 2026. Getting this right matters — a student who shows up Monday with work due Wednesday submits late.
            - Week-anchor fallback (only when day is unknown): If the document provides a Week 1 date range and an assignment ONLY appears in a summary table with "Due Week N" and does NOT appear anywhere in the weekly session body, use the Wednesday of that week as the due date (e.g. "Due Week 4" with "Week 4: June 29–July 5" → July 1). Wednesday is the default because most academic deadlines fall mid-week.
            - Deduplication: When the same assignment appears in BOTH a summary/grade table (with only a week number) AND the detailed weekly session table (with an explicit day), extract it ONLY from the session table entry. Do NOT produce a separate event for the summary table row — that would create a duplicate with a wrong date.
            - If a time is given (e.g. "due at 11:59 pm"), produce a TIME event; otherwise produce a DAY event.

            Category rules (use exactly one per event):
            - DEADLINE  → any graded submission (papers, essays, projects, lab reports, presentations, quizzes, drafts, discussion posts, homework)
            - FINALS    → final exams or capstone assessments closing the semester
            - CLASS     → in-person or synchronous class meeting on a specific weekday
            - REGULAR   → one-time academic task that is NOT graded and does NOT have a due date
            - HOLIDAY   → official break or day when classes do not meet
            - SEMESTER_BOUND → first/last day of term, add/drop, withdrawal deadlines
            - STUDY_BLOCK → do NOT use during extraction; this is reserved for AI-generated study suggestions

            Output Schema structure:
            [
              {
                "title": "Title",
                "type": "TIME" or "DAY",
                "category": "CLASS", "REGULAR", "HOLIDAY", "DEADLINE", "FINALS", or "SEMESTER_BOUND",
                "date": "YYYY-MM-DD",
                "startTime": "HH:mm" (optional),
                "endTime": "HH:mm" (optional),
                "gradeWeight": 0.15 (Optional. Float value representing the grade percentage, e.g., 0.15 for 15%. Search for grade weight in text nearby, like 'Midterm Exam - 15%'),
                "warning": "String" (Optional. Use this if the source text is contradictory or if a date was calculated from a week number rather than stated explicitly.)
              }
            ]

            ## 4. CONSTRAINTS & GUARDRAILS
            - Return ONLY a raw JSON array of objects following the output schema. No filler.
            - Do NOT include any markdown code blocks (e.g. do not wrap in ```json), explanation, or trailing content.
            - Do NOT invent events not mentioned in the document. Do NOT use your training data to add typical course events. Only extract what is explicitly stated in <source_fragment>.
            - Extract ALL events found in the document regardless of which semester they belong to.
        """.trimIndent()
    }

    fun getEventCritiquePrompt(sourceText: String, eventsJson: String): String {
        return """
            # MEMORANDUM BRIEF: EVENT EXTRACTION QUALITY AUDIT

            ## 1. TOPIC CLARIFICATION
            This brief instructs you to audit a list of extracted events against the raw source syllabus text to eliminate any confabulations or factual errors.

            ## 2. STRUCTURED REFERENCE MATERIAL
            <source_syllabus_document>
            $sourceText
            </source_syllabus_document>

            <extracted_events_json>
            $eventsJson
            </extracted_events_json>

            ## 3. TASK PROMPT
            Critique the extracted events inside <extracted_events_json> by validating each event against the text in <source_syllabus_document>.
            
            Identify and correct any:
            1. Hallucinated/invented events: events whose title or assignment name is not mentioned anywhere in the source syllabus.
            2. Incorrect dates, times, titles, or categories.

            Category rules to apply when correcting:
            - DEADLINE  → any graded submission (papers, essays, projects, quizzes, drafts, discussion posts, homework)
            - FINALS    → final exams / capstone assessments
            - CLASS     → in-person or synchronous class meeting
            - REGULAR   → non-graded one-time academic task
            - HOLIDAY   → official break / no-class day
            - SEMESTER_BOUND → term start/end, add/drop, withdrawal deadlines
            Never use STUDY_BLOCK here — that is reserved for the study plan phase.

            Output Schema:
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

            ## 4. CONSTRAINTS & GUARDRAILS
            - Return ONLY a refined JSON array of objects following the EXACT same schema as the input.
            - Do NOT include any markdown code blocks (e.g. do not wrap in ```json), explanation, or surrounding conversational text.
            - If all events are valid, return the original JSON array unchanged.
            - Only remove events whose title or existence is not supported by the source document at all.
            - Events with dates calculated from week numbers are VALID. Do not flag or remove them — their warning field will already note the calculation.
            - CLASS events (in-person/synchronous meetings) are VALID even if they launch an assignment or involve watching an online video — the class meeting still occurs. Never remove or recategorize a CLASS event solely because its topic refers to online content or assignment distribution.
        """.trimIndent()
    }
}
