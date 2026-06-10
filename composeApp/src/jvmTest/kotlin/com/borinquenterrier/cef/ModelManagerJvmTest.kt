package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json

/**
 * JVM-only tests for ModelManager.downloadModel() using Ktor MockEngine.
 */
class ModelManagerJvmTest : FunSpec({

    fun buildClient(mockEngine: MockEngine) = HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    test("isModelDownloaded returns false when directory does not exist") {
        val manager = ModelManager(mockk(), "/tmp/cef_nonexistent_dir_${System.nanoTime()}")
        manager.isModelDownloaded() shouldBe false
    }

    test("downloadModel emits DownloadProgress(1f, true) as the final event") {
        val fakeData = ByteArray(128) { it.toByte() }
        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(fakeData),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Application.OctetStream.toString()),
                    HttpHeaders.ContentLength to listOf(fakeData.size.toString())
                )
            )
        }

        val tmpDir = System.getProperty("java.io.tmpdir") + "/cef_test_${System.nanoTime()}"
        val manager = ModelManager(buildClient(mockEngine), tmpDir)

        val events = manager.downloadModel().toList()

        events.last().isDone shouldBe true
        events.last().progress shouldBe 1f
    }

    test("downloadModel emits at least one intermediate progress event for multi-chunk data") {
        val chunkSize = 64 * 1024
        val totalSize = chunkSize * 3 + 1024 // 3 full chunks + partial
        val fakeData = ByteArray(totalSize) { it.toByte() }

        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(fakeData),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Application.OctetStream.toString()),
                    HttpHeaders.ContentLength to listOf(totalSize.toString())
                )
            )
        }

        val tmpDir = System.getProperty("java.io.tmpdir") + "/cef_test_${System.nanoTime()}"
        val manager = ModelManager(buildClient(mockEngine), tmpDir)

        val events = manager.downloadModel().toList()

        events.size shouldBeGreaterThan 1

        // Intermediate events must have isDone = false and progress in [0, 1]
        events.dropLast(1).forEach { event ->
            event.isDone shouldBe false
            event.progress shouldBeLessThanOrEqualTo 1f
        }

        events.last().isDone shouldBe true
        events.last().progress shouldBe 1f
    }

    test("downloadModel emits progress 0f when content-length is unknown") {
        val fakeData = ByteArray(1024) { 0 }
        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(fakeData),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.OctetStream.toString()))
                // No Content-Length header → contentLength() returns null → reports 0f
            )
        }

        val tmpDir = System.getProperty("java.io.tmpdir") + "/cef_test_${System.nanoTime()}"
        val manager = ModelManager(buildClient(mockEngine), tmpDir)

        val events = manager.downloadModel().toList()

        events.dropLast(1).forEach { event ->
            event.progress shouldBe 0f
            event.isDone shouldBe false
        }
        events.last().isDone shouldBe true
    }

    test("downloadModel throws exception when server returns non-200") {
        val mockEngine = MockEngine { _ ->
            respond(content = ByteReadChannel("Not Found"), status = HttpStatusCode.NotFound)
        }

        val tmpDir = System.getProperty("java.io.tmpdir") + "/cef_test_${System.nanoTime()}"
        val manager = ModelManager(buildClient(mockEngine), tmpDir)

        shouldThrow<Exception> {
            manager.downloadModel().toList()
        }
    }
})
