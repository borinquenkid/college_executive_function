package com.borinquenterrier.cef

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Centrally managed prompts for the AI Service to ensure consistency and reusability.
 */
object AiPrompts {

    private val currentYear = Clock.System.todayIn(TimeZone.currentSystemDefault()).year

    val SOURCE_FRAGMENT_INSTRUCTIONS = """
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

    /**
     * Prompt for extracting events from a specific SourceFragment.
     */
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
                "warning": "String" (Optional. Use this if the source text is contradictory, e.g., 'Document says Monday Jan 1st, but Jan 1st is a Thursday'. Be STRICT about the literal date provided.)
              }
            ]

            # Context
            Current Year: $currentYear

            # Data to Process (SourceFragment JSON)
            $fragmentJson
        """.trimIndent()
    }


    /**
     * Specialized prompt for syllabus analysis that also generates proactive study periods.
     */
    fun getSyllabusStudyPlanPrompt(syllabusText: String, existingSchedule: String = ""): String {
        return """
            You are an Academic Success Assistant. Analyze the provided syllabus and:
            1. Extract all deliverables (Assignments, Quizzes, Exams, Projects) and Scheduled Class Times.
            2. For each major deliverable (Exams, Projects, or Assignments worth >10%), 
               proactively suggest 2-3 "STUDY_BLOCK" events in the days leading up to it.
            
            Return the data EXCLUSIVELY as a JSON array of objects. Do not include any conversational filler.
            
            Structure:
            {
              "title": "Clear, actionable title (e.g. 'Submit Essay' or 'Study for Midterm')",
              "type": "TIME" or "DAY",
              "category": "DEADLINE", "FINALS", "CLASS", "HOLIDAY", or "STUDY_BLOCK",
              "date": "YYYY-MM-DD",
              "startTime": "HH:mm" (optional),
              "endTime": "HH:mm" (optional)
            }
            
            Strict Scheduling Constraints:
            - CATEGORY RULES: ALL proactively suggested study or work periods MUST use the "STUDY_BLOCK" category. Only use "FINALS" or "DEADLINE" for the actual due date/exam itself.
            - PRIORITIES: Calendar Events (Scheduled Class Times) have strict priority over Study/Work Times.
            - EXAMS: Exam Times do NOT coincide with Class Times (extended time accommodations take students out of standard class periods).
            - COLLISIONS: Study/Work Times cannot collide with Exam Times, Class Times, or ANY existing events on the schedule. If a study/work task collides, move it to the latest available time BEFORE the deadline.
            - HOLIDAYS: Classes do not meet on holidays; these periods are completely available for study, work, and breaks.
            - WORKING HOURS: Do not schedule ANY work or study before 09:00 (9 AM) or after 21:00 (9 PM).
            - DAILY BREAKS: You must leave a 1-hour continuous block open for lunch every day, and a separate 2-hour continuous block open in the late afternoon/evening for exercise and dinner. Do not schedule study during these times.
            
            General Guidelines:
            - Focus on creating a balanced schedule that avoids "crunching" before deadlines.
            - Space out the STUDY_BLOCKs reasonably.
            - Use the year $currentYear unless specified.
            
            Existing Schedule (DO NOT OVERLAP WITH THESE):
            ${if (existingSchedule.isBlank()) "None" else existingSchedule}
            
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

    /**
     * Prompt for extracting "Decision Intelligence" from a document.
     * This captures the rules of the game (grading, policies, contacts).
     */
    fun getDocumentIntelligencePrompt(text: String): String {
        return """
            Analyze the provided document (likely a course syllabus) and extract the "Rules of the Game".
            Focus on metadata that would help a student make decisions about their time and effort.

            Extract the following keys if found:
            - "grading_scale": (String summary of weights, e.g., 'Final 30%, Midterm 20%, Quizzes 10%')
            - "late_policy": (String summary of penalties)
            - "attendance_policy": (String summary)
            - "professor_contact": (Preferred method and details)
            - "academic_integrity": (Brief summary of key rules)
            - "required_materials": (Books, software)

            Return ONLY a raw JSON object with these keys. No filler. 
            If a value is not found, use null.

            Document Text:
            $text
        """.trimIndent()
    }
    }
