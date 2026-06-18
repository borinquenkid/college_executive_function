package com.borinquenterrier.cef

import kotlinx.datetime.Clock
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
            - Class sessions with specific content (first day, last day, review sessions, field trips)
            - Semester boundaries: first day, last day, add/drop deadline, withdrawal deadline
            - Holidays and breaks that affect this course

            ## Date rules
            - Use EXPLICIT dates when the document states them directly (e.g. "October 14" or "July 3, 2026").
            - Week-anchor pattern: If the document provides a Week 1 date range (e.g. "Week 1: June 8–14") and an assignment only says "Due Week N", calculate the calendar date using that anchor. Use the Monday of the target week as the due date (e.g. "Due Week 4" → June 29).
            - Day-within-week pattern: If a weekly schedule provides per-week date ranges (e.g. "Week 4: June 29–July 5") and an assignment is listed under a named class day within that week (e.g. under "Wednesday"), compute the exact calendar date. Monday of the range = Day 1, Tuesday = +1, Wednesday = +2, Thursday = +3, Friday = +4. Example: "Issue Brief #1 due" under "Wednesday" in "Week 4: June 29–July 5" → July 1, 2026. Getting this right matters — a student who shows up Monday with work due Wednesday submits late.
            - If a time is given (e.g. "due at 11:59 pm"), produce a TIME event; otherwise produce a DAY event.
            - Do NOT invent events not mentioned in the document. Do NOT use your training data to add typical course events. Only extract what is stated.

            # Output Format
            Return ONLY a raw JSON array of objects. No filler.
            Structure:
            [
              {
                "title": "Title",
                "type": "TIME" or "DAY",
                "category": "REGULAR", "HOLIDAY", "DEADLINE", "FINALS", "SEMESTER_BOUND", or "STUDY_BLOCK",
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
            
            Return a refined JSON array of objects following the EXACT same schema as the input:
            [
              {
                "title": "Title",
                "type": "TIME" or "DAY",
                "category": "REGULAR", "HOLIDAY", "DEADLINE", "FINALS", "SEMESTER_BOUND", or "STUDY_BLOCK",
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
