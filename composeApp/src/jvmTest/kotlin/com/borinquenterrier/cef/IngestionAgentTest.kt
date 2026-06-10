package com.borinquenterrier.cef

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class IngestionAgentTest : FunSpec({

    lateinit var driver: SqlDriver
    lateinit var database: AppDatabase
    lateinit var fileReader: LocalFileReader
    lateinit var docxReader: DocxReader
    lateinit var pdfReader: PdfReader
    lateinit var webReader: WebSourceReader
    lateinit var driveService: GoogleDriveService
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
        driveService = mockk(relaxed = true)
        aiService = mockk(relaxed = true)

        ingestionAgent = IngestionAgent(
            fileReader = fileReader,
            docxReader = docxReader,
            pdfReader = pdfReader,
            webReader = webReader,
            driveService = driveService,
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
        coEvery { fileReader.readText(path) } returns fileContent
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
        coEvery { fileReader.readText(path) } returns icsContent

        val result = ingestionAgent.addLocalFile(path)

        result.category shouldBe SourceCategory.CALENDAR
        coVerify(exactly = 0) { aiService.categorizeSource(any()) }

        val persisted =
            database.appDatabaseQueries.selectSourceById(result.title).executeAsOneOrNull()
        persisted shouldNotBe null
        persisted?.category shouldBe "CALENDAR"
    }
    test("addLocalFile throws SourceValidationException for an ICS with no events") {
        val path = "empty.ics"
        coEvery { fileReader.readText(path) } returns "BEGIN:VCALENDAR\nEND:VCALENDAR"

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
        coEvery { webReader.readTextFromUrl(url) } returns "Week 1: Introduction to algorithms."
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
        coEvery { webReader.readTextFromUrl(url) } returns icsContent

        val result = ingestionAgent.addUrl(url)

        result.category shouldBe SourceCategory.CALENDAR
        coVerify(exactly = 0) { aiService.categorizeSource(any()) }
    }

    test("addUrl throws SourceValidationException for empty ICS") {
        val url = "https://cal.example.com/empty.ics"
        coEvery { webReader.readTextFromUrl(url) } returns "BEGIN:VCALENDAR\nEND:VCALENDAR"

        try {
            ingestionAgent.addUrl(url)
            error("Expected SourceValidationException")
        } catch (e: SourceValidationException) {
            // expected
        }
    }

    test("addDriveFile categorizes non-ICS drive files using AI service") {
        val driveFile = DriveFile(
            "drive-id-1",
            "lecture_notes.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
        coEvery {
            driveService.getFileContent(
                driveFile.id,
                driveFile.mimeType
            )
        } returns "Lecture notes content."
        coEvery { aiService.categorizeSource(any()) } returns SourceCategory.READING_MATERIAL

        val result = ingestionAgent.addDriveFile(driveFile)

        result.category shouldBe SourceCategory.READING_MATERIAL
        result.title shouldBe "lecture_notes.docx"
        coVerify(exactly = 1) { aiService.categorizeSource(any()) }
    }

    test("addDriveFile returns CALENDAR category for ICS drive files and skips AI") {
        val driveFile = DriveFile("drive-id-2", "holidays.ics", "text/calendar")
        val icsContent = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:Holiday
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        coEvery { driveService.getFileContent(driveFile.id, driveFile.mimeType) } returns icsContent

        val result = ingestionAgent.addDriveFile(driveFile)

        result.category shouldBe SourceCategory.CALENDAR
        coVerify(exactly = 0) { aiService.categorizeSource(any()) }
    }
})
