package com.borinquenterrier.cef

import kotlin.time.Clock
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
            # MEMORANDUM BRIEF: STUDY PLAN AND DELIVERABLE EXTRACTION

            ## 1. TOPIC CLARIFICATION
            This brief instructs you to extract academic deliverables from a syllabus and proactively suggest study blocks (STUDY_BLOCK events) leading up to major deliverables.

            ## 2. STRUCTURED REFERENCE MATERIAL
            <study_preferences>
            - Working Hours: Do not schedule ANY work or study before ${formatHour(preferences.studyStartHour)} or after ${formatHour(preferences.studyEndHour)}.
            - Daily Breaks: Leave a continuous block open for lunch every day from ${formatHour(preferences.lunchStartHour)} to ${formatHour(preferences.lunchEndHour)}, and a separate block for exercise and dinner from ${formatHour(preferences.dinnerStartHour)} to ${formatHour(preferences.dinnerEndHour)}.
            - Maximum Duration: The maximum duration of a single STUDY_BLOCK should be ${preferences.maxStudyBlockHours} hours, with a preferred break of at least ${preferences.preferredBreakMinutes} minutes between study blocks.
            </study_preferences>

            <existing_schedule>
            ${if (existingSchedule.isBlank()) "None" else existingSchedule}
            </existing_schedule>

            <source_syllabus_document>
            $syllabusText
            </source_syllabus_document>

            ## 3. TASK PROMPT
            Analyze the content inside <source_syllabus_document> and construct a study plan:
            1. Extract all deliverables (Assignments, Quizzes, Exams, Projects).
            2. For each major deliverable (Exams, Projects, or Assignments worth >10%), proactively suggest 2-3 "STUDY_BLOCK" events in the days leading up to it.

            Category rules to apply:
            - STUDY_BLOCK: ALL proactively suggested study or work periods MUST use "STUDY_BLOCK".
            - REGULAR: Use for one-time academic tasks (e.g. Writing Center visits, peer review sessions).
            - DEADLINE: Use for the actual due date of a graded submission.
            - FINALS: Use for a final exam event.
            - CLASS: Do NOT generate CLASS events. Scheduled class sessions are already on the student's calendar.

            Scheduling constraint rules:
            - PRIORITIES: Existing class times (shown in <existing_schedule>) have strict priority — do not schedule anything during those times.
            - COLLISIONS: Study/Work Times cannot collide with Exam Times, Class Times, or ANY existing events on the schedule. If a study/work task collides, move it to the latest available time BEFORE the deadline.
            - HOLIDAYS: Classes do not meet on holidays; these periods are completely available for study, work, and breaks.
            - PROACTIVE STUDY TIME ALLOCATION: Allocate STUDY_BLOCKs based on the deliverable's weight (extracted in "gradeWeight"). Allocate more preparation hours for higher-weighted deliverables (e.g., suggest 3-4 study blocks for a 30% final exam, but only 1 block or none for a 2% quiz).

            Output Schema:
            [
              {
                "title": "Clear, actionable title (e.g. 'Submit Essay' or 'Study for Midterm')",
                "type": "TIME" or "DAY",
                "category": "DEADLINE", "FINALS", "REGULAR", or "STUDY_BLOCK",
                "date": "YYYY-MM-DD",
                "startTime": "HH:mm" (optional),
                "endTime": "HH:mm" (optional),
                "gradeWeight": 0.15 (Optional. Float value representing the grade percentage, e.g., 0.15 for 15%. Determine from the syllabus context if available.)
              }
            ]

            ## 4. CONSTRAINTS & GUARDRAILS
            - Return ONLY a raw JSON array of objects following the output schema. No filler.
            - Do NOT include any markdown code blocks (e.g. do not wrap in ```json), explanation, or trailing content.
            - Do NOT generate CLASS events.
            - Use the year $currentYear unless specified.
            - Focus on creating a balanced schedule that avoids "crunching" before deadlines. Space out the STUDY_BLOCKs reasonably.
        """.trimIndent()
    }

    fun getStudyPlanCritiquePrompt(syllabusText: String, eventsJson: String): String = """
        # MEMORANDUM BRIEF: STUDY PLAN QUALITY AUDIT

        ## 1. TOPIC CLARIFICATION
        This brief instructs you to audit a generated study plan to improve preparation distribution and ensure that no class sessions are re-extracted.

        ## 2. STRUCTURED REFERENCE MATERIAL
        <source_syllabus_document>
        $syllabusText
        </source_syllabus_document>

        <study_plan_json>
        $eventsJson
        </study_plan_json>

        ## 3. TASK PROMPT
        Review the study plan in <study_plan_json> against <source_syllabus_document>:
        1. Remove hallucinated events not grounded in the syllabus.
        2. Remove CLASS events or re-categorize them as REGULAR or STUDY_BLOCK. The student's calendar already contains class sessions; do NOT add, keep, or re-introduce any events with category "CLASS".
        3. Fix incorrect dates, titles, or categories.
        4. Remove duplicates.

        Category rules to apply:
        - STUDY_BLOCK: any study or work preparation period
        - REGULAR: one-time academic tasks not covered above (Writing Center visits, peer review, etc.)
        - DEADLINE: the actual due date for a submission
        - FINALS: a final exam event

        Output Schema:
        [
          {
            "title": "Title",
            "type": "TIME" or "DAY",
            "category": "STUDY_BLOCK", "REGULAR", "DEADLINE", or "FINALS",
            "date": "YYYY-MM-DD",
            "startTime": "HH:mm" (optional),
            "endTime": "HH:mm" (optional)
          }
        ]

        ## 4. CONSTRAINTS & GUARDRAILS
        - Return ONLY the refined JSON array matching the output schema. No filler.
        - Do NOT include any markdown code blocks (e.g. do not wrap in ```json), conversational text, or explanations.
        - DO NOT add, keep, or re-introduce any events with category "CLASS". If any event in the list has category "CLASS", remove it or change it to "STUDY_BLOCK" or "REGULAR".
        - Allowed categories are STUDY_BLOCK, REGULAR, DEADLINE, FINALS only.
    """.trimIndent()

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
            # MEMORANDUM BRIEF: TASK DECOMPOSITION

            ## 1. TOPIC CLARIFICATION
            You are acting as an Executive Function Coach. This brief instructs you to break down a larger academic assignment into structured, bite-sized chronological sub-tasks.

            ## 2. STRUCTURED REFERENCE MATERIAL
            <target_task>
            Title: $taskTitle
            Due Date: $dueDate
            Today's Date: $today
            Days Available: $daysAvailable
            </target_task>
            ${if (context.isNotBlank()) "\n<extra_context>\n$context\n</extra_context>" else ""}

            ## 3. TASK PROMPT
            Analyze <target_task> and break it down into chronological sub-tasks based on the following timeline guidance:
            $urgencyBlock

            Each sub-task must represent a concrete step that takes 1–2 hours maximum to complete.

            Output Schema:
            [
              {
                "title": "Short title of the step",
                "daysBeforeDue": 3,
                "description": "Detailed explanation of what to do"
              }
            ]

            ## 4. CONSTRAINTS & GUARDRAILS
            - Return ONLY a raw JSON array following the output schema. No filler.
            - Do NOT include any markdown code blocks (e.g. do not wrap in ```json), explanation, or conversational text.
            - Total steps: $stepCount (do not produce more or fewer).
            - Every daysBeforeDue MUST be an integer in [0, $daysAvailable].
            - daysBeforeDue = $daysAvailable means "start today".
            - daysBeforeDue = 0 means "final submission step".
            - Each step should take 1–2 hours maximum.
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
            # MEMORANDUM BRIEF: TASK DECOMPOSITION QUALITY AUDIT

            ## 1. TOPIC CLARIFICATION
            This brief instructs you to act as an executive function coach and quality auditor to review and refine a proposed decomposition of sub-tasks.

            ## 2. STRUCTURED REFERENCE MATERIAL
            <target_task>
            Title: $taskTitle
            Due Date: $dueDate
            Today's Date: $today
            Days Available: $daysAvailable
            </target_task>

            <sub_tasks_json>
            $tasksJson
            </sub_tasks_json>

            ## 3. TASK PROMPT
            Audit the sub-tasks in <sub_tasks_json> against the target task in <target_task>. 
            Refine the tasks using the following checklist:
            1. Each step must be concrete and actionable (1–2 hours max).
            2. No step has daysBeforeDue > $daysAvailable — remove or cap any that do.
            3. Remove redundant or duplicate steps.
            4. Ensure steps flow logically toward the final submission.
            5. Total step count does not exceed ${if (daysAvailable <= 2) 5 else if (daysAvailable <= 7) 7 else 9}.

            Output Schema:
            [
              {
                "title": "Title of the step",
                "daysBeforeDue": N,
                "description": "Description of the step"
              }
            ]

            ## 4. CONSTRAINTS & GUARDRAILS
            - Return ONLY the refined JSON array following the output schema. No filler.
            - Do NOT include any markdown code blocks (e.g. do not wrap in ```json), explanation, or conversational text.
            - If no changes are needed, return the original JSON array unchanged.
        """.trimIndent()
    }
}
