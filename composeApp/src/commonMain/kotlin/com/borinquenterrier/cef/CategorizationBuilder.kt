package com.borinquenterrier.cef

/**
 * Builder for source categorization and intelligence extraction prompts.
 * Handles document type classification and metadata extraction.
 */
object CategorizationBuilder {

    fun getSourceCategorizationPrompt(text: String): String {
        return """
            # MEMORANDUM BRIEF: SOURCE DOCUMENT CATEGORIZATION

            ## 1. TOPIC CLARIFICATION
            This brief instructs you to analyze a sample text block from a document and categorize it as either a Syllabus or a Calendar, verifying that it contains the structural requirements for the chosen category.

            ## 2. STRUCTURED REFERENCE MATERIAL
            <source_document_sample>
            ${text.take(8000)}
            </source_document_sample>
            (Note: the document sample above is truncated to a maximum of 8000 characters to fit prompt context limits.)

            ## 3. TASK PROMPT
            Analyze the document sample in <source_document_sample> and categorize it:
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

            Output Schema:
            {
              "category": "Syllabus" or "Calendar",
              "isValid": true/false,
              "reason": "Brief string explaining the categorization and listing at least one matching element found as validation, or explaining why it is invalid."
            }

            ## 4. CONSTRAINTS & GUARDRAILS
            - Return ONLY a raw JSON object matching the output schema. No filler.
            - Do NOT include any markdown code blocks (e.g. do not wrap in ```json), explanation, or surrounding text.
        """.trimIndent()
    }

    fun getDocumentIntelligencePrompt(text: String): String {
        return """
            # MEMORANDUM BRIEF: DOCUMENT METADATA INTELLIGENCE

            ## 1. TOPIC CLARIFICATION
            This brief instructs you to analyze a course syllabus text and extract essential metadata rules ("Rules of the Game") that define policies, grading, and contact information.

            ## 2. STRUCTURED REFERENCE MATERIAL
            <source_syllabus_document>
            $text
            </source_syllabus_document>

            ## 3. TASK PROMPT
            Analyze <source_syllabus_document> and extract the following metadata properties if present:
            - "grading_scale": String summary of weights (e.g., 'Final 30%, Midterm 20%, Quizzes 10%')
            - "late_policy": String summary of late penalties (e.g., '10% off per day up to 3 days')
            - "attendance_policy": String summary of attendance and participation rules
            - "professor_contact": Preferred method and details of contact
            - "academic_integrity": Summary of cheating/collusion rules
            - "required_materials": Required textbooks, software, or courseware

            Output Schema:
            {
              "grading_scale": "String" or null,
              "late_policy": "String" or null,
              "attendance_policy": "String" or null,
              "professor_contact": "String" or null,
              "academic_integrity": "String" or null,
              "required_materials": "String" or null
            }

            ## 4. CONSTRAINTS & GUARDRAILS
            - Return ONLY a raw JSON object following the output schema. No filler.
            - Do NOT include any markdown code blocks (e.g. do not wrap in ```json), explanation, or surrounding conversational text.
            - If a value is not found in <source_syllabus_document>, set its key to null.
        """.trimIndent()
    }

    fun getSyllabusAuditPrompt(syllabusText: String): String {
        return """
            # MEMORANDUM BRIEF: SYLLABUS INTEGRITY AUDIT

            ## 1. TOPIC CLARIFICATION
            This brief instructs you to identify structural ambiguities, external platform dependencies, tentative timelines, or day/date contradictions inside a syllabus that could compromise calendar extraction.

            ## 2. STRUCTURED REFERENCE MATERIAL
            <source_syllabus_document>
            $syllabusText
            </source_syllabus_document>

            ## 3. TASK PROMPT
            Audit the syllabus text inside <source_syllabus_document> and scan for the following ambiguity types:
            1. EXTERNAL_LMS: Mentions of Blackboard, Canvas, Moodle, or other external platforms where schedule details, quizzes, or assignments are actually hosted/due (e.g., "All quizzes are on Blackboard", "Assignments are posted weekly on Canvas").
            2. TENTATIVE: Declarations that the schedule is tentative or subject to change (e.g., "schedule is tentative and subject to change", "dates may be adjusted").
            3. GRADING_POLICY: Grading rules that impact deadlines or scheduling (e.g., "lowest quiz grade dropped", "final exam is optional if you pass all midterms").
            4. DATE_CONTRADICTION: Discrepancies between days of the week and dates listed in the syllabus (e.g., "Monday, Oct 12" when Oct 12 is a Tuesday).

            Output Schema:
            {
              "hasAmbiguities": true/false,
              "findings": [
                {
                  "type": "EXTERNAL_LMS" | "TENTATIVE" | "GRADING_POLICY" | "DATE_CONTRADICTION",
                  "description": "Brief description of the ambiguity",
                  "severity": "HIGH" | "MEDIUM" | "LOW"
                }
              ]
            }

            ## 4. CONSTRAINTS & GUARDRAILS
            - Return ONLY a raw JSON object following the output schema. No filler.
            - Do NOT include any markdown code blocks (e.g. do not wrap in ```json), explanations, or trailing remarks.
        """.trimIndent()
    }
}

