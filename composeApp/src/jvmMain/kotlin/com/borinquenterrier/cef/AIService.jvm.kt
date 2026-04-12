package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.Settings
import kotlinx.datetime.*

// A dummy implementation for now. We'll replace this with a real AI service later.
actual class AIService(private val settings: Settings) {
    actual suspend fun generateChatResponse(prompt: String): String {
        return "This is a dummy response. The prompt was: $prompt"
    }

    actual suspend fun generateCalendarEvents(prompt: String): List<Event> {
        if (prompt.contains("Academic Milestones")) {
            val currentYear = Clock.System.todayIn(TimeZone.currentSystemDefault()).year
            val fallBreakStart = LocalDate(currentYear, 10, 14)
            val springBreakStart = LocalDate(currentYear, 3, 10)
            val weekdays = listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            )

            return listOf(
                DayEvent(title = "Labor Day", source = EventSource.AI_GENERATED, category = AcademicCategory.HOLIDAY, date = LocalDate(currentYear, 9, 2)),
                DayEvent(title = "Last Day to Drop", source = EventSource.AI_GENERATED, category = AcademicCategory.DEADLINE, date = LocalDate(currentYear, 9, 13)),
                DayEvent(title = "Fall Break", source = EventSource.AI_GENERATED, category = AcademicCategory.HOLIDAY, date = fallBreakStart, 
                    recurrence = Recurrence(weekdays, fallBreakStart, fallBreakStart.plus(4, DateTimeUnit.DAY))),
                DayEvent(title = "Finals Week Starts", source = EventSource.AI_GENERATED, category = AcademicCategory.FINALS, date = LocalDate(currentYear, 12, 9)),
                DayEvent(title = "Semester Ends", source = EventSource.AI_GENERATED, category = AcademicCategory.SEMESTER_BOUND, date = LocalDate(currentYear, 12, 13)),
                DayEvent(title = "Spring Break", source = EventSource.AI_GENERATED, category = AcademicCategory.HOLIDAY, date = springBreakStart,
                    recurrence = Recurrence(weekdays, springBreakStart, springBreakStart.plus(4, DateTimeUnit.DAY)))
            )
        }
        // Dummy implementation for other prompts.
        return emptyList()
    }
}

@Composable
actual fun rememberAIService(): AIService {
    val settings = rememberSettings()
    return remember { AIService(settings) }
}
