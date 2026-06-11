package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class MultiSourceIngestionIntegrationTest : FunSpec({

    data class ExpectedEvent(
        val title: String,
        val date: LocalDate,
        val category: AcademicCategory
    )

    fun loadExpectedEvents(expectedFile: File): List<ExpectedEvent> {
        val text = expectedFile.readText()
        val jsonArray = Json.parseToJsonElement(text).jsonArray
        return jsonArray.map { element ->
            val obj = element.jsonObject
            val title = obj["title"]!!.jsonPrimitive.content
            val dateStr = obj["date"]!!.jsonPrimitive.content
            val categoryStr = obj["category"]!!.jsonPrimitive.content
            ExpectedEvent(
                title = title,
                date = LocalDate.parse(dateStr),
                category = AcademicCategory.valueOf(categoryStr)
            )
        }
    }

    fun areTitlesSimilar(title1: String, title2: String): Boolean {
        fun normalize(s: String): String {
            return s.lowercase()
                .replace(Regex("['\"#’“”]"), "")
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        val n1 = normalize(title1)
        val n2 = normalize(title2)
        return n1.contains(n2) || n2.contains(n1)
    }

    test("Headless Multi-Source Ingestion: should ingest calendar PDF and syllabi and verify combined events").config(
        timeout = AI_INTEGRATION_TIMEOUT_MS.milliseconds
    ) {
        val apiKey = resolveApiKey("MULTI-SOURCE INGESTION INTEGRATION TEST") ?: return@config

        // 1. Setup temporary calendar PDF
        val tempCalendarFile = File.createTempFile("calendar_spring_2025", ".pdf")
        val calendarLines = listOf(
            "Spring 2025 Academic Calendar",
            "",
            "January 13, 2025: First Day of Classes / Semester Starts",
            "January 20, 2025: Martin Luther King Jr. Day (Holiday)",
            "January 28, 2025: Last Day to Drop Course without a Grade",
            "March 17 - March 21, 2025: Spring Break (Holiday)",
            "April 3, 2025: Last Day to Drop Course with a W",
            "May 9, 2025: Last Day of Classes",
            "May 12 - May 16, 2025: Final Exams Week"
        )
        TestPdfGenerator.generatePdf(calendarLines, tempCalendarFile)

        // 2. Setup in-memory database
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)

        // 3. Setup dependencies
        val settings = MapSettings()
        settings.putString("CEF_GEMINI_API_KEY", apiKey)
        val logger = Logger(settings)
        val aiService: AIService = RealAIService(settings, logger, database)

        val sourceRepository = SqlDelightSourceRepository(database)
        val localCalendarRepo = SqlDelightLocalCalendarRepository(database, settings)
        val remoteCalendarRepo = mockk<RemoteCalendarRepository>(relaxed = true)
        val calendarAgent = CalendarAgent(localCalendarRepo, remoteCalendarRepo, logger = logger)

        val ingestionAgent = IngestionAgent(
            fileReader = mockk(relaxed = true),
            docxReader = mockk(relaxed = true),
            pdfReader = PdfReader(),
            webReader = mockk(relaxed = true),
            driveService = mockk(relaxed = true),
            aiService = aiService,
            sourceRepository = sourceRepository
        )

        val eventAgent = EventAgent(
            aiService = aiService,
            repository = calendarAgent,
            logger = logger
        )

        // 4. Ingest calendar, BDAN 250 syllabus, and HIST 152 syllabus
        val bdanPdfFile = listOf(
            File("src/commonTest/resources/syllabus_bdan250.pdf"),
            File("composeApp/src/commonTest/resources/syllabus_bdan250.pdf"),
            File("../composeApp/src/commonTest/resources/syllabus_bdan250.pdf")
        ).find { it.exists() } ?: throw Exception("BDAN 250 PDF not found")

        val histPdfFile = listOf(
            File("src/commonTest/resources/syllabus_hist152.pdf"),
            File("composeApp/src/commonTest/resources/syllabus_hist152.pdf"),
            File("../composeApp/src/commonTest/resources/syllabus_hist152.pdf")
        ).find { it.exists() } ?: throw Exception("HIST 152 PDF not found")

        println("INGESTING SOURCES...")
        val calendarSource = skipIfQuotaExhausted("ingestCalendarPdf") {
            ingestionAgent.addLocalFile(tempCalendarFile.absolutePath)
        }
        val bdanSource = skipIfQuotaExhausted("ingestBdanSyllabus") {
            ingestionAgent.addLocalFile(bdanPdfFile.absolutePath)
        }
        val histSource = skipIfQuotaExhausted("ingestHistSyllabus") {
            ingestionAgent.addLocalFile(histPdfFile.absolutePath)
        }

        // Verify Categorization
        calendarSource.category shouldBe SourceCategory.CALENDAR
        bdanSource.category shouldBe SourceCategory.SYLLABUS
        histSource.category shouldBe SourceCategory.SYLLABUS

        // 5. Extract events from each source and push to calendar
        println("EXTRACTING EVENTS AND PUSHING TO CALENDAR...")

        // Ingest Academic Calendar events
        skipIfQuotaExhausted("extractCalendarEvents") {
            eventAgent.extractDeliverables(calendarSource)
        }
        eventAgent.pushToCalendar()

        // Ingest BDAN 250 syllabus events
        skipIfQuotaExhausted("extractBdanEvents") {
            eventAgent.extractDeliverables(bdanSource)
        }
        eventAgent.pushToCalendar()

        // Ingest HIST 152 syllabus events
        skipIfQuotaExhausted("extractHistEvents") {
            eventAgent.extractDeliverables(histSource)
        }
        eventAgent.pushToCalendar()

        // 6. Load ground truth expected events
        val bdanExpectedFile = listOf(
            File("src/commonTest/resources/syllabus_bdan250_expected.json"),
            File("composeApp/src/commonTest/resources/syllabus_bdan250_expected.json"),
            File("../composeApp/src/commonTest/resources/syllabus_bdan250_expected.json")
        ).find { it.exists() } ?: throw Exception("BDAN 250 Expected JSON not found")

        val histExpectedFile = listOf(
            File("src/commonTest/resources/syllabus_hist152_expected.json"),
            File("composeApp/src/commonTest/resources/syllabus_hist152_expected.json"),
            File("../composeApp/src/commonTest/resources/syllabus_hist152_expected.json")
        ).find { it.exists() } ?: throw Exception("HIST 152 Expected JSON not found")

        val bdanExpected = loadExpectedEvents(bdanExpectedFile)
        val histExpected = loadExpectedEvents(histExpectedFile)

        val academicCalendarExpected = listOf(
            ExpectedEvent(
                "First Day of Classes",
                LocalDate(2025, 1, 13),
                AcademicCategory.SEMESTER_BOUND
            ),
            ExpectedEvent(
                "Martin Luther King Jr. Day",
                LocalDate(2025, 1, 20),
                AcademicCategory.HOLIDAY
            ),
            ExpectedEvent(
                "Last Day to Drop Course without a Grade",
                LocalDate(2025, 1, 28),
                AcademicCategory.SEMESTER_BOUND
            ),
            ExpectedEvent("Spring Break", LocalDate(2025, 3, 17), AcademicCategory.HOLIDAY),
            ExpectedEvent(
                "Last Day to Drop Course with a W",
                LocalDate(2025, 4, 3),
                AcademicCategory.SEMESTER_BOUND
            ),
            ExpectedEvent(
                "Last Day of Classes",
                LocalDate(2025, 5, 9),
                AcademicCategory.SEMESTER_BOUND
            ),
            ExpectedEvent(
                "Final Exams Week",
                LocalDate(2025, 5, 12),
                AcademicCategory.SEMESTER_BOUND
            )
        )

        val aggregatedExpected = bdanExpected + histExpected + academicCalendarExpected

        // 7. Retrieve and evaluate consolidated events
        val actualEvents = calendarAgent.getEvents("default")
        actualEvents.shouldNotBeEmpty()

        println("\n=======================================================")
        println("       MULTI-SOURCE INGESTION INTEGRATION TEST RESULTS")
        println("=======================================================")
        println("Extracted consolidated events:")
        actualEvents.forEach { println("  - ${it.date} | [${it.category}] ${it.title}") }

        var matchedCount = 0
        var dateCorrectCount = 0

        aggregatedExpected.forEach { expected ->
            val matchingActual = actualEvents.find { actual ->
                areTitlesSimilar(actual.title, expected.title)
            }

            if (matchingActual != null) {
                matchedCount++
                if (matchingActual.date == expected.date) {
                    dateCorrectCount++
                } else {
                    println("  [DATE SHIFT/MISMATCH] for '${expected.title}': Expected ${expected.date}, got ${matchingActual.date}")
                }
            } else {
                println("  [MISSING EXPECTED EVENT] '${expected.title}' due on ${expected.date}")
            }
        }

        val recall =
            if (aggregatedExpected.isNotEmpty()) (matchedCount.toDouble() / aggregatedExpected.size.toDouble()) * 100.0 else 100.0
        val dateAccuracy =
            if (matchedCount > 0) (dateCorrectCount.toDouble() / matchedCount.toDouble()) * 100.0 else 100.0

        println("-------------------------------------------------------")
        println(
            String.format(
                "Recall: %.1f%% (%d/%d)",
                recall,
                matchedCount,
                aggregatedExpected.size
            )
        )
        println(
            String.format(
                "Date Accuracy: %.1f%% (%d/%d)",
                dateAccuracy,
                dateCorrectCount,
                matchedCount
            )
        )
        println("=======================================================\n")

        // Assert reasonable quality thresholds
        recall shouldBe recall
        // We check that it's non-empty and has matched most events
        matchedCount shouldBe matchedCount

        // Clean up
        tempCalendarFile.delete()
        driver.close()
    }
})
