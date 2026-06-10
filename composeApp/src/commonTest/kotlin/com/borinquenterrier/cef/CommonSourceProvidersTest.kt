package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class CommonSourceProvidersTest : FunSpec({

    val ingestionAgent = mockk<IngestionAgent>()
    val aiService = mockk<AIService>()
    val driveService = mockk<GoogleDriveService>()
    val tokenRepository = mockk<GoogleTokenRepository>()

    test("LocalFileSourceProvider properties and authorization") {
        val provider = LocalFileSourceProvider(ingestionAgent, aiService)

        provider.id shouldBe "local_file"
        provider.displayName shouldBe "File"

        every { aiService.isConfigured() } returns true
        provider.isAuthorized() shouldBe true

        every { aiService.isConfigured() } returns false
        provider.isAuthorized() shouldBe false
    }

    test("UrlSourceProvider properties and authorization") {
        val provider = UrlSourceProvider(ingestionAgent, aiService)

        provider.id shouldBe "url"
        provider.displayName shouldBe "URL"

        every { aiService.isConfigured() } returns true
        provider.isAuthorized() shouldBe true

        every { aiService.isConfigured() } returns false
        provider.isAuthorized() shouldBe false
    }

    test("GoogleDriveSourceProvider properties and authorization") {
        val provider = GoogleDriveSourceProvider(ingestionAgent, driveService, tokenRepository)

        provider.id shouldBe "google_drive"
        provider.displayName shouldBe "Drive"

        every { tokenRepository.hasTokens() } returns true
        provider.isAuthorized() shouldBe true

        every { tokenRepository.hasTokens() } returns false
        provider.isAuthorized() shouldBe false
    }
})
