package com.borinquenterrier.cef

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot

/**
 * Unit tests for [ContextAgent.queryAllSources] and [AiPrompts.getMultiSourceChatPrompt].
 *
 * Uses a real in-memory SQLite DB (via JdbcSqliteDriver) so SQLDelight's generated
 * Query<T> types are not mocked — avoiding JVM type-erasure issues with MockK.
 * [AIService] is still mocked with MockK and is re-created in [beforeEach] to ensure
 * a clean stub slate between tests.
 */
class ContextAgentMultiSourceTest : FunSpec({

    lateinit var driver: SqlDriver
    lateinit var database: AppDatabase
    lateinit var mockAiService: AIService
    lateinit var sut: ContextAgent
    lateinit var fragmentRanker: FragmentRanker

    beforeEach {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)
        mockAiService = mockk<AIService>()
        fragmentRanker = FragmentRanker()
        sut = ContextAgent(
            aiService = mockAiService,
            sourceRepository = SqlDelightSourceRepository(database),
            fragmentRanker = fragmentRanker,
            contextBuilder = SourceContextBuilder(),
            logger = null
        )
    }

    afterEach {
        driver.close()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    fun sutWithCompaction(chatRepository: ChatRepository, contextWindow: Int) = ContextAgent(
        aiService = mockAiService,
        sourceRepository = SqlDelightSourceRepository(database),
        fragmentRanker = fragmentRanker,
        contextBuilder = SourceContextBuilder(),
        logger = null,
        chatRepository = chatRepository,
        contextWindowProvider = { contextWindow }
    )

    fun makeSource(title: String, category: SourceCategory, text: String) =
        SourceItem(
            title = title,
            fragments = listOf(SourceFragment(text = text)),
            category = category
        )

    fun sutWithEvents(events: List<Event>, today: kotlinx.datetime.LocalDate) = ContextAgent(
        aiService = mockAiService,
        sourceRepository = SqlDelightSourceRepository(database),
        fragmentRanker = fragmentRanker,
        contextBuilder = SourceContextBuilder(),
        logger = null,
        eventsProvider = { events },
        todayProvider = { today }
    )

    // ── tests ─────────────────────────────────────────────────────────────────

    test("queryAllSources returns guard message when no sources are loaded") {
        val result = sut.queryAllSources(
            sources = emptyList(),
            conversationHistory = emptyList(),
            question = "What is the grading policy?"
        )
        result shouldContain "No sources are loaded yet"
    }

    test("queryAllSources injects all source content into the AI prompt") {
        val promptSlot = slot<String>()
        coEvery { mockAiService.generateChatResponse(capture(promptSlot)) } returns "Mocked response"

        val sources = listOf(
            makeSource("MATH 101 Syllabus", SourceCategory.SYLLABUS, "Final exam is worth 40%."),
            makeSource("Week 1 Notes", SourceCategory.LECTURE_NOTES, "Introduction to calculus."),
            makeSource("Lab 1 Manual", SourceCategory.LAB_MANUAL, "Titration procedure steps.")
        )

        sut.queryAllSources(
            sources = sources,
            conversationHistory = emptyList(),
            question = "What topics are covered?"
        )

        val prompt = promptSlot.captured
        prompt shouldContain "MATH 101 Syllabus"
        prompt shouldContain "Week 1 Notes"
        prompt shouldContain "Lab 1 Manual"
        prompt shouldContain "Final exam is worth 40%"
        prompt shouldContain "What topics are covered?"
    }

    test("queryAllSources injects the calendar digest with precedence guardrail when events are wired") {
        val promptSlot = slot<String>()
        coEvery { mockAiService.generateChatResponse(capture(promptSlot)) } returns "ok"

        val today = kotlinx.datetime.LocalDate(2026, 7, 1)
        val events = listOf(
            DayEvent(
                title = "Issue Brief #2",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE,
                date = kotlinx.datetime.LocalDate(2026, 7, 10)
            ),
            DayEvent(
                title = "Inferred class meeting",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.CLASS,
                date = kotlinx.datetime.LocalDate(2026, 7, 3),
                warning = "Added automatically"
            )
        )

        sutWithEvents(events, today).queryAllSources(
            sources = listOf(makeSource("ENG 101", SourceCategory.SYLLABUS, "Weekly schedule.")),
            conversationHistory = emptyList(),
            question = "When is my next deadline?"
        )

        val prompt = promptSlot.captured
        prompt shouldContain "<calendar_digest>"
        prompt shouldContain "2026-07-10 | DEADLINE | Issue Brief #2"
        // Inferred events keep their marker all the way into the prompt.
        prompt shouldContain "Inferred class meeting ⚠"
        // The precedence rule: digest beats document prose on dates, and conflicts are surfaced.
        prompt shouldContain "answer from <calendar_digest> first"
        prompt shouldContain "tell the student that their documents say otherwise"
    }

    test("queryAllSources omits the digest block when no events provider is wired") {
        val promptSlot = slot<String>()
        coEvery { mockAiService.generateChatResponse(capture(promptSlot)) } returns "ok"

        sut.queryAllSources(
            sources = listOf(makeSource("ENG 101", SourceCategory.SYLLABUS, "Weekly schedule.")),
            conversationHistory = emptyList(),
            question = "When is my next deadline?"
        )

        promptSlot.captured shouldNotContain "<calendar_digest>"
    }

    test("queryAllSources still answers from sources when the events provider throws") {
        val promptSlot = slot<String>()
        coEvery { mockAiService.generateChatResponse(capture(promptSlot)) } returns "ok"

        val failing = ContextAgent(
            aiService = mockAiService,
            sourceRepository = SqlDelightSourceRepository(database),
            fragmentRanker = fragmentRanker,
            contextBuilder = SourceContextBuilder(),
            logger = null,
            eventsProvider = { throw IllegalStateException("db unavailable") }
        )

        val result = failing.queryAllSources(
            sources = listOf(makeSource("ENG 101", SourceCategory.SYLLABUS, "Weekly schedule.")),
            conversationHistory = emptyList(),
            question = "When is my next deadline?"
        )

        result shouldBe "ok"
        promptSlot.captured shouldNotContain "<calendar_digest>"
    }

    test("queryAllSources sorts SYLLABUS before READING_MATERIAL before OTHER in prompt") {
        val promptSlot = slot<String>()
        coEvery { mockAiService.generateChatResponse(capture(promptSlot)) } returns "ok"

        // Deliberately supply in reverse priority order
        val sources = listOf(
            makeSource("Extra Reading", SourceCategory.READING_MATERIAL, "Chapter 3 text."),
            makeSource("Misc Notes", SourceCategory.OTHER, "Random notes."),
            makeSource("Course Syllabus", SourceCategory.SYLLABUS, "Grading: A=90%.")
        )

        sut.queryAllSources(
            sources = sources,
            conversationHistory = emptyList(),
            question = "What is an A?"
        )

        val prompt = promptSlot.captured
        val syllabusPos = prompt.indexOf("Course Syllabus")
        val readingPos = prompt.indexOf("Extra Reading")
        val otherPos = prompt.indexOf("Misc Notes")

        // SYLLABUS must appear before READING_MATERIAL, which must appear before OTHER
        (syllabusPos < readingPos) shouldBe true
        (readingPos < otherPos) shouldBe true
    }

    test("queryAllSources threads conversation history into the prompt") {
        val promptSlot = slot<String>()
        coEvery { mockAiService.generateChatResponse(capture(promptSlot)) } returns "ok"

        val history = listOf(
            ChatMessage.create("What is the late policy?", ChatRole.USER, 1L),
            ChatMessage.create("Assignments lose 10% per day late.", ChatRole.AI, 2L),
            ChatMessage.create("What about exams?", ChatRole.USER, 3L)
        )

        sut.queryAllSources(
            sources = listOf(makeSource("Syllabus", SourceCategory.SYLLABUS, "Policies…")),
            conversationHistory = history,
            question = "And group projects?"
        )

        val prompt = promptSlot.captured
        prompt shouldContain "What is the late policy?"
        prompt shouldContain "Assignments lose 10% per day late."
        prompt shouldContain "What about exams?"
        prompt shouldContain "And group projects?"
    }

    test("queryAllSources calls generateChatResponse exactly once regardless of source count") {
        coEvery { mockAiService.generateChatResponse(any()) } returns "ok"

        val sources = (1..5).map {
            makeSource("Source $it", SourceCategory.READING_MATERIAL, "Content $it")
        }

        sut.queryAllSources(
            sources = sources,
            conversationHistory = emptyList(),
            question = "Summarise everything"
        )

        coVerify(exactly = 1) { mockAiService.generateChatResponse(any()) }
    }

    test("AiPrompts.getMultiSourceChatPrompt includes category label and truncates long content") {
        val longText = "x".repeat(10_000)
        val blocks = listOf(
            SourceContextBlock(
                title = "Big Doc",
                category = "SYLLABUS",
                metadata = null,
                fragmentText = longText
            )
        )

        val prompt = AiPrompts.getMultiSourceChatPrompt(
            sourceBlocks = blocks,
            conversationHistory = emptyList(),
            question = "Anything?"
        )

        prompt shouldContain "Big Doc"
        prompt shouldContain "SYLLABUS"
        prompt shouldContain "[content truncated]"
    }

    test("queryAllSources includes all same-category sources in the prompt") {
        val promptSlot = slot<String>()
        coEvery { mockAiService.generateChatResponse(capture(promptSlot)) } returns "ok"

        val sources = listOf(
            makeSource("Alpha", SourceCategory.LECTURE_NOTES, "A"),
            makeSource("Beta", SourceCategory.LECTURE_NOTES, "B"),
            makeSource("Gamma", SourceCategory.LECTURE_NOTES, "C")
        )

        sut.queryAllSources(
            sources = sources,
            conversationHistory = emptyList(),
            question = "q"
        )

        val prompt = promptSlot.captured
        prompt shouldContain "Alpha"
        prompt shouldContain "Beta"
        prompt shouldContain "Gamma"
    }

    test("queryAllSources uses metadata from DB when source has been previously analyzed") {
        val promptSlot = slot<String>()
        coEvery { mockAiService.generateChatResponse(capture(promptSlot)) } returns "ok"

        // Pre-populate DB with metadata for the source
        database.appDatabaseQueries.insertSource(
            id = "CS101 Syllabus",
            title = "CS101 Syllabus",
            originUri = null,
            type = "TEXT",
            category = "SYLLABUS",
            metadata = """{"grading_scale":"Final 40%, Midterm 30%, HW 30%"}""",
            updatedAt = 0L,
            contentHash = null,
            status = "DONE",
            fileBytes = null
        )

        val sources = listOf(makeSource("CS101 Syllabus", SourceCategory.SYLLABUS, "..."))

        sut.queryAllSources(
            sources = sources,
            conversationHistory = emptyList(),
            question = "How is the final weighted?"
        )

        val prompt = promptSlot.captured
        prompt shouldContain "Final 40%"
    }

    test("rankFragments prioritizes matching keyword frequency (TF-IDF)") {
        val mathSyllabus = SourceItem(
            title = "Math Syllabus",
            category = SourceCategory.SYLLABUS,
            fragments = listOf(
                SourceFragment(text = "This fragment does not mention the keyword."),
                SourceFragment(text = "This fragment mentions calculus once in passing."),
                SourceFragment(text = "Calculus is the study of change. Calculus is divided into differential and integral calculus.")
            )
        )

        val ranked = fragmentRanker.rankFragments(
            sources = listOf(mathSyllabus),
            question = "tell me about calculus",
            topK = 5
        )

        ranked.size shouldBe 3
        ranked[0].second.text shouldContain "Calculus is the study of change"
        ranked[1].second.text shouldContain "mentions calculus once"
        ranked[2].second.text shouldContain "does not mention"
    }

    test("rankFragments ignores stop words and queries matching keywords") {
        val physicsSyllabus = SourceItem(
            title = "Physics Syllabus",
            category = SourceCategory.SYLLABUS,
            fragments = listOf(
                SourceFragment(text = "the a of in to with"),
                SourceFragment(text = "Thermodynamics is the study of heat and temperature.")
            )
        )

        val rankedForHeat = fragmentRanker.rankFragments(
            sources = listOf(physicsSyllabus),
            question = "the a of heat",
            topK = 5
        )

        rankedForHeat.first().second.text shouldContain "Thermodynamics is the study of heat"
    }

    test("rankFragments respects topK constraint") {
        val fragments = (1..20).map { i ->
            SourceFragment(text = "calculus article number $i")
        }
        val source = SourceItem(
            title = "Calculus Book",
            category = SourceCategory.READING_MATERIAL,
            fragments = fragments
        )

        val ranked = fragmentRanker.rankFragments(
            sources = listOf(source),
            question = "calculus",
            topK = 5
        )

        ranked.size shouldBe 5
    }

    test("rankFragments falls back to topK when query terms are empty") {
        val fragments = (1..10).map { i ->
            SourceFragment(text = "Text fragment number $i")
        }
        val source = SourceItem(
            title = "Generic Doc",
            category = SourceCategory.OTHER,
            fragments = fragments
        )

        val ranked = fragmentRanker.rankFragments(
            sources = listOf(source),
            question = "the a of",
            topK = 3
        )

        ranked.size shouldBe 3
        ranked[0].second.text shouldBe "Text fragment number 1"
        ranked[1].second.text shouldBe "Text fragment number 2"
        ranked[2].second.text shouldBe "Text fragment number 3"
    }

    // ── compaction (design 2.1, Part B) ─────────────────────────────────────────

    test("queryAllSources does not summarize when history fits comfortably within the budget") {
        val chatRepository = SqlDelightChatRepository(database)
        val conversation = Conversation.create(createdAt = 1L, title = "small chat")
        chatRepository.createConversation(conversation)
        val sut = sutWithCompaction(chatRepository, contextWindow = 50_000)

        coEvery { mockAiService.generateChatResponse(any()) } returns "Final answer"

        val history = listOf(
            ChatMessage.create("Hi", ChatRole.USER, 10L, conversation.id),
            ChatMessage.create("Hello!", ChatRole.AI, 20L, conversation.id)
        )

        sut.queryAllSources(
            sources = listOf(makeSource("Syllabus", SourceCategory.SYLLABUS, "Policies…")),
            conversationHistory = history,
            question = "Anything else?",
            conversationId = conversation.id
        )

        coVerify(exactly = 1) { mockAiService.generateChatResponse(any()) }
        chatRepository.getConversation(conversation.id)?.summary shouldBe null
    }

    test("queryAllSources folds the oldest turns into a persisted rolling summary once history exceeds the budget") {
        val chatRepository = SqlDelightChatRepository(database)
        val conversation = Conversation.create(createdAt = 1L, title = "long chat")
        chatRepository.createConversation(conversation)
        val sut = sutWithCompaction(chatRepository, contextWindow = 2_600)

        val capturedPrompts = mutableListOf<String>()
        coEvery { mockAiService.generateChatResponse(any()) } coAnswers {
            val prompt = firstArg<String>()
            capturedPrompts.add(prompt)
            if (prompt.contains("CONVERSATION SUMMARY COMPACTION")) "Folded summary of early turns."
            else "Final answer"
        }

        // Each message is ~52 tokens ("x".repeat(200) + " turnN"); a 144-token history budget
        // (2600 window - 2448 reserved - ~8 for source/question) fits only the last 2 verbatim.
        val longContent = "x".repeat(200)
        val history = (1..8).map { i ->
            ChatMessage.create("$longContent turn$i", ChatRole.USER, i.toLong(), conversation.id)
        }

        sut.queryAllSources(
            sources = listOf(makeSource("Syllabus", SourceCategory.SYLLABUS, "Policies…")),
            conversationHistory = history,
            question = "Latest question?",
            conversationId = conversation.id
        )

        coVerify(exactly = 2) { mockAiService.generateChatResponse(any()) }

        val summaryPrompt = capturedPrompts.first { it.contains("CONVERSATION SUMMARY COMPACTION") }
        val finalPrompt = capturedPrompts.first { !it.contains("CONVERSATION SUMMARY COMPACTION") }
        summaryPrompt shouldContain "turn1"
        finalPrompt shouldContain "Conversation summary so far: Folded summary of early turns."
        finalPrompt shouldContain "turn8"
        finalPrompt shouldNotContain "turn1"

        val reloaded = chatRepository.getConversation(conversation.id)
        reloaded?.summary shouldBe "Folded summary of early turns."
        reloaded?.summarizedThroughMessageId shouldBe history[5].id
    }

    test("queryAllSources reuses a persisted summary without re-folding when the unsummarized tail already fits") {
        val chatRepository = SqlDelightChatRepository(database)
        val conversation = Conversation.create(createdAt = 1L, title = "resumed chat")
        chatRepository.createConversation(conversation)

        val earlierTurn = ChatMessage.create("earlier turn (already summarized)", ChatRole.USER, 3L, conversation.id)
        val newestTurn = ChatMessage.create("newest turn", ChatRole.USER, 6L, conversation.id)
        chatRepository.updateSummary(
            conversation.id,
            summary = "Earlier turns covered grading policy.",
            summarizedThroughMessageId = earlierTurn.id,
            updatedAt = 5L
        )
        val sut = sutWithCompaction(chatRepository, contextWindow = 50_000)

        val promptSlot = slot<String>()
        coEvery { mockAiService.generateChatResponse(capture(promptSlot)) } returns "Final answer"

        // earlierTurn IS the folded boundary — only newestTurn (after it) is unsummarized.
        val history = listOf(earlierTurn, newestTurn)

        sut.queryAllSources(
            sources = listOf(makeSource("Syllabus", SourceCategory.SYLLABUS, "Policies…")),
            conversationHistory = history,
            question = "Follow-up?",
            conversationId = conversation.id
        )

        coVerify(exactly = 1) { mockAiService.generateChatResponse(any()) }
        promptSlot.captured shouldContain "Conversation summary so far: Earlier turns covered grading policy."
        promptSlot.captured shouldContain "newest turn"
        promptSlot.captured shouldNotContain "already summarized"
        chatRepository.getConversation(conversation.id)?.summarizedThroughMessageId shouldBe earlierTurn.id
    }

    test("queryAllSources keeps a long but under-budget history fully verbatim instead of re-truncating to MAX_HISTORY_TURNS") {
        // Regression test: before the fix, ContextAgent passed summary=null whenever no fold had
        // happened yet, which made ChatBuilder silently re-apply its legacy takeLast(10) cut even
        // though the budget-aware plan had already decided all 15 turns fit comfortably.
        val chatRepository = SqlDelightChatRepository(database)
        val conversation = Conversation.create(createdAt = 1L, title = "long but small chat")
        chatRepository.createConversation(conversation)
        val sut = sutWithCompaction(chatRepository, contextWindow = 50_000)

        val promptSlot = slot<String>()
        coEvery { mockAiService.generateChatResponse(capture(promptSlot)) } returns "Final answer"

        val history = (1..15).map { i ->
            ChatMessage.create("turn$i", ChatRole.USER, i.toLong(), conversation.id)
        }

        sut.queryAllSources(
            sources = listOf(makeSource("Syllabus", SourceCategory.SYLLABUS, "Policies…")),
            conversationHistory = history,
            question = "Latest?",
            conversationId = conversation.id
        )

        coVerify(exactly = 1) { mockAiService.generateChatResponse(any()) }
        promptSlot.captured shouldContain "turn1\n"
        promptSlot.captured shouldContain "turn15"
        chatRepository.getConversation(conversation.id)?.summary shouldBe null
    }

    test("queryAllSources keeps the prompt within budget when the summarization call itself fails") {
        // Regression test: before the fix, a failed/errored summarization call fell back to the
        // raw unsummarized list (over budget by definition) instead of the budget-sized tail,
        // defeating the whole point of compaction on exactly the path meant to be the safety net.
        val chatRepository = SqlDelightChatRepository(database)
        val conversation = Conversation.create(createdAt = 1L, title = "flaky summarizer chat")
        chatRepository.createConversation(conversation)
        val sut = sutWithCompaction(chatRepository, contextWindow = 2_600)

        val finalPromptSlot = slot<String>()
        coEvery { mockAiService.generateChatResponse(any()) } coAnswers {
            val prompt = firstArg<String>()
            if (prompt.contains("CONVERSATION SUMMARY COMPACTION")) {
                "Error: quota exceeded"
            } else {
                finalPromptSlot.captured = prompt
                "Final answer"
            }
        }

        val longContent = "x".repeat(200)
        val history = (1..8).map { i ->
            ChatMessage.create("$longContent turn$i", ChatRole.USER, i.toLong(), conversation.id)
        }

        sut.queryAllSources(
            sources = listOf(makeSource("Syllabus", SourceCategory.SYLLABUS, "Policies…")),
            conversationHistory = history,
            question = "Latest question?",
            conversationId = conversation.id
        )

        // The fallback must use the budget-sized tail (last 2 turns), not the full 8-turn history.
        finalPromptSlot.captured shouldContain "turn8"
        finalPromptSlot.captured shouldNotContain "turn1"
        chatRepository.getConversation(conversation.id)?.summary shouldBe null
    }
})

