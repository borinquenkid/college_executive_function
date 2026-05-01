package com.borinquenterrier.cef

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Centrally managed prompts for the AI Service to ensure consistency and reusability.
 */
object AiPrompts {

    private val currentYear = Clock.System.todayIn(TimeZone.currentSystemDefault()).year

    val CHUNKING_INSTRUCTIONS = """
        # Source Chunk Interpretation Instructions
        You are receiving a JSON object representing a 'SourceChunk'. 
        
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

    /**
     * Prompt for extracting events from a specific SourceChunk.
     */
    fun getChunkEventExtractionPrompt(chunkJson: String): String {
        return """
            $CHUNKING_INSTRUCTIONS
            
            # Objective
            Analyze the provided SourceChunk and identify all academic deliverables, deadlines, or calendar events.
            
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
                "endTime": "HH:mm" (optional)
              }
            ]
            
            # Context
            Current Year: $currentYear
            
            # Data to Process (SourceChunk JSON)
            $chunkJson
        """.trimIndent()
    }


    /**
     * Specialized prompt for syllabus analysis that also generates proactive study periods.
     */
    fun getSyllabusStudyPlanPrompt(syllabusText: String): String {
        return """
            You are an Academic Success Assistant. Analyze the provided syllabus and:
            1. Extract all deliverables (Assignments, Quizzes, Exams, Projects).
            2. For each major deliverable (Exams, Projects, or Assignments worth >10%), 
               proactively suggest 2-3 "STUDY_BLOCK" events in the days leading up to it.
            
            Return the data EXCLUSIVELY as a JSON array of objects. Do not include any conversational filler.
            
            Structure:
            {
              "title": "Clear, actionable title (e.g. 'Submit Essay' or 'Study for Midterm')",
              "type": "TIME" or "DAY",
              "category": "DEADLINE", "FINALS", or "STUDY_BLOCK",
              "date": "YYYY-MM-DD",
              "startTime": "HH:mm" (optional),
              "endTime": "HH:mm" (optional)
            }
            
            Guidelines:
            - Focus on creating a balanced schedule that avoids "crunching" before deadlines.
            - Space out the STUDY_BLOCKs reasonably.
            - Use the year $currentYear unless specified.
            
            Syllabus Text:
            $syllabusText
        """.trimIndent()
    }

    /**
     * Prompt for breaking down a large assignment into smaller, actionable sub-tasks.
     * This is a core executive function support feature.
     */
    fun getTaskDecompositionPrompt(taskTitle: String, dueDate: String, context: String = ""): String {
        return """
            You are an Executive Function Coach. Your goal is to help a student break down a large, 
            intimidating assignment ("$taskTitle" due on $dueDate) into smaller, manageable, and 
            actionable sub-tasks to prevent overwhelm and procrastination.
            
            Provide a step-by-step plan with suggested intermediate deadlines.
            
            Return the plan EXCLUSIVELY as a JSON array of objects.
            
            Structure:
            {
              "title": "Specific, small action (e.g., 'Draft first 200 words of intro')",
              "daysBeforeDue": 5,
              "description": "Brief tip on how to start this small step."
            }
            
            Guidelines:
            - Each step should take no more than 1-2 hours.
            - Focus on the very first "low-friction" step to get started.
            - Work backwards from the due date $dueDate.
            
            Context (if any):
            $context
        """.trimIndent()
    }
}
