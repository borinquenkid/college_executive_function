package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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
})
