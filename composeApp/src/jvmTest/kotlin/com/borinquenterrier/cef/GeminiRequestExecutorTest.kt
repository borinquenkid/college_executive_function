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

    afterEach { GeminiAIService.clearBlacklistForTesting() }

    fun makeService(engine: io.ktor.client.engine.HttpClientEngine, modelsBody: String = singleModel) =
        GeminiAIService(
            apiKey = "test-key",
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
})
