package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

class GeminiModelNegotiationTest : FunSpec({

    // Reset shared blacklist state before each test to prevent cross-test contamination
    beforeTest { GeminiAIService.clearBlacklistForTesting() }

    val modelsJson = """
        {
            "models": [
                {
                    "name": "models/gemini-1.5-flash",
                    "supportedGenerationMethods": ["generateContent"]
                },
                {
                    "name": "models/gemini-1.5-pro",
                    "supportedGenerationMethods": ["generateContent"]
                }
            ]
        }
    """.trimIndent()

    // Helper to build a mock success response for generateContent
    fun successJson(text: String) = """
        {
            "candidates": [
                {
                    "content": {
                        "parts": [
                            {
                                "text": ${Json.encodeToJsonElement(text)}
                            }
                        ]
                    }
                }
            ]
        }
    """.trimIndent()

    fun makeMockClient(
        onGenerateContent: (attempt: Int, modelName: String) -> Pair<HttpStatusCode, String>
    ): Pair<HttpClient, () -> Int> {
        var attemptCount = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/v1beta/models") -> {
                    respond(
                        content = modelsJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString()
                        )
                    )
                }

                request.url.encodedPath.contains("generateContent") -> {
                    attemptCount++
                    val modelName = request.url.encodedPath
                        .substringAfter("/models/")
                        .substringBefore(":generateContent")
                    val (status, body) = onGenerateContent(attemptCount, modelName)
                    val contentType =
                        if (status.isSuccess()) ContentType.Application.Json else ContentType.Text.Plain
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, contentType.toString())
                    )
                }

                else -> error("Unhandled request: ${request.url}")
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return client to { attemptCount }
    }

    // -------------------------------------------------------------------------
    // generateCalendarEvents — existing tests
    // -------------------------------------------------------------------------

    test("503 Service Unavailable retries with exponential backoff and does NOT blacklist the model") {
        val (client, getAttempts) = makeMockClient { attempt, _ ->
            if (attempt < 3) HttpStatusCode.ServiceUnavailable to "Service Unavailable"
            else HttpStatusCode.OK to successJson("[]")
        }

        val logger = mockk<Logger>(relaxed = true)
        val geminiService = GeminiAIService(
            apiKey = "fake-key",
            logger = logger,
            customClient = client,
            delayFn = {})

        val events = geminiService.generateCalendarEvents(listOf(SourceFragment("test content")))
        events shouldNotBe null
        getAttempts() shouldBe 3
    }

    test("404 Not Found blacklists the model and falls back to another model") {
        var flashAttempts = 0
        var proAttempts = 0

        val (client, _) = makeMockClient { _, modelName ->
            when {
                modelName.contains("flash") -> {
                    flashAttempts++
                    HttpStatusCode.NotFound to "Not Found"
                }

                modelName.contains("pro") -> {
                    proAttempts++
                    HttpStatusCode.OK to successJson("[]")
                }

                else -> HttpStatusCode.OK to successJson("[]")
            }
        }

        val logger = mockk<Logger>(relaxed = true)
        val geminiService = GeminiAIService(
            apiKey = "fake-key",
            logger = logger,
            customClient = client,
            delayFn = {})

        val events = geminiService.generateCalendarEvents(listOf(SourceFragment("test content")))
        events shouldNotBe null
        flashAttempts shouldBe 1
        proAttempts shouldBe 1
    }

    // -------------------------------------------------------------------------
    // decomposeTask — new tests for the unified retry engine
    // -------------------------------------------------------------------------

    test("decomposeTask 503 retries with backoff and does NOT blacklist the model") {
        val tasksJson = """[{"title":"Study","daysBeforeDue":3,"description":"Review notes"}]"""
        val (client, getAttempts) = makeMockClient { attempt, _ ->
            if (attempt < 2) HttpStatusCode.ServiceUnavailable to "Service Unavailable"
            else HttpStatusCode.OK to successJson(tasksJson)
        }

        val logger = mockk<Logger>(relaxed = true)
        val geminiService = GeminiAIService(
            apiKey = "fake-key",
            logger = logger,
            customClient = client,
            delayFn = {})

        val tasks = geminiService.decomposeTask("Final Exam", "2025-12-15")
        tasks.size shouldBe 1
        tasks[0].title shouldBe "Study"
        getAttempts() shouldBe 2
    }

    test("decomposeTask 404 blacklists model and falls back") {
        var flashAttempts = 0
        var proAttempts = 0
        val tasksJson = """[{"title":"Outline","daysBeforeDue":5,"description":"Write outline"}]"""

        val (client, _) = makeMockClient { _, modelName ->
            when {
                modelName.contains("flash") -> {
                    flashAttempts++; HttpStatusCode.NotFound to "Not Found"
                }

                else -> {
                    proAttempts++; HttpStatusCode.OK to successJson(tasksJson)
                }
            }
        }

        val logger = mockk<Logger>(relaxed = true)
        val geminiService = GeminiAIService(
            apiKey = "fake-key",
            logger = logger,
            customClient = client,
            delayFn = {})

        val tasks = geminiService.decomposeTask("Research Paper", "2025-11-01")
        tasks.size shouldBe 1
        flashAttempts shouldBe 1
        proAttempts shouldBe 1
    }

    // -------------------------------------------------------------------------
    // categorizeSource — new tests for the unified retry engine
    // -------------------------------------------------------------------------

    test("categorizeSource 503 retries and does NOT blacklist the model") {
        val categoryJson = """{"category":"SYLLABUS"}"""
        val (client, getAttempts) = makeMockClient { attempt, _ ->
            if (attempt < 2) HttpStatusCode.ServiceUnavailable to "Service Unavailable"
            else HttpStatusCode.OK to successJson(categoryJson)
        }

        val logger = mockk<Logger>(relaxed = true)
        val geminiService = GeminiAIService(
            apiKey = "fake-key",
            logger = logger,
            customClient = client,
            delayFn = {})

        val category = geminiService.categorizeSource("This course is worth 30% midterm...")
        category shouldBe SourceCategory.SYLLABUS
        getAttempts() shouldBe 2
    }

    test("categorizeSource defaults to OTHER after exhausting retries") {
        val (client, getAttempts) = makeMockClient { _, _ ->
            HttpStatusCode.ServiceUnavailable to "Service Unavailable"
        }

        val logger = mockk<Logger>(relaxed = true)
        val geminiService = GeminiAIService(
            apiKey = "fake-key",
            logger = logger,
            customClient = client,
            delayFn = {})

        val category = geminiService.categorizeSource("some text")
        category shouldBe SourceCategory.OTHER
        // maxAttempts for categorizeSource is 3
        getAttempts() shouldBe 3
    }

    test("generateCalendarEvents batches large list of fragments") {
        val (client, getAttempts) = makeMockClient { _, _ ->
            HttpStatusCode.OK to successJson("[]")
        }

        val logger = mockk<Logger>(relaxed = true)
        val geminiService = GeminiAIService(
            apiKey = "fake-key",
            logger = logger,
            customClient = client,
            delayFn = {}
        )

        // 5 text fragments -> should result in 2 batches (1-3, 3-5)
        val fragments = List(5) { i ->
            SourceFragment(text = "Fragment ${i + 1}", type = SourceType.TEXT)
        }

        val events = geminiService.generateCalendarEvents(fragments)
        events shouldBe emptyList()
        getAttempts() shouldBe 2
    }
})
