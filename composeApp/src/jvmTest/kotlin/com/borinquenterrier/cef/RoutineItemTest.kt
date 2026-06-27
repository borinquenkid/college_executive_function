package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoutineItemTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true }

    // ── RoutineItem round-trip ────────────────────────────────────────────────

    test("RoutineItem round-trips through JSON") {
        val item = RoutineItem(
            title = "Math 101",
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 30),
            startDate = LocalDate(2026, 1, 12),
            endDate = LocalDate(2026, 5, 10)
        )
        val encoded = json.encodeToString(item)
        val decoded = json.decodeFromString<RoutineItem>(encoded)
        decoded shouldBe item
    }

    test("RoutineItem preserves all day-of-week values") {
        DayOfWeek.entries.forEach { day ->
            val item = RoutineItem(
                title = "Class",
                dayOfWeek = day,
                startTime = LocalTime(8, 0),
                endTime = LocalTime(9, 0),
                startDate = LocalDate(2026, 1, 1),
                endDate = LocalDate(2026, 5, 1)
            )
            json.decodeFromString<RoutineItem>(json.encodeToString(item)).dayOfWeek shouldBe day
        }
    }

    test("RoutineItem preserves midnight start time") {
        val item = RoutineItem(
            title = "Night Class",
            dayOfWeek = DayOfWeek.FRIDAY,
            startTime = LocalTime(0, 0),
            endTime = LocalTime(0, 59),
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2026, 1, 31)
        )
        val rt = json.decodeFromString<RoutineItem>(json.encodeToString(item))
        rt.startTime shouldBe LocalTime(0, 0)
        rt.endTime shouldBe LocalTime(0, 59)
    }

    test("RoutineItem preserves end-of-day time") {
        val item = RoutineItem(
            title = "Late Class",
            dayOfWeek = DayOfWeek.THURSDAY,
            startTime = LocalTime(23, 0),
            endTime = LocalTime(23, 59),
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2026, 12, 31)
        )
        val rt = json.decodeFromString<RoutineItem>(json.encodeToString(item))
        rt.startTime shouldBe LocalTime(23, 0)
        rt.endTime shouldBe LocalTime(23, 59)
    }

    // ── LocalDateSerializer ───────────────────────────────────────────────────

    test("LocalDateSerializer round-trips a date") {
        val date = LocalDate(2026, 9, 15)
        val encoded = Json.encodeToString(LocalDateSerializer, date)
        encoded shouldBe "\"2026-09-15\""
        Json.decodeFromString(LocalDateSerializer, encoded) shouldBe date
    }

    test("LocalDateSerializer handles leap day") {
        val date = LocalDate(2024, 2, 29)
        val rt = Json.decodeFromString(LocalDateSerializer, Json.encodeToString(LocalDateSerializer, date))
        rt shouldBe date
    }

    // ── LocalTimeSerializer ───────────────────────────────────────────────────

    test("LocalTimeSerializer round-trips a time") {
        val time = LocalTime(14, 30)
        val encoded = Json.encodeToString(LocalTimeSerializer, time)
        Json.decodeFromString(LocalTimeSerializer, encoded) shouldBe time
    }

    test("LocalTimeSerializer preserves seconds") {
        val time = LocalTime(10, 15, 45)
        val rt = Json.decodeFromString(LocalTimeSerializer, Json.encodeToString(LocalTimeSerializer, time))
        rt shouldBe time
    }

    test("LocalTimeSerializer handles midnight") {
        val time = LocalTime(0, 0, 0)
        val rt = Json.decodeFromString(LocalTimeSerializer, Json.encodeToString(LocalTimeSerializer, time))
        rt shouldBe time
    }
})
