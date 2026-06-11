package com.borinquenterrier.cef

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Builder for event extraction and critique prompts.
 * Handles source fragment parsing and event quality assurance.
 */
object EventBuilder {

    @OptIn(kotlin.time.ExperimentalTime::class)
    private val currentYear = Clock.System.todayIn(TimeZone.currentSystemDefault()).year

    private val SOURCE_FRAGMENT_INSTRUCTIONS = """
        # Source Fragment Interpretation Instructions
        You are receiving a JSON object representing a 'SourceFragment'. 
        
        ## Fields:
        - `text`: The raw content to analyze.
        - `pageNumber`: (Optional) The page of the document this text came from.
        - `sectionTitle`: (Optional) The title of the document section.
        - `type`: Either `TEXT` (general material) or `CALENDAR` (RFC 5545 format).
        - `metadata`: A map of additional context.

        ## Instructions:
        1. If `type` is `CALENDAR`, the `text` is an iCalendar VEVENT. Map it directly to the target schema.
        2. If `type` is `TEXT`, perform extraction from the `text` field using any provided `pageNumber` or `sectionTitle` as additional context for locating the event in time or importance.
    """.trimIndent()

    fun getSourceEventExtractionPrompt(fragmentJson: String): String {
        return """
            $SOURCE_FRAGMENT_INSTRUCTIONS

            # Objective
            Analyze the provided SourceFragment and identify all academic deliverables, deadlines, or calendar events.
            IMPORTANT: Only extract events that are EXPLICITLY stated in the provided text. Do NOT generate, infer, or add events from your training data or general knowledge.

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
                "warning": "String" (Optional. Use this if the source text is contradictory, e.g., 'Document says Monday Jan 1st, but Jan 1st is a Thursday'. Be STRICT about the literal date provided.)
              }
            ]

            # Context
            Current Year: $currentYear

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
            1. Hallucinated/invented events that are not explicitly stated in the source document.
            2. Incorrect dates, times, titles, or categories.
            
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
