package com.borinquenterrier.cef

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn

/**
 * Builder for study plan and syllabus-related prompts.
 * Handles proactive study block generation and scheduling constraints.
 */
object StudyPlanBuilder {

    private val currentYear = Clock.System.todayIn(TimeZone.currentSystemDefault()).year

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
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val due = LocalDate.parse(dueDate)
        val daysAvailable = today.daysUntil(due).coerceAtLeast(0)

        val urgencyBlock = when {
            daysAvailable == 0 -> """
                EMERGENCY — This assignment is due TODAY.
                Provide 2–4 immediate steps only, focused on:
                  • What can realistically be submitted in the next few hours
                  • Whether requesting an extension or accepting a late penalty makes sense
                  • How to maximize partial credit with the time left
                Do NOT plan beyond today.
            """.trimIndent()
            daysAvailable <= 2 -> """
                TIGHT DEADLINE — Only $daysAvailable day(s) until due.
                Provide 3–5 high-priority steps that fit within $daysAvailable day(s).
                Keep each step under 90 minutes. No research phases; focus on what is achievable.
            """.trimIndent()
            daysAvailable <= 7 -> """
                SHORT TIMELINE — $daysAvailable days available.
                Provide 4–7 focused steps spread across the $daysAvailable days.
                Prioritize what is most essential; skip nice-to-have prep.
            """.trimIndent()
            else -> """
                NORMAL TIMELINE — $daysAvailable days available.
                Provide 5–9 steps spread across the available time.
                Include an early research/planning phase and a revision pass before submission.
            """.trimIndent()
        }

        val stepCount = when {
            daysAvailable == 0 -> "2–4"
            daysAvailable <= 2 -> "3–5"
            daysAvailable <= 7 -> "4–7"
            else -> "5–9"
        }

        return """
            You are an Executive Function Coach helping a student plan their work on:
            "$taskTitle" — due $dueDate

            Today: $today
            Days available: $daysAvailable

            $urgencyBlock

            HARD CONSTRAINTS (violating any of these is an error):
            - Total steps: $stepCount (do not produce more)
            - Every daysBeforeDue MUST be an integer in [0, $daysAvailable]
            - daysBeforeDue = $daysAvailable means "start today"
            - daysBeforeDue = 0 means "final submission step"
            - Each step should take 1–2 hours maximum

            Return ONLY a raw JSON array — no markdown, no explanation:
            [{"title": "...", "daysBeforeDue": N, "description": "..."}]
            ${if (context.isNotBlank()) "\nContext: $context" else ""}
        """.trimIndent()
    }

    fun getDecompositionCritiquePrompt(
        taskTitle: String,
        dueDate: String,
        tasksJson: String
    ): String {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val due = runCatching { LocalDate.parse(dueDate) }.getOrNull()
        val daysAvailable = due?.let { today.daysUntil(it).coerceAtLeast(0) } ?: 30

        return """
            You are an executive function coach and quality auditor.

            A student has "$taskTitle" due on $dueDate (today is $today; $daysAvailable days available).

            # Sub-tasks (JSON):
            $tasksJson

            # Review checklist:
            1. Each step is concrete and actionable (1–2 hours max).
            2. No step has daysBeforeDue > $daysAvailable — remove or cap any that do.
            3. No redundant or duplicate steps.
            4. Steps flow logically toward submission.
            5. Total step count does not exceed ${if (daysAvailable <= 2) 5 else if (daysAvailable <= 7) 7 else 9}.

            Return ONLY the refined JSON array — same schema, no markdown:
            [{"title": "...", "daysBeforeDue": N, "description": "..."}]
        """.trimIndent()
    }
}
