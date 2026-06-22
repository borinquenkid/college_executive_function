package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

class EventReschedulerTest : FunSpec({

    val today = LocalDate(2026, 8, 1)
    val fixedClock = object : Clock {
        override fun now(): Instant =
            Instant.fromEpochMilliseconds(
                today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() + 12 * 3600_000L
            )
    }

    fun dayEvent(title: String, date: LocalDate = LocalDate(2026, 7, 20), id: String? = "evt-1") =
        DayEvent(title = title, source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE, date = date, id = id)

    fun timeEvent(title: String, id: String? = "evt-2") = TimeEvent(
        id = id, title = title, source = EventSource.AI_GENERATED,
        category = AcademicCategory.CLASS,
        startTime = LocalTime(9, 0), endTime = LocalTime(10, 0),
        date = LocalDate(2026, 7, 20)
    )

    fun makeRescheduler(
        pushResolver: CalendarPushResolver = mockk(relaxed = true),
        repository: CalendarAgent = mockk(relaxed = true)
    ) = EventRescheduler(pushResolver, repository, fixedClock)

    // ── Success path ──────────────────────────────────────────────────────────

    test("successful reschedule returns success message with event title") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val resolver = mockk<CalendarPushResolver>(relaxed = true)
        coEvery { repo.getEvents("default") } returns emptyList()
        coEvery { resolver.resolveAndReschedule(any(), any(), any()) } returns true

        val result = makeRescheduler(resolver, repo).run(dayEvent("Essay"), "default")

        result shouldContain "Rescheduled"
        result shouldContain "Essay"
    }

    test("conflict returns 'Cannot reschedule' message") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val resolver = mockk<CalendarPushResolver>(relaxed = true)
        coEvery { repo.getEvents("default") } returns emptyList()
        coEvery { resolver.resolveAndReschedule(any(), any(), any()) } returns false

        val result = makeRescheduler(resolver, repo).run(dayEvent("Essay"), "default")

        result shouldContain "Cannot reschedule"
    }

    // ── Date is set to today ──────────────────────────────────────────────────

    test("DayEvent date is updated to today before resolving") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val resolver = mockk<CalendarPushResolver>(relaxed = true)
        coEvery { repo.getEvents("default") } returns emptyList()
        coEvery { resolver.resolveAndReschedule(any(), any(), any()) } returns true

        makeRescheduler(resolver, repo).run(dayEvent("Essay", date = LocalDate(2026, 7, 1)), "default")

        coVerify {
            resolver.resolveAndReschedule(
                match { it is DayEvent && it.date == today },
                any(), any()
            )
        }
    }

    test("TimeEvent date is updated to today before resolving") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val resolver = mockk<CalendarPushResolver>(relaxed = true)
        coEvery { repo.getEvents("default") } returns emptyList()
        coEvery { resolver.resolveAndReschedule(any(), any(), any()) } returns true

        makeRescheduler(resolver, repo).run(timeEvent("Lecture"), "default")

        coVerify {
            resolver.resolveAndReschedule(
                match { it is TimeEvent && it.date == today },
                any(), any()
            )
        }
    }

    // ── Existing event excluded from candidates ───────────────────────────────

    test("existing event with same id is removed from the candidate list") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val resolver = mockk<CalendarPushResolver>(relaxed = true)
        val same = dayEvent("Essay", id = "evt-1")
        val other = dayEvent("Quiz", date = LocalDate(2026, 9, 5), id = "evt-2")
        coEvery { repo.getEvents("default") } returns listOf(same, other)
        coEvery { resolver.resolveAndReschedule(any(), any(), any()) } returns true

        makeRescheduler(resolver, repo).run(same, "default")

        coVerify {
            resolver.resolveAndReschedule(
                any(),
                match { existing -> existing.none { it.id == "evt-1" } },
                any()
            )
        }
    }
})
