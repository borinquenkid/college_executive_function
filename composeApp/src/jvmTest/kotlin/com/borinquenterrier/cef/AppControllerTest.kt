package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.coroutines.GlobalScope

class AppControllerTest : FunSpec({

    lateinit var container: DependencyContainer
    lateinit var sourceRepository: SqlDelightSourceRepository
    lateinit var localRepository: SqlDelightLocalCalendarRepository
    lateinit var calendarAgent: CalendarAgent
    lateinit var aiService: AIService
    lateinit var contextAgent: ContextAgent
    lateinit var logger: Logger
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
        aiService = mockk(relaxed = true)
        contextAgent = mockk(relaxed = true)
        logger = mockk(relaxed = true)

        container = mockk(relaxed = true)
        every { container.sourceRepository } returns sourceRepository
        every { container.localRepository } returns localRepository
        every { container.calendarAgent } returns calendarAgent
        every { container.aiService } returns aiService
        every { container.contextAgent } returns contextAgent
        every { container.logger } returns logger

        // Set up real SourceManager, SourceSelector, and SourceDeleter for proper state management
        val mockSourceLoader = mockk<SourceLoader>(relaxed = true)
        val mockSourceAdder = mockk<SourceAdder>(relaxed = true)
        val realSourceSelector = SourceSelector()

        coEvery { mockSourceLoader.loadSources() } returns emptyList()

        // Use real SourceDeleter so it actually calls the repositories
        val realSourceDeleter = SourceDeleter(
            sourceRepository,
            localRepository,
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

    test("addEvents appends to aiGeneratedEvents") {
        val e1 = makeEvent("E1")
        val e2 = makeEvent("E2")

        controller.addEvents(listOf(e1))
        controller.aiGeneratedEvents.value.size shouldBe 1

        controller.addEvents(listOf(e2))
        controller.aiGeneratedEvents.value.size shouldBe 2
        controller.aiGeneratedEvents.value[1].title shouldBe "E2"
    }

    test("clearEvents empties aiGeneratedEvents") {
        controller.addEvents(listOf(makeEvent("X")))
        controller.clearEvents()
        controller.aiGeneratedEvents.value shouldBe emptyList()
    }

    // ── Source management ─────────────────────────────────────────────────────
    
    test("addSource adds to sourceItems and sets selectedSource when previously null") {
        kotlinx.coroutines.delay(100)
        val source = SourceItem("notes.txt", emptyList(), SourceCategory.READING_MATERIAL)
        every { aiService.isConfigured() } returns false

        controller.addSource(source)

        controller.sourceItems.value.any { it.title == "notes.txt" } shouldBe true
        controller.selectedSource.value shouldNotBe null
        controller.selectedSource.value?.title shouldBe "notes.txt"
    }

    test("addSource does not overwrite selectedSource when one already exists") {
        kotlinx.coroutines.delay(100)
        val first = SourceItem("first.txt", emptyList(), SourceCategory.SYLLABUS)
        val second = SourceItem("second.txt", emptyList(), SourceCategory.READING_MATERIAL)
        every { aiService.isConfigured() } returns false

        controller.addSource(first)
        controller.addSource(second)

        controller.selectedSource.value?.title shouldBe "first.txt"
    }

    test("deleteSource removes item from sourceItems and deselects it") {
        kotlinx.coroutines.delay(100)
        val source = SourceItem("syllabus.pdf", emptyList(), SourceCategory.SYLLABUS)
        every { aiService.isConfigured() } returns false
        coEvery { localRepository.getAllEvents(any()) } returns emptyList()

        controller.addSource(source)
        controller.sourceItems.value.any { it.title == "syllabus.pdf" } shouldBe true

        controller.deleteSource(source)
        kotlinx.coroutines.delay(300)

        coVerify(atLeast = 1) { sourceRepository.deleteSource("syllabus.pdf") }
        controller.sourceItems.value.any { it.title == "syllabus.pdf" } shouldBe false
        controller.selectedSource.value shouldBe null
    }

    test("deleteSource hardDeletes matching events by id prefix") {
        kotlinx.coroutines.delay(100)
        val source = SourceItem("cs101_syllabus", emptyList(), SourceCategory.SYLLABUS)
        every { aiService.isConfigured() } returns false

        val matchingEvent = makeEvent("cs101_syllabus_midterm", id = "cs101_syllabus_midterm")
        val otherEvent = makeEvent("unrelated_event", id = "unrelated_event")
        coEvery { localRepository.getAllEvents(any()) } returns listOf(matchingEvent, otherEvent)

        controller.addSource(source)
        controller.deleteSource(source)
        kotlinx.coroutines.delay(300)

        coVerify(exactly = 1) {
            localRepository.hardDeleteEvent(
                "cs101_syllabus_midterm",
                "default"
            )
        }
        coVerify(exactly = 0) { localRepository.hardDeleteEvent("unrelated_event", "default") }
    }

    test("deleteSource calls calendarAgent.synchronize after cleanup") {
        kotlinx.coroutines.delay(100)
        val source = SourceItem("notes.txt", emptyList(), SourceCategory.READING_MATERIAL)
        every { aiService.isConfigured() } returns false
        coEvery { localRepository.getAllEvents(any()) } returns emptyList()

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
})
