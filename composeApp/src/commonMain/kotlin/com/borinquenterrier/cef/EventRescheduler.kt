package com.borinquenterrier.cef

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

internal class EventRescheduler(
    private val pushResolver: CalendarPushResolver,
    private val repository: CalendarAgent,
    private val clock: Clock = Clock.System
) : AgentAction<Event, String> {

    override suspend fun run(input: Event, calendarId: String): String {
        val today = clock.todayIn(TimeZone.currentSystemDefault())
        val updated = when (input) {
            is TimeEvent -> input.copy(date = today)
            is DayEvent -> input.copy(date = today)
        }
        val existing = repository.getEvents(calendarId).toMutableList()
        existing.removeAll { it.id == input.id }
        val rescheduled = pushResolver.resolveAndReschedule(updated, existing, calendarId)
        return if (rescheduled)
            "Rescheduled '${input.title}' successfully."
        else
            "Cannot reschedule: conflict detected."
    }
}
