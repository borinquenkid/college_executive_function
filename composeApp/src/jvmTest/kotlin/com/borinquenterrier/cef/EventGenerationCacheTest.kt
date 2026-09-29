package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

/**
 * The analysis cache lives at the generation choke point so BOTH ingestion paths (studio staging
 * and the auto-push pipeline) skip the LLM for a source whose content was already analyzed.
 */
class EventGenerationCacheTest : FunSpec({

    class FakeCache : AnalysisCacheRepository {
        val store = mutableMapOf<String, CachedAnalysis>()
        override suspend fun getCached(hash: String): CachedAnalysis? = store[hash]
        override suspend fun putCache(analysis: CachedAnalysis) { store[analysis.sourceHash] = analysis }
        override suspend fun evict(hash: String) { store.remove(hash) }
    }

    fun deadline(title: String, date: String) = DayEvent(
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE,
        date = LocalDate.parse(date)
    )

    val source = SourceItem(
        title = "syllabus",
        fragments = listOf(SourceFragment(text = "Homework due", type = SourceType.TEXT)),
        category = SourceCategory.OTHER
    )

    test("re-extracting the same content is served from cache (no second LLM call)") {
        val ai = mockk<AIService>(relaxed = true)
        coEvery { ai.generateCalendarEvents(any()) } returns listOf(deadline("HW1 due", "2026-07-15"))
        val prefs = mockk<PreferencesPort> {
            coEvery { getPreferences() } returns
                StudyPreferences(semesterStart = "2026-06-01", semesterEnd = "2026-08-01")
        }
        val svc = EventGenerationService(
            aiService = ai,
            normalizationService = NormalizationService(),
            syllabusAuditor = mockk(relaxed = true),
            preferencesRepository = prefs,
            cacheRepository = FakeCache()
        )

        val first = runBlocking { svc.extractDeliverables(source) }
        val second = runBlocking { svc.extractDeliverables(source) }

        first.map { it.title } shouldContainExactlyInAnyOrder listOf("HW1 due")
        second.map { it.title } shouldContainExactlyInAnyOrder listOf("HW1 due")
        coVerify(exactly = 1) { ai.generateCalendarEvents(any()) }  // second call hit the cache
    }

    test("a cache hit still re-applies the current semester window") {
        val ai = mockk<AIService>(relaxed = true)
        // Cached under no-semester (both survive), then re-read with a summer window (one drops).
        coEvery { ai.generateCalendarEvents(any()) } returns listOf(
            deadline("In term", "2026-07-15"),
            deadline("Out of term", "2026-09-07")
        )
        var current = StudyPreferences()
        val prefs = mockk<PreferencesPort> { coEvery { getPreferences() } answers { current } }
        val svc = EventGenerationService(
            aiService = ai,
            normalizationService = NormalizationService(),
            syllabusAuditor = mockk(relaxed = true),
            preferencesRepository = prefs,
            cacheRepository = FakeCache()
        )

        runBlocking { svc.extractDeliverables(source) }  // populates the cache (no semester set)
        current = StudyPreferences(semesterStart = "2026-06-01", semesterEnd = "2026-08-01")
        val filtered = runBlocking { svc.extractDeliverables(source) }

        filtered.map { it.title } shouldContainExactlyInAnyOrder listOf("In term")
        coVerify(exactly = 1) { ai.generateCalendarEvents(any()) }
    }

    test("syllabus meeting pattern is applied on both fresh and cached paths, and both tag sourceId") {
        val ai = mockk<AIService>(relaxed = true)
        coEvery { ai.generateCalendarEvents(any()) } returns listOf(deadline("Problem Set #1", "2026-09-02"))
        var window = StudyPreferences(semesterStart = "2026-08-24", semesterEnd = "2026-09-04")
        val prefs = mockk<PreferencesPort> { coEvery { getPreferences() } answers { window } }
        val auditor = mockk<SyllabusAuditor> { coEvery { audit(any()) } returns emptyList() }
        val calendarHoliday = DayEvent(
            title = "Labor Day Holiday", source = EventSource.AI_GENERATED,
            category = AcademicCategory.HOLIDAY, date = LocalDate(2026, 9, 7), sourceId = "school calendar"
        )
        val svc = EventGenerationService(
            aiService = ai,
            normalizationService = NormalizationService(),
            syllabusAuditor = auditor,
            preferencesRepository = prefs,
            cacheRepository = FakeCache(),
            knownEvents = { listOf(calendarHoliday) }
        )
        val math = SourceItem(
            title = "MATH4209.pdf",
            fragments = listOf(SourceFragment(
                "MATH 4209 Meeting Days: MoWeFr Meeting Time: 09:00AM - 09:50AM. Problem Set #1 due 9/2/26",
                type = SourceType.TEXT
            )),
            category = SourceCategory.SYLLABUS
        )

        val fresh = runBlocking { svc.extractDeliverables(math) }
        val sessions = fresh.filterIsInstance<TimeEvent>()
        sessions.map { it.date.toString() } shouldContainExactlyInAnyOrder
            listOf("2026-08-24", "2026-08-26", "2026-08-28", "2026-08-31", "2026-09-02", "2026-09-04")
        fresh.all { it.sourceId == "MATH4209.pdf" && it.id != null } shouldBe true

        // Widen the window: the cached analysis is reused, but expansion follows the new window
        // and the other source's Labor Day holiday.
        window = StudyPreferences(semesterStart = "2026-08-24", semesterEnd = "2026-09-09")
        val cached = runBlocking { svc.extractDeliverables(math) }
        coVerify(exactly = 1) { ai.generateCalendarEvents(any()) }
        cached.filterIsInstance<TimeEvent>().map { it.date.toString() }.let {
            it.contains("2026-09-09") shouldBe true
            it.contains("2026-09-07") shouldBe false
        }
        cached.all { it.sourceId == "MATH4209.pdf" } shouldBe true
    }

    test("an event defaulted to today's date is dropped when the source never states it") {
        val ai = mockk<AIService>(relaxed = true)
        coEvery { ai.generateCalendarEvents(any()) } returns listOf(
            deadline("Exam 2", "2026-09-29"),
            deadline("Exam 1", "2026-10-02"),
        )
        val fixedClock = object : kotlin.time.Clock {
            override fun now() = kotlin.time.Instant.parse("2026-09-29T15:00:00Z")
        }
        val svc = EventGenerationService(
            aiService = ai,
            normalizationService = NormalizationService(),
            syllabusAuditor = mockk(relaxed = true),
            clock = fixedClock
        )
        val stat = SourceItem(
            title = "STAT5353.pdf",
            fragments = listOf(SourceFragment(
                "Exams on Friday, October 2nd and Friday, November 20th. Fall 2026", type = SourceType.TEXT
            )),
            category = SourceCategory.OTHER
        )
        runBlocking { svc.extractDeliverables(stat) }.map { it.title } shouldContainExactlyInAnyOrder listOf("Exam 1")
    }
})
