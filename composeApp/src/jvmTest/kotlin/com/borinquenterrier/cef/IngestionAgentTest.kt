package com.borinquenterrier.cef

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk

class IngestionAgentTest : FunSpec({

    lateinit var driver: SqlDriver
    lateinit var database: AppDatabase
    lateinit var fileReader: LocalFileReader
    lateinit var docxReader: DocxReader
    lateinit var pdfReader: PdfReader
    lateinit var webReader: WebSourceReader
    lateinit var aiService: AIService
    lateinit var ingestionAgent: IngestionAgent

    beforeEach {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)

        fileReader = mockk(relaxed = true)
        docxReader = mockk(relaxed = true)
        pdfReader = mockk(relaxed = true)
        webReader = mockk(relaxed = true)
        aiService = mockk(relaxed = true)

        ingestionAgent = IngestionAgent(
            fileReader = fileReader,
            docxReader = docxReader,
            pdfReader = pdfReader,
            webReader = webReader,
            aiService = aiService,
            sourceRepository = SqlDelightSourceRepository(database)
        )
    }

    afterEach {
        driver.close()
    }

    test("addLocalFile categorizes syllabus text and persists to DB") {
        val path = "cs101_syllabus.txt"
        val fileContent = "Course: CS101. Grading policy: Exams 50%, Homework 50%."
        coEvery { fileReader.readBytes(path) } returns fileContent.encodeToByteArray()
        coEvery { aiService.categorizeSource(any()) } returns SourceCategory.SYLLABUS

        val result = ingestionAgent.addLocalFile(path)

        result.category shouldBe SourceCategory.SYLLABUS
        coVerify(exactly = 1) { aiService.categorizeSource(any()) }

        val persisted =
            database.appDatabaseQueries.selectSourceById(result.title).executeAsOneOrNull()
        persisted shouldNotBe null
        persisted?.category shouldBe "SYLLABUS"
    }

    test("addLocalFile defaults to CALENDAR for calendar/ics files and skips AI categorization") {
        val path = "my_schedule.ics"
        val icsContent = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:Lecture 1
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        coEvery { fileReader.readBytes(path) } returns icsContent.encodeToByteArray()

        val result = ingestionAgent.addLocalFile(path)

        result.category shouldBe SourceCategory.CALENDAR
        coVerify(exactly = 0) { aiService.categorizeSource(any()) }

        val persisted =
            database.appDatabaseQueries.selectSourceById(result.title).executeAsOneOrNull()
        persisted shouldNotBe null
        persisted?.category shouldBe "CALENDAR"
    }
    test("addLocalFile resolves format and title from the reader's display name when the path itself carries no extension (e.g. a Drive content:// URI)") {
        val path = "content://com.google.android.apps.docs.storage/document/acc=1;doc=xyz;version=1"
        coEvery { fileReader.resolveDisplayName(path) } returns "syllabus.pdf"
        coEvery { fileReader.readBytes(path) } returns "%PDF-1.4 fake bytes".encodeToByteArray()
        coEvery { pdfReader.readSource(any<ByteArray>()) } returns listOf(SourceFragment("Course: CS101."))
        coEvery { aiService.categorizeSource(any()) } returns SourceCategory.SYLLABUS

        val result = ingestionAgent.addLocalFile(path)

        result.title shouldBe "syllabus.pdf"
        coVerify(exactly = 1) { pdfReader.readSource(any<ByteArray>()) }
    }

    test("addLocalFile falls back to parsing the path when the reader has no better display name") {
        val path = "cs101_syllabus.txt"
        coEvery { fileReader.resolveDisplayName(path) } returns ""
        coEvery { fileReader.readBytes(path) } returns "Course: CS101.".encodeToByteArray()
        coEvery { aiService.categorizeSource(any()) } returns SourceCategory.SYLLABUS

        val result = ingestionAgent.addLocalFile(path)

        result.title shouldBe path
    }

    test("addLocalFile throws SourceValidationException for an ICS with no events") {
        val path = "empty.ics"
        coEvery { fileReader.readBytes(path) } returns "BEGIN:VCALENDAR\nEND:VCALENDAR".encodeToByteArray()

        try {
            ingestionAgent.addLocalFile(path)
            error("Expected SourceValidationException")
        } catch (e: SourceValidationException) {
            // expected
        }
        coVerify(exactly = 0) { aiService.categorizeSource(any()) }
    }

    test("addUrl categorizes non-ICS URLs using AI service") {
        val url = "https://example.com/class/syllabus"
        coEvery { webReader.readBytesFromUrl(url) } returns "<html>Week 1</html>".encodeToByteArray()
        every { webReader.cleanHtml(any()) } returns "Week 1: Introduction to algorithms."
        coEvery { aiService.categorizeSource(any()) } returns SourceCategory.SYLLABUS

        val result = ingestionAgent.addUrl(url)

        result.category shouldBe SourceCategory.SYLLABUS
        coVerify(exactly = 1) { aiService.categorizeSource(any()) }
    }

    test("addUrl returns CALENDAR category for .ics URLs and skips AI") {
        val url = "https://cal.example.com/schedule.ics"
        val icsContent = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:Midterm
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        coEvery { webReader.readBytesFromUrl(url) } returns icsContent.encodeToByteArray()

        val result = ingestionAgent.addUrl(url)

        result.category shouldBe SourceCategory.CALENDAR
        coVerify(exactly = 0) { aiService.categorizeSource(any()) }
    }

    test("addUrl throws SourceValidationException for empty ICS") {
        val url = "https://cal.example.com/empty.ics"
        coEvery { webReader.readBytesFromUrl(url) } returns "BEGIN:VCALENDAR\nEND:VCALENDAR".encodeToByteArray()

        try {
            ingestionAgent.addUrl(url)
            error("Expected SourceValidationException")
        } catch (e: SourceValidationException) {
            // expected
        }
    }

    // ── large-document notice (HARD-8) ─────────────────────────────────────────

    test("addLocalFile sets largeDocumentNotice for a text-less PDF over the inline size cap") {
        val path = "scanned_syllabus.pdf"
        coEvery { fileReader.readBytes(path) } returns ByteArray(14 * 1024 * 1024 + 1)
        coEvery { pdfReader.readSource(any<ByteArray>()) } returns emptyList()
        coEvery { aiService.extractTextFromDocument(any(), any()) } returns "Recovered syllabus text"
        coEvery { aiService.categorizeSource(any()) } returns SourceCategory.SYLLABUS

        ingestionAgent.addLocalFile(path)

        ingestionAgent.largeDocumentNotice.value shouldBe IngestionAgent.LARGE_DOCUMENT_NOTICE
    }

    test("addLocalFile leaves largeDocumentNotice null for a small text-less PDF") {
        val path = "small_scan.pdf"
        coEvery { fileReader.readBytes(path) } returns "%PDF-scan".encodeToByteArray()
        coEvery { pdfReader.readSource(any<ByteArray>()) } returns emptyList()
        coEvery { aiService.extractTextFromDocument(any(), any()) } returns "Recovered text"
        coEvery { aiService.categorizeSource(any()) } returns SourceCategory.SYLLABUS

        ingestionAgent.addLocalFile(path)

        ingestionAgent.largeDocumentNotice.value shouldBe null
    }

    test("largeDocumentNotice clears at the start of the next ingest call") {
        val bigPath = "scanned_syllabus.pdf"
        coEvery { fileReader.readBytes(bigPath) } returns ByteArray(14 * 1024 * 1024 + 1)
        coEvery { pdfReader.readSource(any<ByteArray>()) } returns emptyList()
        coEvery { aiService.extractTextFromDocument(any(), any()) } returns "Recovered syllabus text"
        coEvery { aiService.categorizeSource(any()) } returns SourceCategory.SYLLABUS
        ingestionAgent.addLocalFile(bigPath)
        ingestionAgent.largeDocumentNotice.value shouldNotBe null

        val smallPath = "notes.txt"
        coEvery { fileReader.readBytes(smallPath) } returns "Course: CS101.".encodeToByteArray()
        ingestionAgent.addLocalFile(smallPath)

        ingestionAgent.largeDocumentNotice.value shouldBe null
    }

})
