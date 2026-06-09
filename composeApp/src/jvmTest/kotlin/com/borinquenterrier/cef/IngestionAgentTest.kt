package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import com.borinquenterrier.cef.db.AppDatabase
import com.borinquenterrier.cef.db.DriverFactory
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

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

        val persisted = database.appDatabaseQueries.selectSourceById(result.title).executeAsOneOrNull()
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

        val persisted = database.appDatabaseQueries.selectSourceById(result.title).executeAsOneOrNull()
        persisted shouldNotBe null
        persisted?.category shouldBe "CALENDAR"
    }
})
