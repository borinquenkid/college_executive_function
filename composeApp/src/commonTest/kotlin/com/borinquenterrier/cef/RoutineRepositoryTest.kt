package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class RoutineRepositoryTest : FunSpec({

    test("Routine events are saved and retrieved correctly") {
        // 1. Arrange
        val settings = MapSettings()
        val repository = RoutineRepository(settings)
        val routineEvents = listOf(
            TimeEvent(
                title = "CS 101 Lecture",
                source = EventSource.ROUTINE,
                startTime = LocalTime(10, 30),
                endTime = LocalTime(11, 45),
                date = LocalDate(2024, 8, 26),
                recurrence = Recurrence(
                    daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                    startDate = LocalDate(2024, 8, 26),
                    endDate = LocalDate(2024, 12, 13)
                )
            )
        )

        // 2. Act
        repository.saveRoutineEvents(routineEvents)
        val loadedEvents = repository.getRoutineEvents()

        // 3. Assert
        loadedEvents shouldBe routineEvents
    }

    test("getRoutineEvents returns an empty list when no routine is saved") {
        // 1. Arrange
        val settings = MapSettings()
        val repository = RoutineRepository(settings)

        // 2. Act
        val loadedEvents = repository.getRoutineEvents()

        // 3. Assert
        loadedEvents.isEmpty() shouldBe true
    }
})
