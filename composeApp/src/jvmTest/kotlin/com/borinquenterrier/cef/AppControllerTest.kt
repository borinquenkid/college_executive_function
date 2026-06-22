package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.coroutines.GlobalScope

class AppControllerTest : FunSpec({

    lateinit var container: DependencyContainer
    lateinit var sourceRepository: SqlDelightSourceRepository
    lateinit var localRepository: SqlDelightLocalCalendarRepository
    lateinit var calendarAgent: CalendarAgent
    lateinit var eventAgent: EventAgent
    lateinit var aiService: AIService
    lateinit var contextAgent: ContextAgent
    lateinit var logger: Logger
    lateinit var mockSourceLoader: SourceLoader
    lateinit var mockSourceAdder: SourceAdder
    lateinit var controller: AppController

    fun makeEvent(title: String, id: String? = title): TimeEvent = TimeEvent(
        id = id,
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.STUDY_BLOCK,
        date = LocalDate(2026, 9, 1),
        startTime = LocalTime(9, 0),
        endTime = LocalTime(10, 0)
    )

    beforeEach {
        sourceRepository = mockk(relaxed = true)
        localRepository = mockk(relaxed = true)
        calendarAgent = mockk(relaxed = true)
        eventAgent = mockk(relaxed = true)
        aiService = mockk(relaxed = true)
        contextAgent = mockk(relaxed = true)
        logger = mockk(relaxed = true)

        container = mockk(relaxed = true)
        every { container.sourceRepository } returns sourceRepository
        every { container.localRepository } returns localRepository
        every { container.calendarAgent } returns calendarAgent
        every { container.eventAgent } returns eventAgent
        every { container.aiService } returns aiService
        every { container.contextAgent } returns contextAgent
        every { container.logger } returns logger

        // Set up real SourceManager, SourceSelector, and SourceDeleter for proper state management
        mockSourceLoader = mockk(relaxed = true)
        mockSourceAdder = mockk(relaxed = true)
        val realSourceSelector = SourceSelector()

        coEvery { mockSourceLoader.loadSources() } returns emptyList()

        // Use real SourceDeleter so it actually calls the repositories
        val realSourceDeleter = SourceDeleter(
            sourceRepository,
            calendarAgent,
            logger,
            GlobalScope
        )

        val realSourceManager = SourceManager(
            mockSourceLoader,
            mockSourceAdder,
            realSourceDeleter,
            realSourceSelector,
            GlobalScope
        )
        every { container.sourceManager } returns realSourceManager

        // loadSources is called from init; stub to return empty
        coEvery { sourceRepository.getAllSources() } returns emptyList()
        coEvery { sourceRepository.getFragmentsForSource(any()) } returns emptyList()

        controller = AppController(container)
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    test("navigateTo updates currentScreen state") {
        controller.currentScreen.value shouldBe AppScreen.Home

        controller.navigateTo(AppScreen.Calendar)
        controller.currentScreen.value shouldBe AppScreen.Calendar

        controller.navigateTo(AppScreen.Settings)
        controller.currentScreen.value shouldBe AppScreen.Settings
    }

    // ── Event list ────────────────────────────────────────────────────────────

    test("addEvents replaces aiGeneratedEvents with the new batch") {
        val e1 = makeEvent("E1")
        val e2 = makeEvent("E2")

        controller.addEvents(listOf(e1))
        controller.aiGeneratedEvents.value.size shouldBe 1

        controller.addEvents(listOf(e2))
        // Must replace, not append — prevents calendar duplicates after re-generate or push
        controller.aiGeneratedEvents.value.size shouldBe 1
        controller.aiGeneratedEvents.value[0].title shouldBe "E2"
    }

    test("clearEvents empties aiGeneratedEvents") {
        controller.addEvents(listOf(makeEvent("X")))
        controller.clearEvents()
        controller.aiGeneratedEvents.value shouldBe emptyList()
    }

    // ── Source management ─────────────────────────────────────────────────────

    test("addSource adds to sourceItems and sets selectedSource when previously null") {
        val source = SourceItem("notes.txt", emptyList(), SourceCategory.READING_MATERIAL)
        every { aiService.isConfigured() } returns false

        controller.addSource(source)

        controller.sourceItems.value.any { it.title == "notes.txt" } shouldBe true
        controller.selectedSource.value shouldNotBe null
        controller.selectedSource.value?.title shouldBe "notes.txt"
    }

    test("addSource does not overwrite selectedSource when one already exists") {
        val first = SourceItem("first.txt", emptyList(), SourceCategory.SYLLABUS)
        val second = SourceItem("second.txt", emptyList(), SourceCategory.READING_MATERIAL)
        every { aiService.isConfigured() } returns false

        controller.addSource(first)
        controller.addSource(second)

        controller.selectedSource.value?.title shouldBe "first.txt"
    }

    test("deleteSource removes item from sourceItems and deselects it") {
        val source = SourceItem("syllabus.pdf", emptyList(), SourceCategory.SYLLABUS)
        every { aiService.isConfigured() } returns false
        coEvery { calendarAgent.getEvents(any()) } returns emptyList()

        controller.addSource(source)
        controller.sourceItems.value.any { it.title == "syllabus.pdf" } shouldBe true

        controller.deleteSource(source)
        kotlinx.coroutines.delay(300)

        coVerify(atLeast = 1) { sourceRepository.deleteSource("syllabus.pdf") }
        controller.sourceItems.value.any { it.title == "syllabus.pdf" } shouldBe false
        controller.selectedSource.value shouldBe null
    }

    test("deleteSource deletes matching events by id prefix via calendarAgent") {
        val source = SourceItem("cs101_syllabus", emptyList(), SourceCategory.SYLLABUS)
        every { aiService.isConfigured() } returns false

        val matchingEvent = makeEvent("cs101_syllabus_midterm", id = "cs101_syllabus_midterm")
        val otherEvent = makeEvent("unrelated_event", id = "unrelated_event")
        coEvery { calendarAgent.getEvents(any()) } returns listOf(matchingEvent, otherEvent)

        controller.addSource(source)
        controller.deleteSource(source)
        kotlinx.coroutines.delay(300)

        coVerify(exactly = 1) { calendarAgent.deleteEvent("cs101_syllabus_midterm", "default") }
        coVerify(exactly = 0) { calendarAgent.deleteEvent("unrelated_event", "default") }
    }

    test("deleteSource calls calendarAgent.synchronize after cleanup") {
        val source = SourceItem("notes.txt", emptyList(), SourceCategory.READING_MATERIAL)
        every { aiService.isConfigured() } returns false
        coEvery { calendarAgent.getEvents(any()) } returns emptyList()

        controller.addSource(source)
        controller.deleteSource(source)
        kotlinx.coroutines.delay(300)

        coVerify(atLeast = 1) { calendarAgent.synchronize("default") }
    }

    // ── Chat ──────────────────────────────────────────────────────────────────

    test("addChatMessage appends to chatMessages") {
        val initialCount = controller.chatMessages.value.size
        controller.addChatMessage(ChatMessage("User", "Hello!"))
        controller.chatMessages.value.size shouldBe initialCount + 1
        controller.chatMessages.value.last().content shouldBe "Hello!"
    }

    // ── loadSources ───────────────────────────────────────────────────────────

    test("loadSources updates sourceItems with what the loader returns") {
        val source = SourceItem("lecture.pdf", emptyList(), SourceCategory.SYLLABUS)
        coEvery { mockSourceLoader.loadSources() } returns listOf(source)

        controller.loadSources()
        delay(300)

        controller.sourceItems.value.any { it.title == "lecture.pdf" } shouldBe true
    }

    // ── resetForDemo ──────────────────────────────────────────────────────────

    test("resetForDemo resets the calendar and clears the event agent") {
        controller.resetForDemo()
        delay(300)

        coVerify { calendarAgent.resetCalendar() }
        coVerify { eventAgent.clear() }
    }

    test("resetForDemo clears AI-generated events shown in the UI") {
        controller.addEvents(listOf(makeEvent("Exam")))
        controller.aiGeneratedEvents.value.size shouldBe 1

        controller.resetForDemo()
        delay(300)

        controller.aiGeneratedEvents.value shouldBe emptyList()
    }

    // ── reanalyzeSource ───────────────────────────────────────────────────────

    test("reanalyzeSource keeps the source in sourceItems") {
        val source = SourceItem("notes.txt", emptyList(), SourceCategory.READING_MATERIAL)
        controller.addSource(source)
        controller.reanalyzeSource(source)
        controller.sourceItems.value.any { it.title == "notes.txt" } shouldBe true
    }

    // ── selectSource ──────────────────────────────────────────────────────────

    test("selectSource null clears selectedSource") {
        val source = SourceItem("notes.txt", emptyList(), SourceCategory.READING_MATERIAL)
        controller.addSource(source)
        controller.selectSource(null)
        controller.selectedSource.value shouldBe null
    }

    test("selectSource switches selection to the given source") {
        val a = SourceItem("a.pdf", emptyList(), SourceCategory.SYLLABUS)
        val b = SourceItem("b.pdf", emptyList(), SourceCategory.READING_MATERIAL)
        controller.addSource(a)
        controller.addSource(b)
        controller.selectSource(b)
        controller.selectedSource.value?.title shouldBe "b.pdf"
    }

    // ── launchInScope ─────────────────────────────────────────────────────────

    test("launchInScope executes the given block in the controller scope") {
        var ran = false
        controller.launchInScope { ran = true }
        delay(100)
        ran shouldBe true
    }

    // ── screen listener ───────────────────────────────────────────────────────

    test("setScreenListener receives the current screen immediately on registration") {
        val received = mutableListOf<AppScreen>()
        controller.setScreenListener { received.add(it) }
        received.size shouldBe 1
        received.first() shouldBe AppScreen.Home
    }

    test("setScreenListener is invoked on every subsequent navigation change") {
        val received = mutableListOf<AppScreen>()
        controller.setScreenListener { received.add(it) }
        controller.navigateTo(AppScreen.Settings)
        delay(200)
        received.last() shouldBe AppScreen.Settings
    }

    // ── events listener ───────────────────────────────────────────────────────

    test("setEventsListener receives the current (empty) event list immediately on registration") {
        val received = mutableListOf<List<Event>>()
        controller.setEventsListener { received.add(it) }
        received.size shouldBe 1
        received.first() shouldBe emptyList()
    }

    test("setEventsListener is invoked when events are added") {
        val received = mutableListOf<List<Event>>()
        controller.setEventsListener { received.add(it) }
        controller.addEvents(listOf(makeEvent("Midterm")))
        delay(200)
        received.last().size shouldBe 1
    }

    // ── init: retryLocalOnly ──────────────────────────────────────────────────

    test("init triggers retryLocalOnly once when isLinked transitions to true") {
        val linkedFlow = MutableStateFlow(false)
        val tokenRepo = mockk<GoogleTokenRepository>(relaxed = true)
        every { tokenRepo.isLinked } returns linkedFlow
        every { container.tokenRepository } returns tokenRepo
        AppController(container)

        linkedFlow.value = true
        delay(300)

        coVerify(exactly = 1) { calendarAgent.retryLocalOnly() }
    }

    test("init triggers retryLocalOnly immediately when isLinked is already true") {
        val linkedFlow = MutableStateFlow(true)
        val tokenRepo = mockk<GoogleTokenRepository>(relaxed = true)
        every { tokenRepo.isLinked } returns linkedFlow
        every { container.tokenRepository } returns tokenRepo
        AppController(container)

        delay(300)

        coVerify(exactly = 1) { calendarAgent.retryLocalOnly() }
    }

    test("init triggers retryLocalOnly exactly once even if isLinked emits true multiple times") {
        val linkedFlow = MutableStateFlow(false)
        val tokenRepo = mockk<GoogleTokenRepository>(relaxed = true)
        every { tokenRepo.isLinked } returns linkedFlow
        every { container.tokenRepository } returns tokenRepo
        AppController(container)

        linkedFlow.value = true
        delay(100)
        linkedFlow.value = false
        linkedFlow.value = true
        delay(300)

        coVerify(exactly = 1) { calendarAgent.retryLocalOnly() }
    }
})
