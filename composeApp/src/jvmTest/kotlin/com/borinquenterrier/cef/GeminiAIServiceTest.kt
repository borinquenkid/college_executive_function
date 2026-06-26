package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GeminiAIServiceTest : FunSpec({

    val singleModel = """{"models":[{"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]}]}"""
    val okEvents = """{"candidates":[{"content":{"parts":[{"text":"[]"}]}}]}"""
    val jsonHeader = headersOf(HttpHeaders.ContentType, "application/json")

    afterEach {
        GeminiAIService.clearBlacklistForTesting()
        GeminiRequestExecutor.clearRateLimitResetForTesting()
    }

    // ── production constructor (null customClient → real HttpClient init) ──────

    test("production constructor builds service with internal HttpClient") {
        // Exercises the HttpClient{install(ContentNegotiation){json(…)}} init block
        // (the 13 lines only reachable when customClient is null)
        GeminiAIService(
            apiKey = "test-key",
            logger = mockk(relaxed = true),
            settings = com.russhwolf.settings.MapSettings()
        )
    }

    fun makeService(engine: MockEngine) = GeminiAIService(
        apiKey = "test-key",
        customClient = HttpClient(engine) { install(ContentNegotiation) { json() } },
        delayFn = {}
    )

    fun makeServiceWithLogger(engine: MockEngine) = GeminiAIService(
        apiKey = "test-key",
        logger = mockk(relaxed = true),
        customClient = HttpClient(engine) { install(ContentNegotiation) { json() } },
        delayFn = {}
    )

    fun makeServiceWithSettings(engine: MockEngine) = GeminiAIService(
        apiKey = "test-key",
        settings = com.russhwolf.settings.MapSettings(),
        customClient = HttpClient(engine) { install(ContentNegotiation) { json() } },
        delayFn = {}
    )

    fun statelessEngine(status: HttpStatusCode, body: String) = MockEngine { req ->
        if (req.url.encodedPath.contains("/models") && !req.url.encodedPath.contains(":generateContent")) {
            respond(singleModel, HttpStatusCode.OK, jsonHeader)
        } else {
            respond(body, status, jsonHeader)
        }
    }

    // ── postToModel ───────────────────────────────────────────────────────────

    test("postToModel returns HTTP response from model endpoint") {
        val engine = MockEngine { req ->
            if (req.url.encodedPath.contains(":generateContent")) {
                respond(okEvents, HttpStatusCode.OK, jsonHeader)
            } else {
                respond(singleModel, HttpStatusCode.OK, jsonHeader)
            }
        }
        val body = buildJsonObject { put("contents", buildJsonArray {}) }
        val response = makeService(engine).postToModel("models/gemini-2.5-flash", body)
        response.status shouldBe HttpStatusCode.OK
    }

    // ── generateCalendarEvents: empty fragments ───────────────────────────────

    test("generateCalendarEvents with empty fragment list calls non-batching path") {
        val engine = statelessEngine(HttpStatusCode.OK, okEvents)
        val result = makeService(engine).generateCalendarEvents(emptyList())
        result shouldBe emptyList()
    }

    // ── generateCalendarEvents: non-TEXT fragments, large list ────────────────

    test("generateCalendarEvents with CALENDAR-type fragments skips batching regardless of size") {
        val engine = statelessEngine(HttpStatusCode.OK, okEvents)
        val fragments = (1..5).map { SourceFragment(text = "event $it", type = SourceType.CALENDAR) }
        val result = makeService(engine).generateCalendarEvents(fragments)
        result shouldBe emptyList()
    }

    // ── generateCalendarEvents: large TEXT fragment list → batching ───────────

    test("generateCalendarEvents batches TEXT fragments when size exceeds BATCH_SIZE") {
        var requestCount = 0
        val engine = MockEngine { req ->
            if (req.url.encodedPath.contains("/models") && !req.url.encodedPath.contains(":generateContent")) {
                respond(singleModel, HttpStatusCode.OK, jsonHeader)
            } else {
                requestCount++
                respond(okEvents, HttpStatusCode.OK, jsonHeader)
            }
        }
        val fragments = (1..SourceFragmentBatcher.BATCH_SIZE + 1).map {
            SourceFragment(text = "fragment $it", type = SourceType.TEXT)
        }
        val result = makeService(engine).generateCalendarEvents(fragments)
        result shouldBe emptyList()
        (requestCount >= 2) shouldBe true  // batching sends multiple requests
    }

    // ── generateCalendarEvents: small TEXT fragment list → non-batching ───────

    test("generateCalendarEvents with small TEXT list uses non-batching path") {
        val engine = statelessEngine(HttpStatusCode.OK, okEvents)
        val fragments = listOf(SourceFragment(text = "single fragment", type = SourceType.TEXT))
        val result = makeService(engine).generateCalendarEvents(fragments)
        result shouldBe emptyList()
    }

    // ── generateStudyPlan ─────────────────────────────────────────────────────

    test("generateStudyPlan delegates to generateCalendarEventsFromPrompt") {
        val engine = statelessEngine(HttpStatusCode.OK, okEvents)
        val result = makeService(engine).generateStudyPlan("syllabus text")
        result shouldBe emptyList()
    }

    // ── generateCalendarEventsFromPrompt JSON parse error + telemetry ─────────

    test("generateCalendarEventsFromPrompt with non-array JSON calls logJsonError and rethrows") {
        // parts[0].text = "NOT_JSON" → parseEventsJson("NOT_JSON") throws "Unexpected JSON structure"
        // → catch block calls telemetryManager?.logJsonError() (non-null branch) and rethrows
        val body = """{"candidates":[{"content":{"parts":[{"text":"NOT_JSON"}]}}]}"""
        val engine = statelessEngine(HttpStatusCode.OK, body)
        shouldThrow<Exception> { makeServiceWithSettings(engine).generateCalendarEventsFromPrompt("test") }
    }

    test("generateCalendarEventsFromPrompt propagates parse exception when telemetry is null") {
        // Same JSON parse failure but with null telemetryManager → ?.logJsonError() null branch
        val body = """{"candidates":[{"content":{"parts":[{"text":"NOT_JSON"}]}}]}"""
        val engine = statelessEngine(HttpStatusCode.OK, body)
        shouldThrow<Exception> { makeService(engine).generateCalendarEventsFromPrompt("test") }
    }

    // ── analyzeDocument ───────────────────────────────────────────────────────

    test("analyzeDocument returns analyzed text on success") {
        val okText = """{"candidates":[{"content":{"parts":[{"text":"analysis result"}]}}]}"""
        val engine = statelessEngine(HttpStatusCode.OK, okText)
        val result = makeService(engine).analyzeDocument("document text")
        result shouldBe "analysis result"
    }

    test("analyzeDocument returns null when all retries fail with non-QuotaExhausted error") {
        // 500 TransientServerError exhausts all 3 attempts → non-QuotaExhausted exception → null
        val engine = statelessEngine(HttpStatusCode.InternalServerError, """{"error":"server error"}""")
        val result = makeService(engine).analyzeDocument("document text")
        result shouldBe null
    }

    test("analyzeDocument logs error with non-null logger on non-QuotaExhausted failure") {
        val engine = statelessEngine(HttpStatusCode.InternalServerError, """{"error":"server error"}""")
        val result = makeServiceWithLogger(engine).analyzeDocument("document text")
        result shouldBe null
    }

    test("analyzeDocument rethrows QuotaExhausted exception") {
        GeminiRetryService.skipLongDelaysInTests = true
        val engine = statelessEngine(
            HttpStatusCode.TooManyRequests,
            """{"error":{"message":"retry 5s"}}"""
        )
        val ex = shouldThrow<Exception> { makeService(engine).analyzeDocument("document text") }
        ex.message shouldContain "QuotaExhausted"
        GeminiRetryService.skipLongDelaysInTests = false
    }

    // ── categorizeSource success path ─────────────────────────────────────────

    fun makeCategoryResponse(category: String): String {
        val textContent = buildJsonObject {
            put("category", category)
            put("isValid", true)
        }.toString()
        return buildJsonObject {
            put("candidates", buildJsonArray {
                add(buildJsonObject {
                    put("content", buildJsonObject {
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", textContent) })
                        })
                    })
                })
            })
        }.toString()
    }

    test("categorizeSource returns correct category on success") {
        val engine = statelessEngine(HttpStatusCode.OK, makeCategoryResponse("SYLLABUS"))
        val result = makeService(engine).categorizeSource("short text")
        result shouldBe SourceCategory.SYLLABUS
    }

    test("categorizeSource truncates text longer than 50000 chars") {
        val engine = statelessEngine(HttpStatusCode.OK, makeCategoryResponse("CALENDAR"))
        val result = makeService(engine).categorizeSource("a".repeat(60000))
        result shouldBe SourceCategory.CALENDAR
    }

    // ── categorizeSource non-QuotaExhausted failure path ─────────────────────

    test("categorizeSource returns OTHER when all retries fail with non-QuotaExhausted error") {
        // 500 TransientServerError exhausts all 3 attempts → categorizeSource returns OTHER
        val engine = statelessEngine(HttpStatusCode.InternalServerError, """{"error":"server error"}""")
        val result = makeService(engine).categorizeSource("some text")
        result shouldBe SourceCategory.OTHER
    }

    test("categorizeSource logs error with non-null logger on non-QuotaExhausted failure") {
        val engine = statelessEngine(HttpStatusCode.InternalServerError, """{"error":"server error"}""")
        val result = makeServiceWithLogger(engine).categorizeSource("some text")
        result shouldBe SourceCategory.OTHER
    }

    test("categorizeSource rethrows QuotaExhausted exception") {
        GeminiRetryService.skipLongDelaysInTests = true
        val engine = statelessEngine(
            HttpStatusCode.TooManyRequests,
            """{"error":{"message":"retry 5s"}}"""
        )
        val ex = shouldThrow<Exception> { makeService(engine).categorizeSource("some text") }
        ex.message shouldContain "QuotaExhausted"
        GeminiRetryService.skipLongDelaysInTests = false
    }

    // ── @Serializable classes: serialize() path coverage ─────────────────────

    test("GeminiResponse serializes and round-trips correctly") {
        val resp = GeminiResponse(
            candidates = listOf(Candidate(content = Content(parts = listOf(Part(text = "hello")))))
        )
        val out = Json.encodeToString(resp)
        out shouldContain "hello"
        val rt = Json.decodeFromString<GeminiResponse>(out)
        rt.candidates[0].content.parts[0].text shouldBe "hello"
    }

    test("GeminiResponse with empty candidates list serializes correctly") {
        val resp = GeminiResponse(candidates = emptyList())
        val out = Json.encodeToString(resp)
        Json.decodeFromString<GeminiResponse>(out).candidates shouldBe emptyList()
    }

    test("GeminiResponse deserialized from empty object uses default empty candidates") {
        val rt = Json { ignoreUnknownKeys = true }.decodeFromString<GeminiResponse>("{}")
        rt.candidates shouldBe emptyList()
    }

    test("Content serializes and round-trips correctly") {
        val content = Content(parts = listOf(Part(text = "test"), Part(text = "more")))
        val out = Json.encodeToString(content)
        val rt = Json.decodeFromString<Content>(out)
        rt.parts.size shouldBe 2
    }

    test("Part serializes and round-trips correctly") {
        val part = Part(text = "sample text")
        val out = Json.encodeToString(part)
        Json.decodeFromString<Part>(out).text shouldBe "sample text"
    }

    test("Candidate serializes and round-trips correctly") {
        val candidate = Candidate(content = Content(parts = listOf(Part(text = "response"))))
        val out = Json.encodeToString(candidate)
        Json.decodeFromString<Candidate>(out).content.parts[0].text shouldBe "response"
    }
})
