package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.assertions.throwables.shouldThrow
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
import kotlinx.serialization.json.Json

class GeminiRequestExecutorTest : FunSpec({
    fun createExecutor(mockEngine: MockEngine): GeminiRequestExecutor {
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json()
            }
        }
        val logger = mockk<Logger>(relaxed = true)
        val telemetryManager = mockk<TelemetryManager>(relaxed = true)
        val modelNegotiator = mockk<GeminiModelNegotiator>(relaxed = true) {
            io.mockk.every { getAvailableModels() } returns listOf("gemini-2.5-flash")
            io.mockk.every { negotiateBestModel(any(), any()) } returns "gemini-2.5-flash"
        }
        val delayFn: suspend (Long) -> Unit = { }
        return GeminiRequestExecutor(client, "test-api-key", null, logger, telemetryManager, modelNegotiator, delayFn)
    }

    test("Status 200 with valid Gemini response is parsed and returned") {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{
                    "candidates": [{
                        "content": {
                            "parts": [{"text": "Extracted: Class meets MWF"}]
                        },
                        "finishReason": "STOP"
                    }],
                    "usageMetadata": {"promptTokenCount": 100, "candidatesTokenCount": 50}
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val executor = createExecutor(mockEngine)

        runBlocking {
            val result = executor.executeWithRetry(
                maxAttempts = 1,
                body = { "test-model" to Json.encodeToJsonElement("{}") },
                parseResponse = { text -> text }
            )

            result shouldBe "Extracted: Class meets MWF"
        }
    }

    test("Status 200 with empty response throws exception") {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{
                    "candidates": [{
                        "content": {"parts": []},
                        "finishReason": "STOP"
                    }],
                    "usageMetadata": {"promptTokenCount": 100, "candidatesTokenCount": 0}
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val executor = createExecutor(mockEngine)

        runBlocking {
            shouldThrow<Exception> {
                executor.executeWithRetry(
                    maxAttempts = 1,
                    body = { "test-model" to Json.encodeToJsonElement("{}") },
                    parseResponse = { it }
                )
            }
        }
    }

    test("Status 401 Unauthorized throws with auth error message") {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"error": {"message": "Invalid API key"}}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val executor = createExecutor(mockEngine)

        runBlocking {
            val exception = shouldThrow<Exception> {
                executor.executeWithRetry(
                    maxAttempts = 1,
                    body = { "test-model" to Json.encodeToJsonElement("{}") },
                    parseResponse = { it }
                )
            }
            exception.message shouldBe "Unauthorized"
        }
    }

    test("Status 403 Forbidden throws with permission error message") {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"error": {"message": "Permission denied"}}""",
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val executor = createExecutor(mockEngine)

        runBlocking {
            val exception = shouldThrow<Exception> {
                executor.executeWithRetry(
                    maxAttempts = 1,
                    body = { "test-model" to Json.encodeToJsonElement("{}") },
                    parseResponse = { it }
                )
            }
            exception.message shouldBe "Forbidden"
        }
    }

    test("Status 404 Not Found blacklists model and retries") {
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            if (callCount == 1) {
                respond(
                    content = """{"error": {"message": "Model not found"}}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{
                        "candidates": [{
                            "content": {"parts": [{"text": "Success"}]},
                            "finishReason": "STOP"
                        }]
                    }""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val executor = createExecutor(mockEngine)

        runBlocking {
            val result = executor.executeWithRetry(
                maxAttempts = 2,
                body = { "test-model" to Json.encodeToJsonElement("{}") },
                parseResponse = { it }
            )

            result shouldBe "Success"
            callCount shouldBe 2
        }
    }

    test("Status 429 Rate Limit with short delay retries immediately") {
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            if (callCount == 1) {
                respond(
                    content = """{"error": {"message": "Rate limit exceeded", "metadata": {"retry_after_ms": 1000}}}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(
                        HttpHeaders.ContentType, "application/json",
                        "Retry-After", "1"
                    )
                )
            } else {
                respond(
                    content = """{
                        "candidates": [{
                            "content": {"parts": [{"text": "Retried"}]},
                            "finishReason": "STOP"
                        }]
                    }""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val executor = createExecutor(mockEngine)

        runBlocking {
            val result = executor.executeWithRetry(
                maxAttempts = 2,
                body = { "test-model" to Json.encodeToJsonElement("{}") },
                parseResponse = { it }
            )

            result shouldBe "Retried"
        }
    }

    test("Status 500 Server Error blacklists model and retries") {
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            if (callCount == 1) {
                respond(
                    content = """{"error": {"message": "Internal server error"}}""",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{
                        "candidates": [{
                            "content": {"parts": [{"text": "Recovered"}]},
                            "finishReason": "STOP"
                        }]
                    }""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val executor = createExecutor(mockEngine)

        runBlocking {
            val result = executor.executeWithRetry(
                maxAttempts = 2,
                body = { "test-model" to Json.encodeToJsonElement("{}") },
                parseResponse = { it }
            )

            result shouldBe "Recovered"
        }
    }

    test("Status 503 Service Unavailable blacklists model and retries") {
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            if (callCount == 1) {
                respond(
                    content = """{"error": {"message": "Service temporarily unavailable"}}""",
                    status = HttpStatusCode.ServiceUnavailable,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{
                        "candidates": [{
                            "content": {"parts": [{"text": "Available"}]},
                            "finishReason": "STOP"
                        }]
                    }""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val executor = createExecutor(mockEngine)

        runBlocking {
            val result = executor.executeWithRetry(
                maxAttempts = 2,
                body = { "test-model" to Json.encodeToJsonElement("{}") },
                parseResponse = { it }
            )

            result shouldBe "Available"
        }
    }

    test("All retries exhausted throws last error") {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"error": {"message": "Always fails"}}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val executor = createExecutor(mockEngine)

        runBlocking {
            val exception = shouldThrow<Exception> {
                executor.executeWithRetry(
                    maxAttempts = 2,
                    body = { "test-model" to Json.encodeToJsonElement("{}") },
                    parseResponse = { it }
                )
            }
            exception.message shouldContain "failed"
        }
    }

    test("Response with multiple candidates uses first one") {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{
                    "candidates": [
                        {
                            "content": {"parts": [{"text": "First response"}]},
                            "finishReason": "STOP"
                        },
                        {
                            "content": {"parts": [{"text": "Second response"}]},
                            "finishReason": "STOP"
                        }
                    ],
                    "usageMetadata": {"promptTokenCount": 100, "candidatesTokenCount": 50}
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val executor = createExecutor(mockEngine)

        runBlocking {
            val result = executor.executeWithRetry(
                maxAttempts = 1,
                body = { "test-model" to Json.encodeToJsonElement("{}") },
                parseResponse = { it }
            )

            result shouldBe "First response"
        }
    }

    test("parseResponse is called with extracted text") {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{
                    "candidates": [{
                        "content": {"parts": [{"text": "Raw text from API"}]},
                        "finishReason": "STOP"
                    }]
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val executor = createExecutor(mockEngine)
        var parseResponseCalled = false
        var parseResponseInput = ""

        runBlocking {
            val result = executor.executeWithRetry(
                maxAttempts = 1,
                body = { "test-model" to Json.encodeToJsonElement("{}") },
                parseResponse = { text ->
                    parseResponseCalled = true
                    parseResponseInput = text
                    "parsed: $text"
                }
            )

            parseResponseCalled shouldBe true
            parseResponseInput shouldBe "Raw text from API"
            result shouldBe "parsed: Raw text from API"
        }
    }
})
