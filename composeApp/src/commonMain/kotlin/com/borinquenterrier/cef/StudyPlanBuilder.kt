package com.borinquenterrier.cef

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Builder for study plan and syllabus-related prompts.
 * Handles proactive study block generation and scheduling constraints.
 */
object StudyPlanBuilder {

    private val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year

    private fun formatHour(hour: Int): String {
        val amPm = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "${hour.toString().padStart(2, '0')}:00 ($displayHour $amPm)"
    }

    fun getSyllabusStudyPlanPrompt(
        syllabusText: String,
        existingSchedule: String = "",
        preferences: StudyPreferences = StudyPreferences()
    ): String {
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
              "endTime": "HH:mm" (optional),
              "gradeWeight": 0.15 (Optional. Float value representing the grade percentage, e.g., 0.15 for 15%. Determine from the syllabus context if available.)
            }
            
            Strict Scheduling Constraints:
            - CATEGORY RULES: ALL proactively suggested study or work periods MUST use the "STUDY_BLOCK" category. Only use "FINALS" or "DEADLINE" for the actual due date/exam itself.
            - PRIORITIES: Calendar Events (Scheduled Class Times) have strict priority over Study/Work Times.
            - EXAMS: Exam Times do NOT coincide with Class Times (extended time accommodations take students out of standard class periods).
            - COLLISIONS: Study/Work Times cannot collide with Exam Times, Class Times, or ANY existing events on the schedule. If a study/work task collides, move it to the latest available time BEFORE the deadline.
            - HOLIDAYS: Classes do not meet on holidays; these periods are completely available for study, work, and breaks.
            - WORKING HOURS: Do not schedule ANY work or study before ${formatHour(preferences.studyStartHour)} or after ${
            formatHour(
                preferences.studyEndHour
            )
        }.
            - DAILY BREAKS: You must leave a continuous block open for lunch every day from ${
            formatHour(
                preferences.lunchStartHour
            )
        } to ${formatHour(preferences.lunchEndHour)}, and a separate continuous block open in the late afternoon/evening for exercise and dinner from ${
            formatHour(
                preferences.dinnerStartHour
            )
        } to ${formatHour(preferences.dinnerEndHour)}. Do not schedule study during these times.
            - STUDY BLOCKS: The maximum duration of a single STUDY_BLOCK should be ${preferences.maxStudyBlockHours} hours, with a preferred break of at least ${preferences.preferredBreakMinutes} minutes between study blocks.
            - PROACTIVE STUDY TIME ALLOCATION: Allocate STUDY_BLOCKs based on the deliverable's weight (extracted in "gradeWeight"). Allocate more preparation hours for higher-weighted deliverables (e.g., suggest 3-4 study blocks for a 30% final exam, but only 1 block or none for a 2% quiz).
            
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

    fun getTaskDecompositionPrompt(
        taskTitle: String,
        dueDate: String,
        context: String = ""
    ): String {
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

    fun getDecompositionCritiquePrompt(
        taskTitle: String,
        dueDate: String,
        tasksJson: String
    ): String {
        return """
            You are an executive function coach and quality auditor.
            
            A student has a task "$taskTitle" due on $dueDate. The following sub-tasks were generated to break it down:
            
            # Sub-tasks (JSON):
            $tasksJson
            
            # Task:
            Critique this decomposition plan. Ensure:
            1. Each sub-task is realistic, concrete, and highly actionable.
            2. None of the steps are too large or overwhelming (each should take 1-2 hours max).
            3. The steps flow logically backwards or forwards.
            4. There are no redundant steps.
            
            Return a refined JSON array of objects following the EXACT same schema as the input:
            [
              {
                "title": "Specific, small action",
                "daysBeforeDue": Integer,
                "description": "Brief tip on how to start this small step."
              }
            ]
            
            Ensure you ONLY output the corrected JSON array. If the tasks are already perfect, return the original JSON array. Do not include any explanation or markdown formatting.
        """.trimIndent()
    }
}
