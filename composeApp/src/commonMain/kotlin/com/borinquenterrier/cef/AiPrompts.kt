package com.borinquenterrier.cef

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Centrally managed prompts for the AI Service to ensure consistency and reusability.
 */
object AiPrompts {

    private val currentYear = Clock.System.todayIn(TimeZone.currentSystemDefault()).year

    /**
     * Prompt for extracting structured academic events from unstructured text (syllabi, websites).
     */
    fun getEventExtractionPrompt(text: String): String {
        return """
            You are an Academic Deliverable Extractor. Your task is to analyze the provided text and extract all important dates, deadlines, projects, exams, and holidays.
            
            Return the data EXCLUSIVELY as a JSON array of objects. Do not include any conversational filler, markdown formatting (no ```json), or explanations.
            
            Each object in the array must follow this structure:
            {
              "title": "Title of the assignment or event",
              "type": "TIME" or "DAY",
              "category": "REGULAR", "HOLIDAY", "DEADLINE", "FINALS", or "SEMESTER_BOUND",
              "date": "YYYY-MM-DD",
              "startTime": "HH:mm" (optional, required if type is TIME),
              "endTime": "HH:mm" (optional, required if type is TIME)
            }
            
            Guidelines:
            - If a specific time is not mentioned, use type "DAY".
            - If it's a major exam or final project, use category "FINALS".
            - If it's a homework, quiz, or regular assignment deadline, use category "DEADLINE".
            - If it's a school break or holiday, use category "HOLIDAY".
            - Use the year $currentYear unless the text explicitly mentions a different year.
            - Format all dates as YYYY-MM-DD and times as HH:mm (24-hour).
            
            Text to analyze:
            $text
        """.trimIndent()
    }
}
