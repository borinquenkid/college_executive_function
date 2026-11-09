package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate

class DeliverableExtractorTest : FunSpec({

    val calendarId = "default"

    fun dayEvent(title: String) = DayEvent(
        title = title, source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE, date = LocalDate(2026, 9, 1)
    )

    fun source(title: String = "Syllabus", cat: SourceCategory = SourceCategory.SYLLABUS) =
        SourceItem(title = title, fragments = emptyList(), category = cat)

    fun makeExtractor(
        service: EventGenerationService = mockk(relaxed = true),
        onProgress: (String) -> Unit = {}
    ) = DeliverableExtractor(service, onProgress)

    // ── Normal result ─────────────────────────────────────────────────────────

    test("events returned by service are in result") {
        val service = mockk<EventGenerationService>(relaxed = true)
        val events = listOf(dayEvent("Essay"), dayEvent("Quiz"))
        coEvery { service.extractDeliverables(any(), any()) } returns events

        val result = makeExtractor(service).run(source(), calendarId)

        result.events shouldHaveSize 2
        result.warning shouldBe null
        result.statusMessage shouldContain "2 deadlines"
    }

    // ── Contradictory source date: held back, never guessed ───────────────────

    test("an event on a weekday/date contradiction is held back, not returned for pushing") {
        val text = listOf(
            "Tue 8/25 - Paired introductions", "Thur 8/27 - Re-introductions", "Tue 9/1 - Speech 1",
            "Thur 11/19 - Rehearsal day; Online Speech due by 5 pm on Friday, Nov. 21"
        ).joinToString("\n")
        val src = SourceItem("SPMS", listOf(SourceFragment(text)), SourceCategory.SYLLABUS)
        val speech = DayEvent(
            title = "Online Speech due", source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE, date = LocalDate(2026, 11, 20)
        )
        val service = mockk<EventGenerationService>(relaxed = true)
        coEvery { service.extractDeliverables(any(), any()) } returns listOf(dayEvent("Speech 1"), speech)

        val result = makeExtractor(service).run(src, calendarId)

        result.events.map { it.title } shouldBe listOf("Speech 1")
        result.needsResolution.map { it.event.title } shouldBe listOf("Online Speech due")
        result.statusMessage shouldContain "1 need your date"
        result.warning shouldBe null
    }

    // ── Warning: empty result ─────────────────────────────────────────────────

    test("empty events produces warning about no events found") {
        val service = mockk<EventGenerationService>(relaxed = true)
        coEvery { service.extractDeliverables(any(), any()) } returns emptyList()

        val result = makeExtractor(service).run(source("ComplianceSyllabus"), calendarId)

        result.events.shouldBeEmpty()
        result.warning shouldNotBe null
        result.warning!! shouldContain "No events found"
        result.warning!! shouldContain "ComplianceSyllabus"
    }

    // ── Warning: few events + OTHER category ─────────────────────────────────

    test("fewer than 5 events with SourceCategory.OTHER produces low-count warning") {
        val service = mockk<EventGenerationService>(relaxed = true)
        coEvery { service.extractDeliverables(any(), any()) } returns listOf(dayEvent("Essay"), dayEvent("Quiz"))

        val result = makeExtractor(service).run(source(cat = SourceCategory.OTHER), calendarId)

        result.warning shouldNotBe null
        result.warning!! shouldContain "Only 2 event(s)"
    }

    test("fewer than 5 events with SYLLABUS category produces no warning") {
        val service = mockk<EventGenerationService>(relaxed = true)
        coEvery { service.extractDeliverables(any(), any()) } returns listOf(dayEvent("Essay"))

        val result = makeExtractor(service).run(source(cat = SourceCategory.SYLLABUS), calendarId)

        result.warning shouldBe null
    }

    test("5 or more events with OTHER category produces no warning") {
        val service = mockk<EventGenerationService>(relaxed = true)
        coEvery { service.extractDeliverables(any(), any()) } returns
            List(5) { dayEvent("Event $it") }

        val result = makeExtractor(service).run(source(cat = SourceCategory.OTHER), calendarId)

        result.warning shouldBe null
    }

    // ── Progress callback ─────────────────────────────────────────────────────

    test("onProgress callback receives messages from service") {
        val service = mockk<EventGenerationService>(relaxed = true)
        val progressMessages = mutableListOf<String>()
        coEvery { service.extractDeliverables(any(), captureLambda()) } answers {
            val cb = secondArg<(String) -> Unit>()
            cb("Batch 1 of 2")
            cb("Batch 2 of 2")
            listOf(dayEvent("Essay"))
        }

        makeExtractor(service, onProgress = { progressMessages += it }).run(source(), calendarId)

        progressMessages shouldBe listOf("Batch 1 of 2", "Batch 2 of 2")
    }
})
