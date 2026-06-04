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


    private fun formatHour(hour: Int): String {
        val amPm = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "${hour.toString().padStart(2, '0')}:00 ($displayHour $amPm)"
    }

    /**
     * Specialized prompt for syllabus analysis that also generates proactive study periods.
     */
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
            - WORKING HOURS: Do not schedule ANY work or study before ${formatHour(preferences.studyStartHour)} or after ${formatHour(preferences.studyEndHour)}.
            - DAILY BREAKS: You must leave a continuous block open for lunch every day from ${formatHour(preferences.lunchStartHour)} to ${formatHour(preferences.lunchEndHour)}, and a separate continuous block open in the late afternoon/evening for exercise and dinner from ${formatHour(preferences.dinnerStartHour)} to ${formatHour(preferences.dinnerEndHour)}. Do not schedule study during these times.
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

    /**
     * Prompt for automatically categorizing academic sources.
     */
    fun getSourceCategorizationPrompt(text: String): String {
        return """
            Analyze the provided document text and categorize it into exactly one of the following categories:
            - "Syllabus" (contains course details, schedule, grading scales, assignment due dates, policies)
            - "Reading Material" (textbook chapters, papers, articles, research papers, stories)
            - "Lab Manual" (instructions/procedures for laboratory experiments or hands-on activities)
            - "Lecture Notes" (summaries of lectures, presentation slides, class notes)
            - "Other" (any other document that does not fit the above categories)

            Analyze the tone, structure, and content of the document.
            Return ONLY a raw JSON object with a single key "category" whose value is one of the exact strings above.
            Example output format:
            {
              "category": "Syllabus"
            }

            Document Text (truncated/sample):
            $text
        """.trimIndent()
    }

    /**
     * Prompt for multi-source chat. Aggregates context from ALL available course materials
     * and threads conversation history so the model can handle follow-up questions coherently.
     *
     * @param sourceBlocks Distilled context for each source, sorted by category priority
     *                     (SYLLABUS first) before being passed here.
     * @param conversationHistory Prior chat turns as (author, content) pairs. At most
     *                            [MAX_HISTORY_TURNS] recent turns are injected.
     * @param question The student's current question.
     */
    fun getMultiSourceChatPrompt(
        sourceBlocks: List<SourceContextBlock>,
        conversationHistory: List<Pair<String, String>>,
        question: String
    ): String {
        val sourcesSection = if (sourceBlocks.isEmpty()) {
            "No course materials are loaded yet. Ask the student to add a source first."
        } else {
            sourceBlocks.joinToString("\n\n---\n\n") { block ->
                buildString {
                    appendLine("### ${block.title} [${block.category}]")
                    if (!block.metadata.isNullOrBlank()) {
                        appendLine("**Policies & Rules:**")
                        appendLine(block.metadata)
                        appendLine()
                    }
                    val content = if (block.fragmentText.length > MAX_CHARS_PER_SOURCE)
                        block.fragmentText.take(MAX_CHARS_PER_SOURCE) + "\n… [content truncated]"
                    else block.fragmentText
                    appendLine("**Content:**")
                    append(content)
                }
            }
        }

        val historySection = if (conversationHistory.isEmpty()) {
            "(No prior messages)"
        } else {
            conversationHistory.takeLast(MAX_HISTORY_TURNS).joinToString("\n") { (author, content) ->
                "${if (author == "User") "Student" else "Assistant"}: $content"
            }
        }

        return """
            You are an Academic Success Assistant with full access to a student's course materials.
            Answer questions by reasoning across ALL provided sources and synthesizing information.

            # Course Materials (${sourceBlocks.size} source(s))

            $sourcesSection

            # Prior Conversation
            $historySection

            # Student's Question
            $question

            # Instructions
            - Base your answer ONLY on the provided materials. Do not use outside knowledge.
            - If relevant information spans multiple sources, synthesize it and cite the source title.
            - If the answer is not found in any provided source, say so clearly rather than guessing.
            - Keep answers concise and actionable for a student managing their academics.
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

    fun getChatCritiquePrompt(originalPrompt: String, response: String): String {
        return """
            You are a factual critique and quality control agent.
            
            Below is the original user prompt / chat history and the generated response.
            
            # Original Prompt / Context:
            $originalPrompt
            
            # Generated Response:
            $response
            
            # Task:
            Critique the generated response. Check if:
            1. The response contains any assertions or facts that are NOT supported by the source materials in the original prompt context.
            2. The response contains hallucinations, fabrications, or outside assumptions.
            
            If the response is fully factual and supported by the sources, return the original response completely unchanged.
            If the response contains unsupported information or makes assumptions, revise it to ONLY use facts explicitly stated in the source materials. If a fact cannot be verified, clearly state "I do not have enough information to answer that based on the provided materials."
            
            Return ONLY the final revised response text. Do not add any intros, explanations, or meta-commentary.
        """.trimIndent()
    }

    fun getDecompositionCritiquePrompt(taskTitle: String, dueDate: String, tasksJson: String): String {
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

    fun getSyllabusAuditPrompt(syllabusText: String): String {
        return """
            You are a strict syllabus auditor. Analyze the following syllabus text and identify structural ambiguities, inconsistencies, or assumptions that could make calendar extraction unreliable.
            
            Specifically, scan for and identify:
            1. External Calendar/LMS dependencies: Mentions of Blackboard, Canvas, Moodle, or other external websites/platforms where the weekly schedule, quizzes, or assignments are actually hosted/due (e.g., "All quizzes are on Blackboard", "Assignments are posted weekly on Canvas").
            2. Tentative Schedule declarations: Clarifications that the schedule is tentative, subject to change, or approximate (e.g., "schedule is tentative and subject to change", "dates may be adjusted").
            3. Grading policies that affect deadlines or scheduling: Dropped grade rules, optional exams, or alternate submission policies (e.g., "lowest quiz grade dropped", "final exam is optional if you pass all midterms").
            4. Date/Day Contradictions: Discrepancies between days of the week and dates listed in the syllabus (e.g., "Monday, Oct 12" when Oct 12 is a Tuesday).
            
            Return ONLY a raw JSON object with the following schema:
            {
              "hasAmbiguities": true/false,
              "findings": [
                {
                  "type": "EXTERNAL_LMS" | "TENTATIVE" | "GRADING_POLICY" | "DATE_CONTRADICTION",
                  "description": "Brief description of the ambiguity (e.g., 'Weekly quizzes are hosted on Blackboard instead of listed here')",
                  "severity": "HIGH" | "MEDIUM" | "LOW"
                }
              ]
            }
            Do not include any markdown formatting (like ```json) or conversational filler.
            
            Syllabus Text:
            ${'$'}syllabusText
        """.trimIndent()
    }

    private const val MAX_CHARS_PER_SOURCE = 6_000
    private const val MAX_HISTORY_TURNS = 10
}

/**
 * Holds the distilled context for one source, used by [AiPrompts.getMultiSourceChatPrompt].
 */
data class SourceContextBlock(
    val title: String,
    val category: String,
    val metadata: String?,
    val fragmentText: String
)
