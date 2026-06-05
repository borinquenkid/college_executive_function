package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.mockk.*
import kotlinx.coroutines.delay

class BugReporterTest : FunSpec({

    test("does not report error if shareAnonymousBugReports is false") {
        val mockEngine = MockEngine { request ->
            respond("", HttpStatusCode.OK)
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val mockPreferencesRepository = mockk<PreferencesRepository>()
        coEvery { mockPreferencesRepository.getPreferences() } returns StudyPreferences(shareAnonymousBugReports = false)

        val telemetryManager = mockk<TelemetryManager>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)

        val bugReporter = BugReporter(httpClient, mockPreferencesRepository, telemetryManager, logger)
        bugReporter.reportError(Exception("Test Exception"), "Test Context")

        delay(150) // wait for coroutine scope launch

        mockEngine.requestHistory.size shouldBe 0
    }

    test("reports error via POST request if shareAnonymousBugReports is true") {
        val mockEngine = MockEngine { request ->
            respond("", HttpStatusCode.OK)
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val mockPreferencesRepository = mockk<PreferencesRepository>()
        coEvery { mockPreferencesRepository.getPreferences() } returns StudyPreferences(shareAnonymousBugReports = true)

        val telemetryManager = mockk<TelemetryManager>(relaxed = true)
        every { telemetryManager.getJsonErrors() } returns 2
        every { telemetryManager.getRateLimitErrors() } returns 1
        every { telemetryManager.getCriticTotal() } returns 5
        every { telemetryManager.getCriticModified() } returns 3

        val logger = mockk<Logger>(relaxed = true)

        val bugReporter = BugReporter(httpClient, mockPreferencesRepository, telemetryManager, logger)
        bugReporter.reportError(Exception("Test Exception"), "Test Context")

        delay(150) // wait for coroutine scope launch

        mockEngine.requestHistory.size shouldBe 1
        val request = mockEngine.requestHistory.first()
        request.url.toString() shouldBe "https://api.web3forms.com/submit"
    }
})
