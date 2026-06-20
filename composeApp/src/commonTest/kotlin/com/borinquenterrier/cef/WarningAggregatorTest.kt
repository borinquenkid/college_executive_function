package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

class WarningAggregatorTest : FunSpec({

    val today = LocalDate(2026, 9, 1)

    fun eventWithWarning(warning: String, date: LocalDate = today) = DayEvent(
        title = "Exam", source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE, date = date,
        warning = warning
    )

    test("returns empty list when all inputs are empty or null") {
        WarningAggregator.collect(emptyList(), emptyList(), null, today).shouldBeEmpty()
    }

    test("collects warnings from generated events") {
        val events = listOf(eventWithWarning("Section A missing"))
        val result = WarningAggregator.collect(events, emptyList(), null, today)
        result shouldHaveSize 1
        result.first() shouldBe "Section A missing"
    }

    test("includes persisted warnings") {
        val result = WarningAggregator.collect(emptyList(), listOf("Old warning"), null, today)
        result shouldHaveSize 1
        result.first() shouldBe "Old warning"
    }

    test("includes extractionWarning when present") {
        val result = WarningAggregator.collect(emptyList(), emptyList(), "No events found", today)
        result shouldHaveSize 1
        result.first() shouldBe "No events found"
    }

    test("deduplicates identical warnings across all sources") {
        val events = listOf(eventWithWarning("Dup warning"))
        val result = WarningAggregator.collect(events, listOf("Dup warning"), "Dup warning", today)
        result shouldHaveSize 1
    }

    test("collects from all three sources when distinct") {
        val events = listOf(eventWithWarning("Event warning"))
        val result = WarningAggregator.collect(events, listOf("Persisted"), "Extraction", today)
        result shouldHaveSize 3
    }

    test("events without a warning are not included") {
        val event = DayEvent(
            title = "Class", source = EventSource.AI_GENERATED,
            category = AcademicCategory.CLASS, date = today,
            warning = null
        )
        WarningAggregator.collect(listOf(event), emptyList(), null, today).shouldBeEmpty()
    }
})
