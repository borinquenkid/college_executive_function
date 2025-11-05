package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

actual class AIService {
    actual suspend fun generateChatResponse(prompt: String): String {
        delay(1000)
        return "This is a dummy iOS chat response to: '$prompt'"
    }

    actual suspend fun generateCalendarEvents(prompt: String): List<CalendarEvent> {
        delay(2000) // Simulate a network request
        val now = Clock.System.now()
        return listOf(
            CalendarEvent("Dummy iOS Event 1", now, now.plus(1, DateTimeUnit.HOUR)),
            CalendarEvent("Dummy iOS Event 2", now.plus(2, DateTimeUnit.HOUR), now.plus(3, DateTimeUnit.HOUR))
        )
    }
}

@Composable
actual fun rememberAIService(): AIService {
    return remember { AIService() }
}
