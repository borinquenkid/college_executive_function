package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class RoutineRepositoryTest : FunSpec({

    test("Routine items are saved and retrieved correctly") {
        // 1. Arrange
        val settings = MapSettings()
        val repository = RoutineRepository(settings)
        val routineItems = listOf(
            RoutineItem(
                title = "CS 101 Lecture",
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime(10, 30),
                endTime = LocalTime(11, 45),
                startDate = LocalDate(2024, 8, 26),
                endDate = LocalDate(2024, 12, 13)
            ),
            RoutineItem(
                title = "Gym Session",
                dayOfWeek = DayOfWeek.TUESDAY,
                startTime = LocalTime(18, 0),
                endTime = LocalTime(19, 30),
                startDate = LocalDate(2024, 8, 26),
                endDate = LocalDate(2024, 12, 13)
            )
        )

        // 2. Act
        repository.saveRoutine(routineItems)

        // 3. Assert
        val loadedItems = repository.getRoutine()
        loadedItems shouldBe routineItems
    }

    test("getRoutine returns an empty list when no routine is saved") {
        // 1. Arrange
        val settings = MapSettings()
        val repository = RoutineRepository(settings)

        // 2. Act
        val loadedItems = repository.getRoutine()

        // 3. Assert
        loadedItems.isEmpty() shouldBe true
    }
})
