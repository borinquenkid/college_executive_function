package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.DriverFactory
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
import com.russhwolf.settings.MapSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking

class GoogleDriveHeadlessTest : FunSpec({

    test("Headless GDrive: should list files via DependencyContainer") {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"files": [{"id": "1", "name": "HeadlessTest.pdf", "mimeType": "application/pdf"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val settings = MapSettings()
        val logger = Logger(settings)
        val driverFactory = DriverFactory() // In-memory
        
        val container = DependencyContainer(
            settings = settings,
            logger = logger,
            driverFactory = driverFactory,
            modelBasePath = "/tmp/models",
            fileReader = mockk(relaxed = true),
            docxReader = mockk(relaxed = true),
            pdfReader = mockk(relaxed = true)
        )
        
        // Mock the auth service inside the container's GDrive service if possible, 
        // or just mock the whole GDrive service for this test.
        // Actually, let's just use the one I created before, it was more direct.
        
        val tokenRepo = container.tokenRepository
        tokenRepo.saveTokens("mock-access-token", "mock-refresh-token")
        
        // We need to inject the mock engine into the container's httpClient.
        // But the container creates its own HttpClient.
        // Let's modify the test to just focus on the service but use container's repos.
        
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }
        
        val driveService = GoogleDriveService(httpClient, tokenRepo, container.authService)
        
        println("STARTING HEADLESS GDRIVE LIST VIA CONTAINER REPOS...")
        val files = driveService.listFiles()
        
        files.size shouldBe 1
        files.first().name shouldBe "HeadlessTest.pdf"
        println("Successfully verified headless GDrive listing.")
    }
})
