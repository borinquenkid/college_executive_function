package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.mockk.mockk

class GeminiRequestBuilderTest : StringSpec({

    "buildPostUrl constructs URL with API key" {
        val client = mockk<HttpClient>()
        val builder = GeminiRequestBuilder(client, "test-key", null)

        val url = builder.buildPostUrl("gemini-pro")

        url.shouldBe("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=test-key")
    }

    "buildPostUrl constructs URL without API key" {
        val client = mockk<HttpClient>()
        val builder = GeminiRequestBuilder(client, null, "test-token")

        val url = builder.buildPostUrl("gemini-pro")

        url.shouldBe("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent")
    }

    "hasApiKey returns true when API key present" {
        val client = mockk<HttpClient>()
        val builder = GeminiRequestBuilder(client, "test-key", null)

        builder.hasApiKey() shouldBe true
    }

    "hasApiKey returns false when API key absent" {
        val client = mockk<HttpClient>()
        val builder = GeminiRequestBuilder(client, null, null)

        builder.hasApiKey() shouldBe false
    }

    "hasAccessToken returns true when token present" {
        val client = mockk<HttpClient>()
        val builder = GeminiRequestBuilder(client, null, "test-token")

        builder.hasAccessToken() shouldBe true
    }

    "hasAccessToken returns false when token absent" {
        val client = mockk<HttpClient>()
        val builder = GeminiRequestBuilder(client, null, null)

        builder.hasAccessToken() shouldBe false
    }
})
