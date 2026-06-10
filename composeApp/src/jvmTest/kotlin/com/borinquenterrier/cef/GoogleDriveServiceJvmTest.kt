package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.serialization.json.Json

class GoogleDriveServiceJvmTest : FunSpec({

    val tokenRepository = mockk<GoogleTokenRepository>(relaxed = true)
    val authService = mockk<GoogleAuthService>(relaxed = true)
    val authErrorMessages = mutableListOf<String>()

    fun makeService(client: HttpClient) = GoogleDriveService(
        httpClient = client,
        tokenRepository = tokenRepository,
        authService = authService,
        onAuthError = { authErrorMessages.add(it) }
    )

    fun jsonClient(handler: MockRequestHandler): HttpClient {
        val engine = MockEngine(handler)
        return HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }

    beforeEach {
        clearAllMocks()
        authErrorMessages.clear()
    }

    // ─── validateConnection ──────────────────────────────────────────────────

    test("validateConnection returns true on 200 OK") {
        val client = jsonClient { respond("", HttpStatusCode.OK) }
        makeService(client).validateConnection("token") shouldBe true
    }

    test("validateConnection returns false on 401") {
        val client = jsonClient { respond("Unauthorized", HttpStatusCode.Unauthorized) }
        makeService(client).validateConnection("bad") shouldBe false
    }

    test("validateConnection returns false when network throws") {
        val engine = MockEngine { throw Exception("Network failure") }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        makeService(client).validateConnection("any") shouldBe false
    }

    // ─── listFiles ───────────────────────────────────────────────────────────

    test("listFiles returns parsed file list on success") {
        val body = """{"files":[{"id":"f1","name":"lecture.pdf","mimeType":"application/pdf"}]}"""
        val client = jsonClient {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        coEvery { tokenRepository.getAccessToken() } returns "good-token"

        val files = makeService(client).listFiles()

        files.size shouldBe 1
        files[0].id shouldBe "f1"
        files[0].name shouldBe "lecture.pdf"
    }

    test("listFiles throws on non-success response") {
        coEvery { tokenRepository.getAccessToken() } returns "good-token"
        val client = jsonClient { respond("Forbidden", HttpStatusCode.Forbidden) }

        shouldThrow<Exception> { makeService(client).listFiles() }
            .message shouldContain "403"
    }

    test("listFiles retries with refreshed token after 401 exception") {
        val goodJson =
            """{"files":[{"id":"f2","name":"syllabus.pdf","mimeType":"application/pdf"}]}"""
        coEvery { tokenRepository.getAccessToken() } returns "expired-token"
        coEvery { tokenRepository.getRefreshToken() } returns "refresh-token"
        coEvery { authService.refreshAccessToken("refresh-token") } returns "new-token"
        coEvery { tokenRepository.saveTokens(any(), any()) } just runs

        var callCount = 0
        val engine = MockEngine { _ ->
            callCount++
            if (callCount == 1) throw Exception("401 Unauthorized")
            else respond(
                goodJson,
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val files = makeService(client).listFiles()

        files.size shouldBe 1
        files[0].id shouldBe "f2"
        coVerify { authService.refreshAccessToken("refresh-token") }
        coVerify { tokenRepository.saveTokens("new-token", "refresh-token") }
    }

    test("listFiles calls onAuthError and throws when no refresh token available") {
        coEvery { tokenRepository.getAccessToken() } returns "expired-token"
        coEvery { tokenRepository.getRefreshToken() } returns null
        val engine = MockEngine { throw Exception("401 Unauthorized") }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        shouldThrow<Exception> { makeService(client).listFiles() }
        authErrorMessages.size shouldBe 1
        authErrorMessages[0] shouldContain "expired"
    }

    // ─── getFileContent ──────────────────────────────────────────────────────

    test("getFileContent uses export endpoint for Google Docs") {
        coEvery { tokenRepository.getAccessToken() } returns "good-token"
        var capturedUrl = ""
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(
                "Lecture notes",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val content =
            makeService(client).getFileContent("doc-id", "application/vnd.google-apps.document")

        content shouldBe "Lecture notes"
        capturedUrl shouldContain "/export"
    }

    test("getFileContent uses media download endpoint for binary files") {
        coEvery { tokenRepository.getAccessToken() } returns "good-token"
        var capturedUrl = ""
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(
                "PDF bytes",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/octet-stream")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        makeService(client).getFileContent("pdf-id", "application/pdf")

        capturedUrl shouldContain "alt=media"
    }

    test("getFileContent throws on non-200 response") {
        coEvery { tokenRepository.getAccessToken() } returns "good-token"
        val client = jsonClient { respond("Not Found", HttpStatusCode.NotFound) }

        shouldThrow<Exception> {
            makeService(client).getFileContent("missing-id", "application/pdf")
        }.message shouldContain "404"
    }
})
