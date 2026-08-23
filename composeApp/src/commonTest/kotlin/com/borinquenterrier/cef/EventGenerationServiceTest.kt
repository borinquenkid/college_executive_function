package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class EventGenerationServiceTest : FunSpec({

    fun fragment(text: String = "PSYCH 101 Syllabus") =
        SourceFragment(text = text, type = SourceType.TEXT)

    fun syllabusSource(text: String = "PSYCH 101 Syllabus", fragments: List<SourceFragment>? = null) =
        SourceItem(
            title = "PSYCH 101",
            fragments = fragments ?: listOf(fragment(text)),
            category = SourceCategory.SYLLABUS
        )

    fun calendarSource() =
        SourceItem(
            title = "Academic Calendar",
            fragments = listOf(fragment("Academic calendar 2025")),
            category = SourceCategory.CALENDAR
        )

    fun dayEvent(title: String, date: LocalDate = LocalDate(2025, 10, 1), warning: String? = null) =
        DayEvent(title = title, source = EventSource.AI_GENERATED, category = AcademicCategory.DEADLINE, date = date, warning = warning)

    // ── extractDeliverables ─────────────────────────────────────────────────

    test("extractDeliverables returns normalized events from AI") {
        val aiService = mockk<AIService>(relaxed = true)
        val normalization = NormalizationService()
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        coEvery { auditor.audit(any()) } returns emptyList()
        coEvery { aiService.generateCalendarEvents(any()) } returns listOf(
            dayEvent("HW1"), dayEvent("HW2")
        )
        val service = EventGenerationService(aiService, normalization, auditor)

        val events = service.extractDeliverables(syllabusSource())
        events shouldHaveSize 2
    }

    test("extractDeliverables returns empty list when AI returns nothing") {
        val aiService = mockk<AIService>(relaxed = true)
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        coEvery { auditor.audit(any()) } returns emptyList()
        coEvery { aiService.generateCalendarEvents(any()) } returns emptyList()
        val service = EventGenerationService(aiService, NormalizationService(), auditor)

        service.extractDeliverables(syllabusSource()).shouldBeEmpty()
    }

    test("extractDeliverables assigns deterministic IDs so duplicates across runs collapse") {
        val aiService = mockk<AIService>(relaxed = true)
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        coEvery { auditor.audit(any()) } returns emptyList()
        val event = dayEvent("Midterm")
        coEvery { aiService.generateCalendarEvents(any()) } returns listOf(event)
        val service = EventGenerationService(aiService, NormalizationService(), auditor)

        val run1 = service.extractDeliverables(syllabusSource())
        val run2 = service.extractDeliverables(syllabusSource())
        run1[0].id shouldBe run2[0].id
        run1[0].id shouldNotBe null
    }

    test("extractDeliverables deduplicates events with the same title-date-time fingerprint") {
        val aiService = mockk<AIService>(relaxed = true)
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        coEvery { auditor.audit(any()) } returns emptyList()
        val duplicate = dayEvent("HW1")
        coEvery { aiService.generateCalendarEvents(any()) } returns listOf(duplicate, duplicate)
        val service = EventGenerationService(aiService, NormalizationService(), auditor)

        val events = service.extractDeliverables(syllabusSource())
        events shouldHaveSize 1
    }

    test("extractDeliverables appends audit warning to all events for SYLLABUS sources") {
        val aiService = mockk<AIService>(relaxed = true)
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        coEvery { auditor.audit(any()) } returns listOf("No office hours listed", "Grading policy unclear")
        coEvery { aiService.generateCalendarEvents(any()) } returns listOf(dayEvent("HW1"))
        val service = EventGenerationService(aiService, NormalizationService(), auditor)

        val events = service.extractDeliverables(syllabusSource())
        events[0].warning shouldContain "No office hours listed"
        events[0].warning shouldContain "Grading policy unclear"
    }

    test("extractDeliverables merges audit warning with existing event warning") {
        val aiService = mockk<AIService>(relaxed = true)
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        coEvery { auditor.audit(any()) } returns listOf("Audit note")
        coEvery { aiService.generateCalendarEvents(any()) } returns listOf(
            dayEvent("HW1", warning = "Date computed from week anchor")
        )
        val service = EventGenerationService(aiService, NormalizationService(), auditor)

        val events = service.extractDeliverables(syllabusSource())
        events[0].warning shouldContain "Date computed from week anchor"
        events[0].warning shouldContain "Audit note"
    }

    test("extractDeliverables does not audit non-SYLLABUS sources") {
        val aiService = mockk<AIService>(relaxed = true)
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        coEvery { aiService.generateCalendarEvents(any()) } returns listOf(dayEvent("Holiday"))
        val service = EventGenerationService(aiService, NormalizationService(), auditor)

        val events = service.extractDeliverables(calendarSource())
        events shouldHaveSize 1
        // auditor.audit should never have been called
        io.mockk.coVerify(exactly = 0) { auditor.audit(any()) }
    }

    // ── generateStudyPlan ───────────────────────────────────────────────────

    test("generateStudyPlan grounds study blocks against a source-dated deliverable") {
        val aiService = mockk<AIService>(relaxed = true)
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        val midterm = DayEvent(
            title = "Midterm", source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE, date = LocalDate(2025, 10, 12)
        )
        val studyBlock = TimeEvent(
            title = "Study: Midterm", source = EventSource.AI_GENERATED,
            date = LocalDate(2025, 10, 10), startTime = LocalTime(14, 0), endTime = LocalTime(16, 0),
            category = AcademicCategory.STUDY_BLOCK
        )
        coEvery { aiService.generateStudyPlan(any(), any(), any()) } returns listOf(midterm, studyBlock)
        val service = EventGenerationService(aiService, NormalizationService(), auditor)

        // Source references the deliverable's date so it grounds; the block then anchors to it.
        val result = service.generateStudyPlan(syllabusSource("PSYCH 101. Midterm October 12, 2025."), emptyList())
        (result.grounded.any { it.title == "Study: Midterm" }) shouldBe true
        result.needsResolution.shouldBeEmpty()
    }

    test("generateStudyPlan passes existing events as schedule context to AI") {
        val aiService = mockk<AIService>(relaxed = true)
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        val scheduleSlot = io.mockk.slot<String>()
        coEvery { aiService.generateStudyPlan(any(), capture(scheduleSlot), any()) } returns emptyList()

        val service = EventGenerationService(aiService, NormalizationService(), auditor)
        val existing = listOf(dayEvent("Midterm", LocalDate(2025, 10, 15)))
        service.generateStudyPlan(syllabusSource(), existing)

        io.mockk.coVerify { aiService.generateStudyPlan(any(), any(), any()) }
        scheduleSlot.isCaptured shouldBe true
        scheduleSlot.captured.shouldContain("Midterm")
    }

    test("generateStudyPlan returns empty when AI returns nothing") {
        val aiService = mockk<AIService>(relaxed = true)
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        coEvery { aiService.generateStudyPlan(any(), any(), any()) } returns emptyList()
        val service = EventGenerationService(aiService, NormalizationService(), auditor)

        service.generateStudyPlan(syllabusSource(), emptyList()).grounded.shouldBeEmpty()
    }

    // ── batch loop ──────────────────────────────────────────────────────────

    test("extractDeliverables calls AI once per batch when TEXT fragments exceed BATCH_SIZE") {
        val aiService = mockk<AIService>()
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        coEvery { auditor.audit(any()) } returns emptyList()
        // 4 fragments → 2 batches; return distinct events so merging is verifiable
        coEvery { aiService.generateCalendarEvents(any()) } returnsMany listOf(
            listOf(dayEvent("HW1"), dayEvent("HW2")),
            listOf(dayEvent("HW3"))
        )
        val fragments = (1..4).map { i ->
            SourceFragment("page $i content", pageNumber = i, type = SourceType.TEXT)
        }
        val service = EventGenerationService(aiService, NormalizationService(), auditor)

        val events = service.extractDeliverables(syllabusSource(fragments = fragments))

        coVerify(exactly = 2) { aiService.generateCalendarEvents(any()) }
        events shouldHaveSize 3
    }

    test("extractDeliverables calls AI once when TEXT fragments do not exceed BATCH_SIZE") {
        val aiService = mockk<AIService>()
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        coEvery { auditor.audit(any()) } returns emptyList()
        coEvery { aiService.generateCalendarEvents(any()) } returns listOf(dayEvent("Midterm"))
        // 3 fragments == BATCH_SIZE, not >, so no split
        val fragments = (1..3).map { i ->
            SourceFragment("page $i", pageNumber = i, type = SourceType.TEXT)
        }
        val service = EventGenerationService(aiService, NormalizationService(), auditor)

        service.extractDeliverables(syllabusSource(fragments = fragments))

        coVerify(exactly = 1) { aiService.generateCalendarEvents(any()) }
    }

    test("extractDeliverables calls AI once for non-TEXT fragments regardless of count") {
        val aiService = mockk<AIService>()
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        coEvery { auditor.audit(any()) } returns emptyList()
        coEvery { aiService.generateCalendarEvents(any()) } returns listOf(dayEvent("Lecture"))
        // 5 CALENDAR fragments — batching only applies to TEXT
        val fragments = (1..5).map { i ->
            SourceFragment("event $i", pageNumber = i, type = SourceType.CALENDAR)
        }
        val service = EventGenerationService(aiService, NormalizationService(), auditor)

        service.extractDeliverables(syllabusSource(fragments = fragments))

        coVerify(exactly = 1) { aiService.generateCalendarEvents(any()) }
    }

    // ── document-level year grounding (post-merge, not per-batch) ─────────────
    //
    // Regression coverage for a real bug found 2026-08-23: a batch containing an advising-office
    // phone number ("636-422-2000") but not the syllabus's "Fall 2026" header false-matched "2000"
    // as the batch's only "source year" and dropped 7 real 2026-dated events. Grounding must run
    // once against the full merged document text, not per batch — see GroundingGuardAIService's
    // kdoc and EventGenerationService.extractDeliverables.

    test("extractDeliverables keeps events from a batch whose own text has no year, as long as the DOCUMENT has one") {
        val aiService = mockk<AIService>()
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        coEvery { auditor.audit(any()) } returns emptyList()
        // Batch 1's fragment carries the document's year; batch 2's fragment (a weekly test
        // schedule) doesn't mention a year at all but its events are still 2026-dated.
        coEvery { aiService.generateCalendarEvents(any()) } returnsMany listOf(
            listOf(dayEvent("First Day of Class", date = LocalDate(2026, 8, 24))),
            listOf(
                dayEvent("Test 1", date = LocalDate(2026, 9, 11)),
                dayEvent("Test 2", date = LocalDate(2026, 9, 25))
            )
        )
        // BATCH_SIZE=3, OVERLAP=1 (SourceFragmentBatcher): 4 fragments → batch1=[0,1,2],
        // batch2=[2,3]. Put the year header only in fragment 0 and the test schedule only in
        // fragment 3, so batch2's own text (fragment 2 + fragment 3) never mentions a year —
        // the exact shape that reproduced the real bug.
        val fragments = listOf(
            SourceFragment("Fall 2026 Syllabus, Section 605", pageNumber = 1, type = SourceType.TEXT),
            SourceFragment("filler page 2", pageNumber = 2, type = SourceType.TEXT),
            SourceFragment("filler page 3", pageNumber = 3, type = SourceType.TEXT),
            SourceFragment("Wk 3: Test 1. Wk 5: Test 2.", pageNumber = 4, type = SourceType.TEXT)
        )
        val service = EventGenerationService(aiService, NormalizationService(), auditor)

        val events = service.extractDeliverables(syllabusSource(fragments = fragments))

        events shouldHaveSize 3
    }

    test("extractDeliverables still drops an event whose year appears nowhere in the whole document") {
        val aiService = mockk<AIService>()
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        coEvery { auditor.audit(any()) } returns emptyList()
        coEvery { aiService.generateCalendarEvents(any()) } returns listOf(
            dayEvent("Real Midterm", date = LocalDate(2026, 10, 14)),
            dayEvent("Confabulated Event", date = LocalDate(2099, 1, 1))
        )
        val service = EventGenerationService(aiService, NormalizationService(), auditor)

        val events = service.extractDeliverables(syllabusSource(text = "Fall 2026 Syllabus"))

        events shouldHaveSize 1
        events[0].title shouldBe "Real Midterm"
    }

    test("extractDeliverables emits audit then per-batch progress messages for SYLLABUS source") {
        val aiService = mockk<AIService>(relaxed = true)
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        coEvery { auditor.audit(any()) } returns emptyList()
        coEvery { aiService.generateCalendarEvents(any()) } returns emptyList()
        // 4 fragments → 2 batches; SYLLABUS → audit message fires first
        val fragments = (1..4).map { i ->
            SourceFragment("page $i", pageNumber = i, type = SourceType.TEXT)
        }
        val service = EventGenerationService(aiService, NormalizationService(), auditor)

        val messages = mutableListOf<String>()
        service.extractDeliverables(syllabusSource(fragments = fragments)) { messages.add(it) }

        messages shouldHaveSize 3
        messages[0] shouldContain "Auditing"
        messages[1] shouldContain "pages 1"
        messages[2] shouldContain "pages"
    }

    test("extractDeliverables does not emit audit progress for non-SYLLABUS source") {
        val aiService = mockk<AIService>(relaxed = true)
        val auditor = mockk<SyllabusAuditor>(relaxed = true)
        coEvery { aiService.generateCalendarEvents(any()) } returns emptyList()
        val fragments = (1..4).map { i ->
            SourceFragment("event $i", pageNumber = i, type = SourceType.TEXT)
        }
        val service = EventGenerationService(aiService, NormalizationService(), auditor)

        val messages = mutableListOf<String>()
        service.extractDeliverables(calendarSource().copy(fragments = fragments)) { messages.add(it) }

        messages.none { it.contains("Auditing") } shouldBe true
        messages shouldHaveSize 2
    }
})
