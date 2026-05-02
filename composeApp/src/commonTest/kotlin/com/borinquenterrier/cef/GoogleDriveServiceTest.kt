package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

import io.mockk.every
import io.mockk.mockk

class GoogleDriveServiceTest : FunSpec({

    test("listFiles sends correct GET request") {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"files": [{"id": "1", "name": "Syllabus.pdf", "mimeType": "application/pdf"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }
        
        val tokenRepo = mockk<GoogleTokenRepository>()
        val authService = mockk<GoogleAuthService>()
        every { tokenRepo.getAccessToken() } returns "mock-token"

        val service = GoogleDriveService(httpClient, tokenRepo, authService)
        val files = service.listFiles()

        files.size shouldBe 1
        files.first().name shouldBe "Syllabus.pdf"
        
        val request = mockEngine.requestHistory.first()
        request.url.toString() shouldBe "https://www.googleapis.com/drive/v3/files"
        request.headers["Authorization"] shouldBe "Bearer mock-token"
    }

    test("getFileContent for Google Doc uses export endpoint") {
        val mockEngine = MockEngine { request ->
            respond(
                content = "Exported Text",
                status = HttpStatusCode.OK
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }
        
        val tokenRepo = mockk<GoogleTokenRepository>()
        val authService = mockk<GoogleAuthService>()
        every { tokenRepo.getAccessToken() } returns "mock-token"

        val service = GoogleDriveService(httpClient, tokenRepo, authService)
        val content = service.getFileContent("doc-123", "application/vnd.google-apps.document")

        content shouldBe "Exported Text"
        
        val request = mockEngine.requestHistory.first()
        request.url.toString() shouldBe "https://www.googleapis.com/drive/v3/files/doc-123/export?mimeType=text%2Fplain"
    }
})
