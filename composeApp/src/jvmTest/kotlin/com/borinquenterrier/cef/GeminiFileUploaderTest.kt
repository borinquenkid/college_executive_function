package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking

class GeminiFileUploaderTest : FunSpec({

    val jsonHeader = headersOf("Content-Type", "application/json")
    val uri = "https://generativelanguage.googleapis.com/v1beta/files/abc"
    fun fileJson(state: String) = """{"file":{"name":"files/abc","uri":"$uri","state":"$state"}}"""

    fun uploader(engine: MockEngine) =
        GeminiFileUploader(HttpClient(engine), apiKey = "k", delayFn = {}, maxPollAttempts = 5)

    test("uploads then polls until ACTIVE and returns the file uri") {
        var call = 0
        val engine = MockEngine { req ->
            call++
            when {
                req.method == HttpMethod.Post -> respond(fileJson("PROCESSING"), HttpStatusCode.OK, jsonHeader)
                call == 2 -> respond("""{"state":"PROCESSING"}""", HttpStatusCode.OK, jsonHeader) // still processing
                else -> respond("""{"state":"ACTIVE"}""", HttpStatusCode.OK, jsonHeader)
            }
        }
        runBlocking { uploader(engine).uploadAndAwait(ByteArray(10), "application/pdf") } shouldBe uri
    }

    test("returns the uri immediately when the upload comes back ACTIVE") {
        var polls = 0
        val engine = MockEngine { req ->
            if (req.method == HttpMethod.Get) polls++
            respond(fileJson("ACTIVE"), HttpStatusCode.OK, jsonHeader)
        }
        runBlocking { uploader(engine).uploadAndAwait(ByteArray(10), "application/pdf") } shouldBe uri
        polls shouldBe 0 // no polling needed
    }

    test("returns null when processing FAILED") {
        val engine = MockEngine { req ->
            if (req.method == HttpMethod.Post) respond(fileJson("PROCESSING"), HttpStatusCode.OK, jsonHeader)
            else respond("""{"state":"FAILED"}""", HttpStatusCode.OK, jsonHeader)
        }
        runBlocking { uploader(engine).uploadAndAwait(ByteArray(10), "application/pdf") } shouldBe null
    }

    test("retries past a transient non-2xx poll response instead of aborting immediately") {
        var pollCount = 0
        val engine = MockEngine { req ->
            when {
                req.method == HttpMethod.Post -> respond(fileJson("PROCESSING"), HttpStatusCode.OK, jsonHeader)
                else -> {
                    pollCount++
                    if (pollCount == 1) {
                        // A transient 5xx blip on the first poll must not permanently fail the
                        // upload — plenty of poll attempts remain.
                        respond("server error", HttpStatusCode.InternalServerError, jsonHeader)
                    } else {
                        respond("""{"state":"ACTIVE"}""", HttpStatusCode.OK, jsonHeader)
                    }
                }
            }
        }
        runBlocking { uploader(engine).uploadAndAwait(ByteArray(10), "application/pdf") } shouldBe uri
        pollCount shouldBe 2
    }

    test("returns null when the upload request errors") {
        val engine = MockEngine { respond("nope", HttpStatusCode.InternalServerError, jsonHeader) }
        runBlocking { uploader(engine).uploadAndAwait(ByteArray(10), "application/pdf") } shouldBe null
    }
})
