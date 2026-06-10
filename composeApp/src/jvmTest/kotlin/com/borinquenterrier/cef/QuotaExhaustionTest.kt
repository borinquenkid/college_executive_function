package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

/**
 * Unit tests verifying that the quota exhaustion fast-fail works correctly.
 *
 * A daily-quota (RPD) 429 should throw [Exception] immediately — not retry for
 * an hour consuming the server-supplied back-off delays.
 *
 * A per-minute (RPM) 429 with a retry hint should NOT be mistaken for quota
 * exhaustion and should still back-off normally.
 */
class QuotaExhaustionTest : FunSpec({

    afterEach { GeminiAIService.clearBlacklistForTesting() }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Minimal valid /v1beta/models response — lets negotiation pick "gemini-2.5-flash". */
    val modelsResponse = """
        {"models":[{"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]}]}
    """.trimIndent()

    /**
     * Builds a MockEngine that:
     *  - Returns [modelsResponse] for any /models request (so negotiation works).
     *  - Returns [contentStatus] + [contentBody] for any /generateContent request.
     */
    fun mockEngine(contentStatus: HttpStatusCode, contentBody: String) = MockEngine { request ->
        if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
            respond(
                content = modelsResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        } else {
            respond(
                content = contentBody,
                status = contentStatus,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    }

    fun makeService(engine: HttpClientEngine) = GeminiAIService(
        apiKey = "test-key",
        customClient = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        },
        delayFn = {} // no real sleeps in unit tests
    )

    // -------------------------------------------------------------------------
    // Quota exhaustion bodies — should throw immediately
    // Note: generateCalendarEventsFromPrompt propagates exceptions directly.
    // (generateChatResponse catches and returns "Error: ..." strings.)
    // -------------------------------------------------------------------------

    test("'quota exhausted' body throws QuotaExhausted immediately") {
        val body =
            """{"error":{"code":429,"message":"Resource has been exhausted (e.g. check quota).","status":"RESOURCE_EXHAUSTED"}}"""
        val service = makeService(mockEngine(HttpStatusCode.TooManyRequests, body))

        val ex = shouldThrow<Exception> {
            service.generateCalendarEventsFromPrompt("list events")
        }
        ex.message shouldContain "QuotaExhausted"
    }

    test("'quota exceeded' body throws QuotaExhausted immediately") {
        val body =
            """{"error":{"message":"You have exceeded your daily quota for the Gemini API."}}"""
        val service = makeService(mockEngine(HttpStatusCode.TooManyRequests, body))

        val ex = shouldThrow<Exception> {
            service.generateCalendarEventsFromPrompt("list events")
        }
        ex.message shouldContain "QuotaExhausted"
    }

    test("'quota limit' without retry hint throws QuotaExhausted immediately") {
        val body = """{"error":{"message":"API quota limit reached for this project."}}"""
        val service = makeService(mockEngine(HttpStatusCode.TooManyRequests, body))

        val ex = shouldThrow<Exception> {
            service.generateCalendarEventsFromPrompt("list events")
        }
        ex.message shouldContain "QuotaExhausted"
    }

    // -------------------------------------------------------------------------
    // Per-minute rate limit bodies — should NOT be mistaken for quota exhaustion
    // -------------------------------------------------------------------------

    test("'retry in Xs' body is treated as transient RPM — does NOT throw QuotaExhausted") {
        val rpmBody = """{"error":{"message":"RESOURCE_EXHAUSTED. Please retry in 5s."}}"""
        var generateCallCount = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                // Models list — always succeed so negotiation works
                respond(
                    content = modelsResponse,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                generateCallCount++
                if (generateCallCount < 3) {
                    respond(
                        content = rpmBody,
                        status = HttpStatusCode.TooManyRequests,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    // Third generate attempt: return valid response
                    respond(
                        content = """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
        val service = makeService(engine)
        // generateChatResponse swallows exceptions — a quota error would return "Error: QuotaExhausted:..."
        // An RPM retry should eventually return the actual content "ok" (no "QuotaExhausted" in result)
        val result = service.generateChatResponse("hello")
        result shouldContain "ok"
        generateCallCount shouldBe 3
    }

    test("daily quota exhaustion on first model falls back to next model and succeeds") {
        val quotaExhaustedBody =
            """{"error":{"code":429,"message":"Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 0, model: gemini-2.0-flash","status":"RESOURCE_EXHAUSTED"}}"""
        val twoModelsResponse = """
            {"models":[
                {"name":"models/gemini-2.0-flash","supportedGenerationMethods":["generateContent"]},
                {"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]}
            ]}
        """.trimIndent()

        var callCount = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(
                    content = twoModelsResponse,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                callCount++
                if (request.url.encodedPath.contains("gemini-2.0-flash")) {
                    respond(
                        content = quotaExhaustedBody,
                        status = HttpStatusCode.TooManyRequests,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond(
                        content = """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val service = makeService(engine)
        val result = service.generateChatResponse("hello")
        result shouldContain "ok"
        callCount shouldBe 2
    }

    test("long retry delay on first model falls back to next model and succeeds") {
        val longRetryBody =
            """{"error":{"code":429,"message":"Quota exceeded. Please retry in 48s.","status":"RESOURCE_EXHAUSTED"}}"""
        val twoModelsResponse = """
            {"models":[
                {"name":"models/gemini-2.0-flash","supportedGenerationMethods":["generateContent"]},
                {"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]}
            ]}
        """.trimIndent()

        var callCount = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/models") && !request.url.encodedPath.contains(":generateContent")) {
                respond(
                    content = twoModelsResponse,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                callCount++
                if (request.url.encodedPath.contains("gemini-2.0-flash")) {
                    respond(
                        content = longRetryBody,
                        status = HttpStatusCode.TooManyRequests,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond(
                        content = """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val service = makeService(engine)
        val result = service.generateChatResponse("hello")
        result shouldContain "ok"
        callCount shouldBe 2
    }
})
