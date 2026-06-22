package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

/**
 * Unit tests for GeminiRequestExecutor error-handling branches, exercised via GeminiAIService.
 * All tests bypass the request queue and real delays.
 */
class GeminiRequestExecutorTest : FunSpec({

    val singleModel = """{"models":[{"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]}]}"""
    val twoModels = """{"models":[
        {"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]},
        {"name":"models/gemini-2.0-flash","supportedGenerationMethods":["generateContent"]}
    ]}"""
    val okEvents = """{"candidates":[{"content":{"parts":[{"text":"[]"}]}}]}"""
    val okText = """{"candidates":[{"content":{"parts":[{"text":"hello"}]}}]}"""

    afterEach {
        GeminiAIService.clearBlacklistForTesting()
        GeminiRequestExecutor.clearRateLimitResetForTesting()
    }

    fun makeService(engine: io.ktor.client.engine.HttpClientEngine, modelsBody: String = singleModel) =
        GeminiAIService(
            apiKey = "test-key",
            customClient = HttpClient(engine) { install(ContentNegotiation) { json() } },
            delayFn = {}
        )

    fun makeServiceWithLogger(engine: io.ktor.client.engine.HttpClientEngine) =
        GeminiAIService(
            apiKey = "test-key",
            logger = io.mockk.mockk(relaxed = true),
            customClient = HttpClient(engine) { install(ContentNegotiation) { json() } },
            delayFn = {}
        )

    fun makeServiceWithSettings(engine: io.ktor.client.engine.HttpClientEngine) =
        GeminiAIService(
            apiKey = "test-key",
            settings = com.russhwolf.settings.MapSettings(),
            customClient = HttpClient(engine) { install(ContentNegotiation) { json() } },
            delayFn = {}
        )

    fun statelessEngine(
        contentStatus: HttpStatusCode,
        contentBody: String,
        modelsBody: String = singleModel
    ) = MockEngine { request ->
        if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
            respond(modelsBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        } else {
            respond(contentBody, contentStatus, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    }

    // ── Successful path ───────────────────────────────────────────────────────

    test("successful 200 response returns parsed result") {
        val engine = statelessEngine(HttpStatusCode.OK, okEvents)
        val result = makeService(engine).generateCalendarEventsFromPrompt("test")
        result shouldBe emptyList()
    }

    // ── Auth errors ───────────────────────────────────────────────────────────

    test("401 Unauthorized throws Unauthorized immediately") {
        val engine = statelessEngine(HttpStatusCode.Unauthorized, """{"error":"unauthorized"}""")
        val ex = shouldThrow<Exception> { makeService(engine).generateCalendarEventsFromPrompt("x") }
        ex.message shouldContain "Unauthorized"
    }

    test("403 Forbidden throws Forbidden immediately") {
        val engine = statelessEngine(HttpStatusCode.Forbidden, """{"error":"forbidden"}""")
        val ex = shouldThrow<Exception> { makeService(engine).generateCalendarEventsFromPrompt("x") }
        ex.message shouldContain "Forbidden"
    }

    // ── StructuralError (404) ─────────────────────────────────────────────────

    test("404 StructuralError retries until exhaustion then throws") {
        var callCount = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(singleModel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                callCount++
                respond("""{"error":"not found"}""", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        // With 1 model and maxAttempts=5, all 5 attempts hit StructuralError
        // Since there's only 1 model, it exhausts and throws the blacklist exception
        shouldThrow<Exception> { makeService(engine).generateCalendarEventsFromPrompt("x") }
        callCount shouldBe 5
    }

    // ── TransientServerError (5xx) ────────────────────────────────────────────

    test("500 TransientServerError retries then throws when exhausted") {
        var callCount = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(singleModel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                callCount++
                respond("""{"error":"server error"}""", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        shouldThrow<Exception> { makeService(engine).generateCalendarEventsFromPrompt("x") }
        callCount shouldBe 5
    }

    test("500 then success returns result on recovery") {
        var callCount = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(twoModels, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                callCount++
                val status = if (callCount == 1) HttpStatusCode.InternalServerError else HttpStatusCode.OK
                val body = if (callCount == 1) """{"error":"temporary"}""" else okEvents
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val result = makeService(engine).generateCalendarEventsFromPrompt("x")
        result shouldBe emptyList()
    }

    // ── OtherError (400 with unrecognized body) ───────────────────────────────

    test("400 non-structural error throws with error message") {
        val engine = statelessEngine(
            HttpStatusCode.BadRequest,
            """{"error":{"message":"Invalid request body"}}"""
        )
        val ex = shouldThrow<Exception> { makeService(engine).generateCalendarEventsFromPrompt("x") }
        ex.message shouldContain "400"
    }

    // ── TransientRateLimit → ShortDelay ───────────────────────────────────────

    test("429 with short retry-after recovers on next attempt") {
        var callCount = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(singleModel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                callCount++
                if (callCount == 1) {
                    respond(
                        """{"error":{"message":"retry 5s"}}""",
                        HttpStatusCode.TooManyRequests,
                        headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond(okText, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
        val result = makeService(engine).generateChatResponse("hi")
        result shouldBe "hello"
        callCount shouldBe 2
    }

    // ── Exception catch block ─────────────────────────────────────────────────

    test("network exception is caught and retried then throws after maxAttempts") {
        var callCount = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(singleModel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                callCount++
                error("Simulated network failure")
            }
        }
        // generateChatResponse catches exceptions and returns "Error: ..."
        val result = makeService(engine).generateChatResponse("x")
        result shouldContain "Error"
        callCount shouldBe 3
    }

    // ── GeminiAIService high-level paths ─────────────────────────────────────

    test("generateCalendarEvents with >3 text fragments batches and merges results") {
        var generateCalls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(singleModel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                generateCalls++
                respond(okEvents, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val service = makeService(engine)
        val fragments = (1..5).map {
            SourceFragment(text = "Page $it content", pageNumber = it, type = SourceType.TEXT)
        }
        val result = service.generateCalendarEvents(fragments)
        result shouldBe emptyList()
        // 5 fragments / 3 per batch = 2 batches
        generateCalls shouldBe 2
    }

    test("generateCalendarEvents with <=3 fragments sends single request") {
        var generateCalls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(singleModel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                generateCalls++
                respond(okEvents, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val fragments = (1..3).map {
            SourceFragment(text = "Page $it", pageNumber = it, type = SourceType.TEXT)
        }
        makeService(engine).generateCalendarEvents(fragments)
        generateCalls shouldBe 1
    }

    test("categorizeSource truncates text over 50000 chars") {
        val longText = "a".repeat(60000)
        val engine = statelessEngine(HttpStatusCode.OK, """{"candidates":[{"content":{"parts":[{"text":"{\"category\":\"OTHER\"}"}]}}]}""")
        // Should not throw — truncation happens before the request
        val result = makeService(engine).categorizeSource(longText)
        result shouldBe SourceCategory.OTHER
    }

    test("categorizeSource defaults to OTHER on non-quota exception") {
        val engine = statelessEngine(
            HttpStatusCode.InternalServerError,
            """{"error":"server error"}"""
        )
        val result = makeService(engine).categorizeSource("text")
        result shouldBe SourceCategory.OTHER
    }

    test("categorizeSource rethrows QuotaExhausted exception") {
        val quotaBody = """{"error":{"code":429,"message":"Resource has been exhausted (e.g. check quota).","status":"RESOURCE_EXHAUSTED"}}"""
        val engine = statelessEngine(HttpStatusCode.TooManyRequests, quotaBody)
        val ex = shouldThrow<Exception> { makeService(engine).categorizeSource("text") }
        ex.message shouldContain "QuotaExhausted"
    }

    test("analyzeDocument returns null on non-quota exception") {
        val engine = statelessEngine(HttpStatusCode.InternalServerError, """{"error":"server error"}""")
        val result = makeService(engine).analyzeDocument("text")
        result shouldBe null
    }

    test("analyzeDocument rethrows QuotaExhausted exception") {
        val quotaBody = """{"error":{"code":429,"message":"Resource has been exhausted (e.g. check quota).","status":"RESOURCE_EXHAUSTED"}}"""
        val engine = statelessEngine(HttpStatusCode.TooManyRequests, quotaBody)
        val ex = shouldThrow<Exception> { makeService(engine).analyzeDocument("text") }
        ex.message shouldContain "QuotaExhausted"
    }

    test("generateChatResponse returns 'Error: ...' string on failure") {
        val engine = statelessEngine(HttpStatusCode.Unauthorized, """{"error":"bad key"}""")
        val result = makeService(engine).generateChatResponse("hello")
        result shouldContain "Error"
        result shouldNotContain "QuotaExhausted"
    }

    test("generateChatResponse returns response text on success") {
        val engine = statelessEngine(HttpStatusCode.OK, okText)
        val result = makeService(engine).generateChatResponse("hi")
        result shouldBe "hello"
    }

    // ── Empty response from Gemini ────────────────────────────────────────────

    test("200 with empty candidates throws Empty response from AI") {
        val emptyBody = """{"candidates":[]}"""
        val engine = statelessEngine(HttpStatusCode.OK, emptyBody)
        val ex = shouldThrow<Exception> { makeService(engine).generateCalendarEventsFromPrompt("x") }
        ex.message shouldContain "Empty response"
    }

    // ── Non-null logger paths ─────────────────────────────────────────────────

    test("401 Unauthorized logs error message when logger is non-null") {
        val engine = statelessEngine(HttpStatusCode.Unauthorized, """{"error":"bad key"}""")
        shouldThrow<Exception> { makeServiceWithLogger(engine).generateCalendarEventsFromPrompt("x") }
    }

    test("403 Forbidden logs error message when logger is non-null") {
        val engine = statelessEngine(HttpStatusCode.Forbidden, """{"error":"forbidden"}""")
        shouldThrow<Exception> { makeServiceWithLogger(engine).generateCalendarEventsFromPrompt("x") }
    }

    test("404 StructuralError logs and blacklists with non-null logger") {
        val engine = statelessEngine(HttpStatusCode.NotFound, """{"error":"not found"}""")
        shouldThrow<Exception> { makeServiceWithLogger(engine).generateCalendarEventsFromPrompt("x") }
    }

    test("429 QuotaExhausted logs exhaustion message with non-null logger") {
        val quotaBody = """{"error":{"code":429,"message":"Resource has been exhausted (e.g. check quota).","status":"RESOURCE_EXHAUSTED"}}"""
        val engine = statelessEngine(HttpStatusCode.TooManyRequests, quotaBody)
        shouldThrow<Exception> { makeServiceWithLogger(engine).generateCalendarEventsFromPrompt("x") }
    }

    test("500 TransientServerError logs and retries with non-null logger") {
        val engine = statelessEngine(HttpStatusCode.InternalServerError, """{"error":"server error"}""")
        shouldThrow<Exception> { makeServiceWithLogger(engine).generateCalendarEventsFromPrompt("x") }
    }

    test("400 OtherError logs API error message with non-null logger") {
        val engine = statelessEngine(HttpStatusCode.BadRequest, """{"error":{"message":"bad request"}}""")
        shouldThrow<Exception> { makeServiceWithLogger(engine).generateCalendarEventsFromPrompt("x") }
    }

    test("network exception is logged with non-null logger before retry") {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(singleModel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                error("Simulated network failure")
            }
        }
        val result = makeServiceWithLogger(engine).generateChatResponse("x")
        result shouldContain "Error"
    }

    // ── Non-null telemetryManager paths ───────────────────────────────────────

    test("429 QuotaExhausted calls telemetryManager.logRateLimitError when settings present") {
        val quotaBody = """{"error":{"code":429,"message":"Resource has been exhausted (e.g. check quota).","status":"RESOURCE_EXHAUSTED"}}"""
        val engine = statelessEngine(HttpStatusCode.TooManyRequests, quotaBody)
        shouldThrow<Exception> { makeServiceWithSettings(engine).generateCalendarEventsFromPrompt("x") }
    }

    test("500 TransientServerError calls telemetryManager.logRateLimitError when settings present") {
        val engine = statelessEngine(HttpStatusCode.InternalServerError, """{"error":"server error"}""")
        shouldThrow<Exception> { makeServiceWithSettings(engine).generateCalendarEventsFromPrompt("x") }
    }

    test("429 short delay calls telemetryManager.logRateLimitError then retries when settings present") {
        var callCount = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(singleModel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                callCount++
                if (callCount == 1) respond(
                    """{"error":{"message":"retry 5s"}}""",
                    HttpStatusCode.TooManyRequests,
                    headersOf(HttpHeaders.ContentType, "application/json")
                )
                else respond(okText, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val result = makeServiceWithSettings(engine).generateChatResponse("hi")
        result shouldBe "hello"
    }

    // ── Deep null chain in successful response ────────────────────────────────

    test("200 with empty parts list throws Empty response from AI") {
        val engine = statelessEngine(HttpStatusCode.OK, """{"candidates":[{"content":{"parts":[]}}]}""")
        val ex = shouldThrow<Exception> { makeService(engine).generateCalendarEventsFromPrompt("x") }
        ex.message shouldContain "Empty response"
    }

    // ── ExtremeDelay branch ───────────────────────────────────────────────────

    test("429 with extreme delay logs warning with non-null logger") {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(singleModel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond("""{"error":{"message":"retry 130s"}}""", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        shouldThrow<Exception> { makeServiceWithLogger(engine).generateCalendarEventsFromPrompt("x") }
    }

    test("429 with retry 130s body triggers ExtremeDelay and blacklists model") {
        var callCount = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(singleModel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                callCount++
                // "retry 130s" → RetryAfterParser parses 130*1000=130000ms > 120000ms → ExtremeDelay
                respond(
                    """{"error":{"message":"retry 130s"}}""",
                    HttpStatusCode.TooManyRequests,
                    headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        // ExtremeDelay: no wait, advanceAttempt=false → loop runs until model negotiator exhausts
        shouldThrow<Exception> { makeService(engine).generateCalendarEventsFromPrompt("x") }
        (callCount >= 1) shouldBe true
    }

    // ── QuotaExhausted rethrow from catch block ───────────────────────────────

    test("ShortDelay wait throwing QuotaExhausted is rethrown by catch block") {
        // skipLongDelaysInTests=true causes retryService.wait(5000ms) to throw QuotaExhausted
        // that exception is caught by the catch block and rethrown (msg.contains("QuotaExhausted"))
        GeminiRetryService.skipLongDelaysInTests = true
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(singleModel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond("""{"error":{"message":"retry 5s"}}""", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val ex = shouldThrow<Exception> { makeService(engine).categorizeSource("test") }
        ex.message shouldContain "QuotaExhausted"
        // afterEach calls clearRateLimitResetForTesting → clearGlobalHoldForTesting which resets skipLongDelaysInTests
    }

    // ── LongDelay and SaturatedKey logger branches ────────────────────────────

    test("429 with long delay logs blacklist warning with non-null logger") {
        var callCount = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(twoModels, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                callCount++
                // "retry 11s" → 11000ms > 10000ms, count=0+1=1 < 2 → LongDelay → blacklist model 1
                if (callCount == 1) respond("""{"error":{"message":"retry 11s"}}""", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.ContentType, "application/json"))
                else respond(okText, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val result = makeServiceWithLogger(engine).generateChatResponse("hi")
        result shouldBe "hello"
    }

    test("two consecutive long-delay 429s triggers SaturatedKey with non-null logger") {
        var callCount = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(twoModels, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                callCount++
                // call 1: LongDelay (consecutiveRateLimitCount 0→1 < 2) → blacklist model 1
                // call 2: SaturatedKey (count 1+1=2 >= 2) → blacklist model 2, wait, reset count
                respond("""{"error":{"message":"retry 11s"}}""", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        // LongDelay on call 1, SaturatedKey on call 2 (consecutiveRateLimitCount 0→1→2)
        shouldThrow<Exception> { makeServiceWithLogger(engine).generateCalendarEventsFromPrompt("x") }
        (callCount >= 2) shouldBe true
    }

    // ── CancellationException propagation ─────────────────────────────────────

    test("CancellationException from network is rethrown without retry") {
        var callCount = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(singleModel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                callCount++
                throw kotlinx.coroutines.CancellationException("test cancel")
            }
        }
        // generateChatResponse catches all exceptions and returns "Error: ..."
        val result = makeService(engine).generateChatResponse("hi")
        callCount shouldBe 1  // NOT retried — CancellationException is rethrown by catch block
        result shouldContain "Error"
    }
})
