package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.mockk
import kotlinx.coroutines.runBlocking

/**
 * Thin AIService adapter that routes generateCalendarEvents to a GeminiAIService
 * with an injected MockEngine. Everything else is a no-op safe for tests.
 */
private class GeminiBackedAIService(private val gemini: GeminiAIService) : AIService {
    override fun isConfigured() = true
    override suspend fun generateChatResponse(prompt: String) = ""
    override suspend fun generateCalendarEvents(fragments: List<SourceFragment>) =
        gemini.generateCalendarEvents(fragments)
    override suspend fun generateStudyPlan(
        syllabusText: String,
        existingSchedule: String,
        preferences: StudyPreferences
    ) = gemini.generateStudyPlan(syllabusText, existingSchedule, preferences)
    override suspend fun analyzeDocument(text: String): String? = null
    override suspend fun decomposeTask(taskTitle: String, dueDate: String) = emptyList<DecomposedTask>()
    override suspend fun categorizeSource(text: String) = SourceCategory.OTHER
}

/**
 * End-to-end pipeline tests for AI event extraction.
 *
 * Gemini is mocked at the HTTP engine level so GeminiResponseParser, NormalizationService,
 * EventDeduplicator, and CalendarPushResolver all run with real code — only the outbound
 * network call is intercepted.
 *
 * Why GOOGLE_ACCESS_TOKEN is set and run_profile is NOT "test":
 *   run_profile = "test" makes SyncGate.isLive() = false → events saved as LOCAL_ONLY.
 *   CalendarPushResolver Phase-2 only deduplicates SYNCED events, so LOCAL_ONLY events
 *   bypass the guard. To exercise the guard events must be SYNCED, requiring isLive() = true:
 *   fake token → isGoogleLinked() = true, default run_profile → isLiveSyncEnabled() = true.
 *   The mocked RemoteCalendarRepository makes saveEvent() a silent no-op, so the local
 *   repo receives the SYNCED copy.
 */
class EventExtractionPipelineTest : FunSpec({

    val jsonHeader = headersOf(HttpHeaders.ContentType, "application/json")
    val singleModel =
        """{"models":[{"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]}]}"""

    afterEach {
        GeminiAIService.clearBlacklistForTesting()
        GeminiRequestExecutor.clearRateLimitResetForTesting()
    }

    fun geminiBody(eventsJson: String): String {
        val escaped = eventsJson.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"candidates":[{"content":{"parts":[{"text":"$escaped"}]}}]}"""
    }

    fun makeMockAIService(eventsJson: String): AIService {
        val body = geminiBody(eventsJson)
        val engine = MockEngine { req ->
            if (req.url.encodedPath.contains("/models") && !req.url.encodedPath.contains(":generateContent")) {
                respond(singleModel, HttpStatusCode.OK, jsonHeader)
            } else {
                respond(body, HttpStatusCode.OK, jsonHeader)
            }
        }
        return GeminiBackedAIService(
            GeminiAIService(
                apiKey = "test-key",
                customClient = HttpClient(engine) { install(ContentNegotiation) { json() } },
                delayFn = {}
            )
        )
    }

    fun setupAgent(
        database: com.borinquenterrier.cef.db.AppDatabase,
        aiService: AIService
    ): Pair<EventAgent, SqlDelightLocalCalendarRepository> {
        val settings = MapSettings()
        settings.putString("GOOGLE_ACCESS_TOKEN", "fake-test-token")
        val localRepo = SqlDelightLocalCalendarRepository(database, settings)
        val calendarAgent = CalendarAgent(localRepo, mockk<RemoteCalendarRepository>(relaxed = true))
        return Pair(
            EventAgent(
                aiService = aiService,
                repository = calendarAgent,
                database = database,
                normalizationService = NormalizationService(),
                logger = Logger(settings)
            ),
            localRepo
        )
    }

    fun dayEventJson(title: String, date: String = "2027-09-15") =
        """{"title":"$title","type":"DAY","date":"$date","category":"DEADLINE"}"""

    fun singleEventJson(title: String, date: String = "2027-09-15") =
        "[${dayEventJson(title, date)}]"

    val testSource = SourceItem(
        title = "CSCI 101 Syllabus",
        fragments = listOf(
            SourceFragment(
                text = "Homework 1 due Sept 15 2027.",
                type = SourceType.TEXT
            )
        ),
        category = SourceCategory.OTHER
    )

    // ── Parameterized case definition ─────────────────────────────────────────

    data class PipelineCase(
        val name: String,
        val passes: List<String>,  // Gemini events JSON for each consecutive extraction
        val expectedCount: Int     // rows expected in DB after all passes
    )

    // All title variants that EventDeduplicator.submissionCanonical() normalises to the same
    // canonical form as "Homework 1 Due". Adding a new normalization rule to submissionCanonical
    // means adding its variants here — the Cartesian product covers all ordered pairs automatically.
    val titleVariants = listOf(
        // Casing
        "Homework 1 Due",
        "homework 1 due",
        "HOMEWORK 1 DUE",
        // Original verb prefixes
        "Submit Homework 1 Due",
        "submit homework 1 due",
        "Complete Homework 1 Due",
        "complete homework 1 due",
        "Upload Homework 1 Due",
        "upload homework 1 due",
        "Post Homework 1 Due",
        "post homework 1 due",
        // Possessive
        "Your Homework 1 Due",
        "your homework 1 due",
        // Extended verb prefixes
        "Turn In Homework 1 Due",
        "Hand In Homework 1 Due",
        "Finish Homework 1 Due",
        "Do Homework 1 Due",
        // Verb + article
        "Submit the Homework 1 Due",
        "Turn in the Homework 1 Due",
        // Trailing noise
        "Homework 1 Due Date",
        "Homework 1 Deadline",
        "Homework 1 Due.",
        // Number formatting
        "Homework #1 Due",
        // Internal whitespace
        "Homework  1  Due",
    )

    // All ordered pairs of (firstPassTitle, secondPassTitle) — full Cartesian product.
    // Every pair should suppress the second push since all variants are canonical-equal.
    val duplicateSuppression: List<PipelineCase> = titleVariants.flatMap { first ->
        titleVariants.map { second ->
            PipelineCase(
                name = "dedup: \"$first\" then \"$second\" → 1 row",
                passes = listOf(singleEventJson(first), singleEventJson(second)),
                expectedCount = 1
            )
        }
    }

    // Different title on the same date → genuinely new event
    val distinctTitle: List<PipelineCase> = titleVariants.map { first ->
        PipelineCase(
            name = "new event: \"$first\" then \"Homework 2 Due\" → 2 rows",
            passes = listOf(
                singleEventJson(first),
                singleEventJson("Homework 2 Due")
            ),
            expectedCount = 2
        )
    }

    // Same canonical title but different date → new event (different day = different deadline)
    val distinctDate: List<PipelineCase> = titleVariants.map { variant ->
        PipelineCase(
            name = "new date: \"$variant\" on 09-15, then same title on 10-15 → 2 rows",
            passes = listOf(
                singleEventJson(variant, "2027-09-15"),
                singleEventJson(variant, "2027-10-15")
            ),
            expectedCount = 2
        )
    }

    val allCases: List<PipelineCase> = duplicateSuppression + distinctTitle + distinctDate

    // ── Register one test per case ────────────────────────────────────────────

    allCases.forEach { case ->
        test(case.name) {
            val database = createTestDatabase()
            var lastRepo: SqlDelightLocalCalendarRepository? = null

            case.passes.forEach { eventsJson ->
                val (agent, repo) = setupAgent(database, makeMockAIService(eventsJson))
                lastRepo = repo
                runBlocking { agent.extractDeliverables(testSource) }
                runBlocking { agent.pushToCalendar() }
            }

            runBlocking { lastRepo!!.getAllEvents() } shouldHaveSize case.expectedCount
        }
    }
})
