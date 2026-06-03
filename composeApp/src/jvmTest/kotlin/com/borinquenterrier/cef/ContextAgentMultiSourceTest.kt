package com.borinquenterrier.cef

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
import com.borinquenterrier.cef.db.AppDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

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

    beforeEach {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)
        mockAiService = mockk<AIService>()
        sut = ContextAgent(aiService = mockAiService, database = database, logger = null)
    }

    afterEach {
        driver.close()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    fun makeSource(title: String, category: SourceCategory, text: String) =
        SourceItem(
            title     = title,
            fragments = listOf(SourceFragment(text = text)),
            category  = category
        )

    // ── tests ─────────────────────────────────────────────────────────────────

    test("queryAllSources returns guard message when no sources are loaded") {
        val result = sut.queryAllSources(
            sources             = emptyList(),
            conversationHistory = emptyList(),
            question            = "What is the grading policy?"
        )
        result shouldContain "No sources are loaded yet"
    }

    test("queryAllSources injects all source content into the AI prompt") {
        val promptSlot = slot<String>()
        coEvery { mockAiService.generateChatResponse(capture(promptSlot)) } returns "Mocked response"

        val sources = listOf(
            makeSource("MATH 101 Syllabus", SourceCategory.SYLLABUS,        "Final exam is worth 40%."),
            makeSource("Week 1 Notes",      SourceCategory.LECTURE_NOTES,   "Introduction to calculus."),
            makeSource("Lab 1 Manual",      SourceCategory.LAB_MANUAL,      "Titration procedure steps.")
        )

        sut.queryAllSources(
            sources             = sources,
            conversationHistory = emptyList(),
            question            = "What topics are covered?"
        )

        val prompt = promptSlot.captured
        prompt shouldContain "MATH 101 Syllabus"
        prompt shouldContain "Week 1 Notes"
        prompt shouldContain "Lab 1 Manual"
        prompt shouldContain "Final exam is worth 40%"
        prompt shouldContain "What topics are covered?"
    }

    test("queryAllSources sorts SYLLABUS before READING_MATERIAL before OTHER in prompt") {
        val promptSlot = slot<String>()
        coEvery { mockAiService.generateChatResponse(capture(promptSlot)) } returns "ok"

        // Deliberately supply in reverse priority order
        val sources = listOf(
            makeSource("Extra Reading",   SourceCategory.READING_MATERIAL, "Chapter 3 text."),
            makeSource("Misc Notes",      SourceCategory.OTHER,            "Random notes."),
            makeSource("Course Syllabus", SourceCategory.SYLLABUS,         "Grading: A=90%.")
        )

        sut.queryAllSources(
            sources             = sources,
            conversationHistory = emptyList(),
            question            = "What is an A?"
        )

        val prompt = promptSlot.captured
        val syllabusPos = prompt.indexOf("Course Syllabus")
        val readingPos  = prompt.indexOf("Extra Reading")
        val otherPos    = prompt.indexOf("Misc Notes")

        // SYLLABUS must appear before READING_MATERIAL, which must appear before OTHER
        (syllabusPos < readingPos) shouldBe true
        (readingPos  < otherPos)   shouldBe true
    }

    test("queryAllSources threads conversation history into the prompt") {
        val promptSlot = slot<String>()
        coEvery { mockAiService.generateChatResponse(capture(promptSlot)) } returns "ok"

        val history = listOf(
            ChatMessage("User", "What is the late policy?"),
            ChatMessage("AI",   "Assignments lose 10% per day late."),
            ChatMessage("User", "What about exams?")
        )

        sut.queryAllSources(
            sources             = listOf(makeSource("Syllabus", SourceCategory.SYLLABUS, "Policies…")),
            conversationHistory = history,
            question            = "And group projects?"
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
            sources             = sources,
            conversationHistory = emptyList(),
            question            = "Summarise everything"
        )

        coVerify(exactly = 1) { mockAiService.generateChatResponse(any()) }
    }

    test("AiPrompts.getMultiSourceChatPrompt includes category label and truncates long content") {
        val longText = "x".repeat(10_000)
        val blocks   = listOf(
            SourceContextBlock(
                title        = "Big Doc",
                category     = "SYLLABUS",
                metadata     = null,
                fragmentText = longText
            )
        )

        val prompt = AiPrompts.getMultiSourceChatPrompt(
            sourceBlocks        = blocks,
            conversationHistory = emptyList(),
            question            = "Anything?"
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
            makeSource("Beta",  SourceCategory.LECTURE_NOTES, "B"),
            makeSource("Gamma", SourceCategory.LECTURE_NOTES, "C")
        )

        sut.queryAllSources(
            sources             = sources,
            conversationHistory = emptyList(),
            question            = "q"
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
            id        = "CS101 Syllabus",
            title     = "CS101 Syllabus",
            originUri = null,
            type      = "TEXT",
            category  = "SYLLABUS",
            metadata  = """{"grading_scale":"Final 40%, Midterm 30%, HW 30%"}""",
            updatedAt = 0L
        )

        val sources = listOf(makeSource("CS101 Syllabus", SourceCategory.SYLLABUS, "..."))

        sut.queryAllSources(
            sources             = sources,
            conversationHistory = emptyList(),
            question            = "How is the final weighted?"
        )

        val prompt = promptSlot.captured
        prompt shouldContain "Final 40%"
    }
})
