package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate

class SemesterFilterTest : FunSpec({

    // ---- helpers -------------------------------------------------------------------------

    fun makeEvent(title: String, date: LocalDate) = DayEvent(
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE,
        date = date
    )

    // ---- SourceAdder semester filter tests -----------------------------------------------

    context("SourceAdder semester filter") {

        lateinit var testScope: CoroutineScope
        lateinit var analysisCacheRepo: SqlDelightAnalysisCacheRepository
        lateinit var mockAi: AIService
        val settings = MapSettings()
        val logger = Logger(settings)

        beforeEach {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            AppDatabase.Schema.create(driver)
            listOf(
                "ALTER TABLE SourceEntity ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'",
                "CREATE TABLE IF NOT EXISTS AnalysisCacheEntity (sourceHash TEXT NOT NULL PRIMARY KEY, cachedEventsJson TEXT NOT NULL DEFAULT '', cachedMetadataJson TEXT, createdAt INTEGER NOT NULL DEFAULT 0)",
                "ALTER TABLE SourceEntity ADD COLUMN metadataJson TEXT"
            ).forEach { sql -> try { driver.execute(null, sql, 0, null) } catch (_: Exception) {} }
            val db = AppDatabase(driver)
            analysisCacheRepo = SqlDelightAnalysisCacheRepository(db)
            mockAi = mockk(relaxed = true)
            testScope = CoroutineScope(Dispatchers.Default)
        }

        afterEach { testScope.cancel() }

        test("events within semester range are passed through") {
            val semesterStart = LocalDate(2026, 8, 20)
            val semesterEnd = LocalDate(2026, 12, 15)
            val prefs = StudyPreferences(semesterStart = "2026-08-20", semesterEnd = "2026-12-15")
            val prefsPort: PreferencesPort = mockk {
                coEvery { getPreferences() } returns prefs
            }

            val midterm = makeEvent("Midterm", LocalDate(2026, 10, 1))
            val final = makeEvent("Final", LocalDate(2026, 12, 10))
            val mockEventGenService: EventGenerationService = mockk {
                coEvery { extractDeliverables(any()) } returns listOf(midterm, final)
            }

            val collected = mutableListOf<Event>()
            val done = CompletableDeferred<Unit>()
            val sourceAdder = SourceAdder(
                aiService = mockAi,
                eventGenerationService = mockEventGenService,
                contextAgent = mockk(relaxed = true),
                logger = logger,
                scope = testScope,
                cacheRepository = analysisCacheRepo,
                sourceRepository = mockk(relaxed = true),
                onEventsAdded = { events ->
                    collected.addAll(events)
                    done.complete(Unit)
                },
                preferencesRepository = prefsPort
            )

            coEvery { mockAi.isConfigured() } returns true
            sourceAdder.addSource(SourceItem("Test", emptyList(), SourceCategory.OTHER))
            withTimeout(5000) { done.await() }
            collected shouldHaveSize 2
        }

        test("events outside semester range are dropped") {
            val prefs = StudyPreferences(semesterStart = "2026-08-20", semesterEnd = "2026-12-15")
            val prefsPort: PreferencesPort = mockk {
                coEvery { getPreferences() } returns prefs
            }

            val springEvent = makeEvent("Spring Orientation", LocalDate(2027, 1, 10))
            val summerEvent = makeEvent("Summer Workshop", LocalDate(2026, 6, 1))
            val mockEventGenService: EventGenerationService = mockk {
                coEvery { extractDeliverables(any()) } returns listOf(springEvent, summerEvent)
            }

            val collected = mutableListOf<Event>()
            val done = CompletableDeferred<Unit>()
            var callCount = 0
            val sourceAdder = SourceAdder(
                aiService = mockAi,
                eventGenerationService = mockEventGenService,
                contextAgent = mockk(relaxed = true),
                logger = logger,
                scope = testScope,
                cacheRepository = analysisCacheRepo,
                sourceRepository = mockk(relaxed = true),
                onEventsAdded = { events ->
                    collected.addAll(events)
                    callCount++
                    done.complete(Unit)
                },
                preferencesRepository = prefsPort
            )

            coEvery { mockAi.isConfigured() } returns true
            sourceAdder.addSource(SourceItem("Test", emptyList(), SourceCategory.OTHER))
            withTimeout(5000) { done.await() }
            collected.shouldBeEmpty()
        }

        test("mixed events: only in-range survive") {
            val prefs = StudyPreferences(semesterStart = "2026-08-20", semesterEnd = "2026-12-15")
            val prefsPort: PreferencesPort = mockk {
                coEvery { getPreferences() } returns prefs
            }

            val inRange = makeEvent("Midterm", LocalDate(2026, 10, 5))
            val outOfRange = makeEvent("Spring Final", LocalDate(2027, 5, 1))
            val mockEventGenService: EventGenerationService = mockk {
                coEvery { extractDeliverables(any()) } returns listOf(inRange, outOfRange)
            }

            val collected = mutableListOf<Event>()
            val done = CompletableDeferred<Unit>()
            val sourceAdder = SourceAdder(
                aiService = mockAi,
                eventGenerationService = mockEventGenService,
                contextAgent = mockk(relaxed = true),
                logger = logger,
                scope = testScope,
                cacheRepository = analysisCacheRepo,
                sourceRepository = mockk(relaxed = true),
                onEventsAdded = { events ->
                    collected.addAll(events)
                    done.complete(Unit)
                },
                preferencesRepository = prefsPort
            )

            coEvery { mockAi.isConfigured() } returns true
            sourceAdder.addSource(SourceItem("Test", emptyList(), SourceCategory.OTHER))
            withTimeout(5000) { done.await() }
            collected shouldHaveSize 1
            (collected.first() as DayEvent).title shouldBe "Midterm"
        }

        test("no filter when semester not configured — all events pass through") {
            val prefsPort: PreferencesPort = mockk {
                coEvery { getPreferences() } returns StudyPreferences()
            }

            val e1 = makeEvent("Event A", LocalDate(2025, 1, 1))
            val e2 = makeEvent("Event B", LocalDate(2027, 12, 31))
            val mockEventGenService: EventGenerationService = mockk {
                coEvery { extractDeliverables(any()) } returns listOf(e1, e2)
            }

            val collected = mutableListOf<Event>()
            val done = CompletableDeferred<Unit>()
            val sourceAdder = SourceAdder(
                aiService = mockAi,
                eventGenerationService = mockEventGenService,
                contextAgent = mockk(relaxed = true),
                logger = logger,
                scope = testScope,
                cacheRepository = analysisCacheRepo,
                sourceRepository = mockk(relaxed = true),
                onEventsAdded = { events ->
                    collected.addAll(events)
                    done.complete(Unit)
                },
                preferencesRepository = prefsPort
            )

            coEvery { mockAi.isConfigured() } returns true
            sourceAdder.addSource(SourceItem("Test", emptyList(), SourceCategory.OTHER))
            withTimeout(5000) { done.await() }
            collected shouldHaveSize 2
        }

        test("invalid semester date strings are treated as no filter") {
            val prefs = StudyPreferences(semesterStart = "not-a-date", semesterEnd = "also-bad")
            val prefsPort: PreferencesPort = mockk {
                coEvery { getPreferences() } returns prefs
            }

            val event = makeEvent("Exam", LocalDate(2026, 10, 1))
            val mockEventGenService: EventGenerationService = mockk {
                coEvery { extractDeliverables(any()) } returns listOf(event)
            }

            val collected = mutableListOf<Event>()
            val done = CompletableDeferred<Unit>()
            val sourceAdder = SourceAdder(
                aiService = mockAi,
                eventGenerationService = mockEventGenService,
                contextAgent = mockk(relaxed = true),
                logger = logger,
                scope = testScope,
                cacheRepository = analysisCacheRepo,
                sourceRepository = mockk(relaxed = true),
                onEventsAdded = { events ->
                    collected.addAll(events)
                    done.complete(Unit)
                },
                preferencesRepository = prefsPort
            )

            coEvery { mockAi.isConfigured() } returns true
            sourceAdder.addSource(SourceItem("Test", emptyList(), SourceCategory.OTHER))
            withTimeout(5000) { done.await() }
            collected shouldHaveSize 1
        }
    }

    // ---- PreferencesRepository.readSync tests -------------------------------------------

    context("PreferencesRepository.readSync") {

        test("returns default StudyPreferences when settings is empty") {
            val repo = PreferencesRepository(MapSettings())
            val prefs = repo.readSync()
            prefs shouldBe StudyPreferences()
        }

        test("round-trips semesterStart and semesterEnd through save/readSync") {
            val settings = MapSettings()
            val repo = PreferencesRepository(settings)
            val original = StudyPreferences(semesterStart = "2026-08-20", semesterEnd = "2026-12-15")
            kotlinx.coroutines.runBlocking { repo.savePreferences(original) }
            val read = repo.readSync()
            read.semesterStart shouldBe "2026-08-20"
            read.semesterEnd shouldBe "2026-12-15"
        }

        test("readSync survives corrupt JSON by returning defaults") {
            val settings = MapSettings()
            settings.putString("STUDY_PREFERENCES", "{invalid json}")
            val repo = PreferencesRepository(settings)
            val prefs = repo.readSync()
            prefs shouldBe StudyPreferences()
        }

        test("flow is initialized synchronously from readSync") {
            val settings = MapSettings()
            val repo = PreferencesRepository(settings)
            kotlinx.coroutines.runBlocking {
                repo.savePreferences(StudyPreferences(semesterStart = "2026-01-10"))
            }
            val repo2 = PreferencesRepository(settings)
            repo2.flow.value.semesterStart shouldBe "2026-01-10"
        }
    }

    // ---- SettingsPreferencesParser semester params --------------------------------------

    context("SettingsPreferencesParser semester passthrough") {

        val baseArgs = listOf("9", "21", "12", "13", "18", "19", "2", "15", false, "", "")

        fun parse(semStart: String?, semEnd: String?, current: StudyPreferences = StudyPreferences()) =
            SettingsPreferencesParser.parse(
                studyStartStr = "9",
                studyEndStr = "21",
                lunchStartStr = "12",
                lunchEndStr = "13",
                dinnerStartStr = "18",
                dinnerEndStr = "19",
                maxStudyBlockStr = "2",
                preferredBreakStr = "15",
                shareAnonymousBugReports = false,
                googleCalendarId = "",
                googleCalendarName = "",
                currentPrefs = current,
                semesterStart = semStart,
                semesterEnd = semEnd
            )

        test("sets semesterStart and semesterEnd when provided") {
            val result = parse("2026-08-20", "2026-12-15")
            result.semesterStart shouldBe "2026-08-20"
            result.semesterEnd shouldBe "2026-12-15"
        }

        test("preserves existing values when params are omitted (use defaults)") {
            val current = StudyPreferences(semesterStart = "2026-08-20", semesterEnd = "2026-12-15")
            val result = SettingsPreferencesParser.parse(
                studyStartStr = "9", studyEndStr = "21",
                lunchStartStr = "12", lunchEndStr = "13",
                dinnerStartStr = "18", dinnerEndStr = "19",
                maxStudyBlockStr = "2", preferredBreakStr = "15",
                shareAnonymousBugReports = false,
                googleCalendarId = "", googleCalendarName = "",
                currentPrefs = current
            )
            result.semesterStart shouldBe "2026-08-20"
            result.semesterEnd shouldBe "2026-12-15"
        }

        test("blank string is normalized to null") {
            val result = parse("", "")
            result.semesterStart shouldBe null
            result.semesterEnd shouldBe null
        }

        test("whitespace-only string is normalized to null") {
            val result = parse("  ", "  ")
            result.semesterStart shouldBe null
            result.semesterEnd shouldBe null
        }

        test("clears semester when empty string overrides a previous value") {
            val current = StudyPreferences(semesterStart = "2026-08-20", semesterEnd = "2026-12-15")
            val result = parse("", "", current)
            result.semesterStart shouldBe null
            result.semesterEnd shouldBe null
        }
    }
})
