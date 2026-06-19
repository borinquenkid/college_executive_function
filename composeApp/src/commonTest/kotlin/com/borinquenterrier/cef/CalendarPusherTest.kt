package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

class CalendarPusherTest : FunSpec({

    val today = LocalDate(2026, 8, 1)
    val tomorrow = LocalDate(2026, 8, 2)
    val yesterday = LocalDate(2026, 7, 31)

    // Noon UTC on today so todayIn(any timezone) resolves to `today`
    val fixedClock = object : Clock {
        override fun now(): Instant =
            Instant.fromEpochMilliseconds(
                today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() + 12 * 3600_000L
            )
    }

    fun dayEvent(title: String, date: LocalDate, cat: AcademicCategory = AcademicCategory.DEADLINE) =
        DayEvent(title = title, source = EventSource.AI_GENERATED, category = cat, date = date)

    fun unresolved(title: String) = ConflictResolver.UnresolvedConflict(
        title = title, date = tomorrow, reason = "test reason", requiresProfessorApproval = true
    )

    fun calendarNotFound() = CalendarNotFoundException("calendar-id", "Calendar gone")

    fun makePusher(
        pushResolver: CalendarPushResolver = mockk(relaxed = true),
        repository: CalendarAgent = mockk(relaxed = true),
        onIsLoading: (Boolean) -> Unit = {},
        onStatus: (String) -> Unit = {},
        onGeneratedEvents: (List<Event>) -> Unit = {},
        onUnresolvedConflicts: (List<ConflictResolver.UnresolvedConflict>) -> Unit = {},
        onErrorState: (AgentError) -> Unit = {}
    ) = CalendarPusher(
        pushResolver = pushResolver,
        repository = repository,
        logger = null,
        clock = fixedClock,
        onIsLoading = onIsLoading,
        onStatus = onStatus,
        onGeneratedEvents = onGeneratedEvents,
        onUnresolvedConflicts = onUnresolvedConflicts,
        onErrorState = onErrorState
    )

    // ── Empty input guards ────────────────────────────────────────────────────

    test("empty allEvents returns empty without touching loading state") {
        var loadingCalled = false
        val result = makePusher(onIsLoading = { loadingCalled = true }).push(emptyList(), "default")
        result.shouldBeEmpty()
        loadingCalled shouldBe false
    }

    test("all past events clears generated events and returns empty") {
        val statuses = mutableListOf<String>()
        val generated = mutableListOf<List<Event>>()
        val result = makePusher(
            onStatus = { statuses += it },
            onGeneratedEvents = { generated += listOf(it) }
        ).push(listOf(dayEvent("Past", yesterday)), "default")

        result.shouldBeEmpty()
        statuses.last() shouldContain "past events skipped"
        generated.last().shouldBeEmpty()
    }

    // ── Success path ──────────────────────────────────────────────────────────

    test("success sets success status and clears generated events") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val resolver = mockk<CalendarPushResolver>(relaxed = true)
        coEvery { repo.getEvents("default") } returns emptyList()
        coEvery { resolver.resolveAndPush(any(), any(), any()) } returns
            PushOutcome(successCount = 1, conflicts = emptyList())

        val statuses = mutableListOf<String>()
        val generated = mutableListOf<List<Event>>()

        val result = makePusher(
            pushResolver = resolver, repository = repo,
            onStatus = { statuses += it }, onGeneratedEvents = { generated += listOf(it) }
        ).push(listOf(dayEvent("Essay", tomorrow)), "default")

        result.shouldBeEmpty()
        statuses.last() shouldContain "Success"
        generated.last().shouldBeEmpty()
    }

    test("conflicts returns conflict list and sets conflict status") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val resolver = mockk<CalendarPushResolver>(relaxed = true)
        val conflict = dayEvent("Conflict", tomorrow)
        coEvery { repo.getEvents("default") } returns emptyList()
        coEvery { resolver.resolveAndPush(any(), any(), any()) } returns
            PushOutcome(successCount = 0, conflicts = listOf(conflict))

        val statuses = mutableListOf<String>()
        val generated = mutableListOf<List<Event>>()

        val result = makePusher(
            pushResolver = resolver, repository = repo,
            onStatus = { statuses += it }, onGeneratedEvents = { generated += listOf(it) }
        ).push(listOf(dayEvent("Essay", tomorrow)), "default")

        result shouldHaveSize 1
        statuses.last() shouldContain "conflicts"
        generated.last() shouldHaveSize 1
    }

    test("unresolvable conflicts calls onUnresolvedConflicts") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val resolver = mockk<CalendarPushResolver>(relaxed = true)
        coEvery { repo.getEvents("default") } returns emptyList()
        coEvery { resolver.resolveAndPush(any(), any(), any()) } returns
            PushOutcome(successCount = 0, conflicts = emptyList(), unresolvableConflicts = listOf(unresolved("Quiz")))

        val unresolvedCapture = mutableListOf<List<ConflictResolver.UnresolvedConflict>>()
        makePusher(
            pushResolver = resolver, repository = repo,
            onUnresolvedConflicts = { unresolvedCapture += listOf(it) }
        ).push(listOf(dayEvent("Quiz", tomorrow)), "default")

        unresolvedCapture.last() shouldHaveSize 1
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    test("CalendarNotFoundException calls onErrorState with GenericError and sets status") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val resolver = mockk<CalendarPushResolver>(relaxed = true)
        coEvery { repo.getEvents("default") } returns emptyList()
        coEvery { resolver.resolveAndPush(any(), any(), any()) } throws calendarNotFound()

        val statuses = mutableListOf<String>()
        val errors = mutableListOf<AgentError>()
        makePusher(
            pushResolver = resolver, repository = repo,
            onStatus = { statuses += it }, onErrorState = { errors += it }
        ).push(listOf(dayEvent("Essay", tomorrow)), "default")

        statuses.last() shouldContain "Calendar gone"
        errors.last() shouldBe AgentError.GenericError("Calendar gone")
    }

    test("generic exception sets Sync Error status") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val resolver = mockk<CalendarPushResolver>(relaxed = true)
        coEvery { repo.getEvents("default") } returns emptyList()
        coEvery { resolver.resolveAndPush(any(), any(), any()) } throws Exception("Network fail")

        val statuses = mutableListOf<String>()
        makePusher(
            pushResolver = resolver, repository = repo,
            onStatus = { statuses += it }
        ).push(listOf(dayEvent("Essay", tomorrow)), "default")

        statuses.last() shouldContain "Sync Error"
    }

    // ── Loading lifecycle ─────────────────────────────────────────────────────

    test("onIsLoading called true then false on success") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val resolver = mockk<CalendarPushResolver>(relaxed = true)
        coEvery { repo.getEvents("default") } returns emptyList()
        coEvery { resolver.resolveAndPush(any(), any(), any()) } returns
            PushOutcome(successCount = 1, conflicts = emptyList())

        val states = mutableListOf<Boolean>()
        makePusher(
            pushResolver = resolver, repository = repo,
            onIsLoading = { states += it }
        ).push(listOf(dayEvent("Essay", tomorrow)), "default")

        states shouldBe listOf(true, false)
    }

    test("onIsLoading reset to false even when exception is thrown") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val resolver = mockk<CalendarPushResolver>(relaxed = true)
        coEvery { repo.getEvents("default") } returns emptyList()
        coEvery { resolver.resolveAndPush(any(), any(), any()) } throws Exception("boom")

        val states = mutableListOf<Boolean>()
        makePusher(
            pushResolver = resolver, repository = repo,
            onIsLoading = { states += it }
        ).push(listOf(dayEvent("Essay", tomorrow)), "default")

        states.last() shouldBe false
    }

    // ── Skipped-count note ────────────────────────────────────────────────────

    test("skipped note included in status when past and future events are mixed") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val resolver = mockk<CalendarPushResolver>(relaxed = true)
        coEvery { repo.getEvents("default") } returns emptyList()
        coEvery { resolver.resolveAndPush(any(), any(), any()) } returns
            PushOutcome(successCount = 1, conflicts = emptyList())

        val statuses = mutableListOf<String>()
        makePusher(
            pushResolver = resolver, repository = repo,
            onStatus = { statuses += it }
        ).push(listOf(dayEvent("Past", yesterday), dayEvent("Future", tomorrow)), "default")

        statuses.last() shouldContain "past events skipped"
    }
})
