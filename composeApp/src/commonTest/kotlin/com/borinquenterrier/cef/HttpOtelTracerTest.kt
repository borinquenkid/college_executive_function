package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HttpOtelTracerTest : StringSpec({

    "export single span successfully" {
        val requests = mutableListOf<String>()
        val requestHeaders = mutableListOf<Headers>()
        val mutex = Mutex()

        val mockEngine = MockEngine { request ->
            mutex.withLock {
                val bodyText = (request.body as? TextContent)?.text.orEmpty()
                requests.add(bodyText)
                requestHeaders.add(request.headers)
            }
            respond("OK", HttpStatusCode.OK)
        }
        val client = HttpClient(mockEngine)
        val tracer = HttpOtelTracer(
            endpoint = "https://otel-collector/v1/traces",
            authHeader = "Basic dXNlcjpwYXNz",
            serviceName = "test-service",
            client = client
        )

        tracer.span("test-span", mapOf("env" to "test")) {
            setAttribute("span-attr", "value1")
            setAttribute("numeric-attr", 42L)
            addEvent("custom-event", mapOf("foo" to "bar"))
        }

        // Wait for asynchronous export to complete
        var attempts = 0
        while (attempts < 50) {
            val size = mutex.withLock { requests.size }
            if (size >= 1) break
            delay(10)
            attempts++
        }

        requests.size shouldBe 1
        val body = requests[0]
        body.shouldContain("test-span")
        body.shouldContain("test-service")
        body.shouldContain("span-attr")
        body.shouldContain("value1")
        body.shouldContain("numeric-attr")
        body.shouldContain("42")
        body.shouldContain("custom-event")
        body.shouldContain("foo")
        body.shouldContain("bar")

        val headers = requestHeaders[0]
        headers[HttpHeaders.Authorization] shouldBe "Basic dXNlcjpwYXNz"
    }

    "propagate parent-child span context" {
        val requests = mutableListOf<String>()
        val mutex = Mutex()

        val mockEngine = MockEngine { request ->
            mutex.withLock {
                val bodyText = (request.body as? TextContent)?.text.orEmpty()
                requests.add(bodyText)
            }
            respond("OK", HttpStatusCode.OK)
        }
        val client = HttpClient(mockEngine)
        val tracer = HttpOtelTracer(
            endpoint = "https://otel-collector/v1/traces",
            authHeader = "Basic dXNlcjpwYXNz",
            serviceName = "test-service",
            client = client
        )

        tracer.span("parent") {
            tracer.span("child") {
                setAttribute("role", "subtask")
            }
        }

        // Wait for both exports (child first, then parent)
        var attempts = 0
        while (attempts < 50) {
            val size = mutex.withLock { requests.size }
            if (size >= 2) break
            delay(10)
            attempts++
        }

        requests.size shouldBe 2
        val childJson = requests.first { it.contains("\"name\":\"child\"") }
        val parentJson = requests.first { it.contains("\"name\":\"parent\"") }

        childJson.shouldContain("parentSpanId")
        childJson.shouldContain("role")
        childJson.shouldContain("subtask")

        parentJson.contains("parentSpanId") shouldBe false
    }

    "export standalone event reliably as its own span" {
        // event() must NOT be silently dropped — telemetry on paths like study-plan grounding
        // depends on it reaching the collector even when emitted outside a span scope.
        val requests = mutableListOf<String>()
        val mutex = Mutex()

        val mockEngine = MockEngine { request ->
            mutex.withLock {
                val bodyText = (request.body as? TextContent)?.text.orEmpty()
                requests.add(bodyText)
            }
            respond("OK", HttpStatusCode.OK)
        }
        val client = HttpClient(mockEngine)
        val tracer = HttpOtelTracer(
            endpoint = "https://otel-collector/v1/traces",
            authHeader = "Basic dXNlcjpwYXNz",
            serviceName = "test-service",
            client = client
        )

        tracer.event("grounding.filter", mapOf("events.dropped" to "3", "caller" to "generateStudyPlan"))

        var attempts = 0
        while (attempts < 50) {
            val size = mutex.withLock { requests.size }
            if (size >= 1) break
            delay(10)
            attempts++
        }

        requests.size shouldBe 1
        val body = requests[0]
        body.shouldContain("grounding.filter")
        body.shouldContain("events.dropped")
        body.shouldContain("generateStudyPlan")
        body.shouldContain("test-service")
    }

    "handle exceptions thrown in span block and rethrow them" {
        val requests = mutableListOf<String>()
        val mutex = Mutex()

        val mockEngine = MockEngine { request ->
            mutex.withLock {
                val bodyText = (request.body as? TextContent)?.text.orEmpty()
                requests.add(bodyText)
            }
            respond("OK", HttpStatusCode.OK)
        }
        val client = HttpClient(mockEngine)
        val tracer = HttpOtelTracer(
            endpoint = "https://otel-collector/v1/traces",
            authHeader = "Basic dXNlcjpwYXNz",
            serviceName = "test-service",
            client = client
        )

        shouldThrow<IllegalStateException> {
            tracer.span("failing-span") {
                throw IllegalStateException("Something failed")
            }
        }

        var attempts = 0
        while (attempts < 50) {
            val size = mutex.withLock { requests.size }
            if (size >= 1) break
            delay(10)
            attempts++
        }

        requests.size shouldBe 1
        val body = requests[0]
        body.shouldContain("failing-span")
        body.shouldContain("exception")
        body.shouldContain("IllegalStateException")
        body.shouldContain("Something failed")
        body.shouldContain("\"code\":2") // Status code 2 is Error in OTLP
    }

    "swallow post failures silently" {
        val mockEngine = MockEngine {
            respondError(HttpStatusCode.InternalServerError, "Server error")
        }
        val client = HttpClient(mockEngine)
        val tracer = HttpOtelTracer(
            endpoint = "https://otel-collector/v1/traces",
            authHeader = "Basic dXNlcjpwYXNz",
            serviceName = "test-service",
            client = client
        )

        // Should not throw or crash even if post request fails
        tracer.span("failing-post-span") {
            setAttribute("foo", "bar")
        }

        delay(50) // Allow async export task to execute
    }
})
