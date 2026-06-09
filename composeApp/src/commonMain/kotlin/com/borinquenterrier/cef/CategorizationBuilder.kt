package com.borinquenterrier.cef

/**
 * Builder for source categorization and intelligence extraction prompts.
 * Handles document type classification and metadata extraction.
 */
object CategorizationBuilder {

    fun getSourceCategorizationPrompt(text: String): String {
        return """
            Analyze the provided document text and categorize it into exactly one of:
            - "Syllabus": Contains course details, policies, and MUST contain at least one of:
               * Repeating meeting times / class schedule (e.g., "Mondays and Wednesdays 10:00-11:30")
               * Deliverables (quizzes, homework, tests, exams) with deadlines/due dates
            - "Calendar": Contains day-long events, deadlines, and holidays, and MUST contain at least one of:
               * Day-long events
               * Deadlines
               * Holidays

            Validation Rule:
            You must verify that the document contains at least one of the required elements for its category.
            If a document categorized as "Syllabus" has NO repeating meeting times and NO deliverables, or a document categorized as "Calendar" has NO day-long events, deadlines, or holidays, you must set "isValid" to false. Otherwise, set "isValid" to true.

            Return ONLY a raw JSON object with the following keys:
            - "category": either "Syllabus" or "Calendar"
            - "isValid": boolean (true/false)
            - "reason": brief string explaining the categorization and listing at least one matching element found as validation, or explaining why it is invalid.

            Example output format:
            {
              "category": "Syllabus",
              "isValid": true,
              "reason": "Found repeating class time 'MWF 9-10 AM' and homework deadlines."
            }

            Document Text (truncated/sample):
            ${text.take(8000)}
        """.trimIndent()
    }

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
            $syllabusText
        """.trimIndent()
    }
}
