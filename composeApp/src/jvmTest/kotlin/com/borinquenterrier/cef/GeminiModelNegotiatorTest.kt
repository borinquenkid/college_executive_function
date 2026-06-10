package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import com.borinquenterrier.cef.db.AppDatabaseQueries
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
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class GeminiModelNegotiatorTest : FunSpec({

    val mockDatabase = mockk<AppDatabase>(relaxed = true)
    val mockQueries = mockk<AppDatabaseQueries>(relaxed = true)

    beforeTest {
        clearAllMocks()
        every { mockDatabase.appDatabaseQueries } returns mockQueries
        // Reset shared blacklist companion state
        GeminiAIService.clearBlacklistForTesting()
    }

    val availableModelsList = listOf(
        ModelInfo("models/gemini-2.5-flash", listOf("generateContent")),
        ModelInfo("models/gemini-2.0-flash", listOf("generateContent")),
        ModelInfo("models/gemini-2.5-flash-lite", listOf("generateContent")),
        ModelInfo("models/gemini-2.0-flash-lite", listOf("generateContent")),
        ModelInfo("models/gemini-2.0-flash-001", listOf("generateContent")),
        ModelInfo(
            "models/gemini-2.5-flash-preview-tts",
            listOf("generateContent")
        ), // text-to-speech, should filter out
        ModelInfo(
            "models/gemini-2.5-pro-image",
            listOf("generateContent")
        ), // image, should filter out
        ModelInfo(
            "models/gemini-robotics-er",
            listOf("generateContent")
        ) // robotics, should filter out
    )

    test("negotiateBestModel respects HEAVY task tier preferences") {
        every {
            mockQueries.getSelectedModel("preferred_gemini_model").executeAsOneOrNull()
        } returns null

        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = mockDatabase,
            logger = null
        )

        // HEAVY prefers: gemini-2.5-flash, gemini-2.0-flash, gemini-2.5-flash-lite, gemini-2.0-flash-lite
        val selected =
            negotiator.negotiateBestModel(availableModelsList, GeminiAIService.TaskTier.HEAVY)
        selected shouldBe "gemini-2.5-flash"

        verify(exactly = 1) {
            mockQueries.insertModel("preferred_gemini_model", "gemini-2.5-flash", any())
        }
    }

    test("negotiateBestModel respects LIGHT task tier preferences") {
        every {
            mockQueries.getSelectedModel("preferred_gemini_model").executeAsOneOrNull()
        } returns null

        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = mockDatabase,
            logger = null
        )

        // LIGHT prefers: gemini-2.5-flash-lite, gemini-2.0-flash-lite, gemini-2.0-flash, gemini-2.5-flash
        val selected =
            negotiator.negotiateBestModel(availableModelsList, GeminiAIService.TaskTier.LIGHT)
        selected shouldBe "gemini-2.5-flash-lite"

        verify(exactly = 1) {
            mockQueries.insertModel("preferred_gemini_model", "gemini-2.5-flash-lite", any())
        }
    }

    test("negotiateBestModel filters out non-text/unsupported models") {
        every {
            mockQueries.getSelectedModel("preferred_gemini_model").executeAsOneOrNull()
        } returns null

        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = mockDatabase,
            logger = null
        )

        // Only offer image and tts models, should fall back to default
        val unsupportedList = listOf(
            ModelInfo("models/gemini-2.5-flash-preview-tts", listOf("generateContent")),
            ModelInfo("models/gemini-2.5-pro-image", listOf("generateContent"))
        )

        val selected =
            negotiator.negotiateBestModel(unsupportedList, GeminiAIService.TaskTier.HEAVY)
        selected shouldBe "gemini-2.0-flash" // fallback default
    }

    test("negotiateBestModel uses cached model from database if not blacklisted") {
        every {
            mockQueries.getSelectedModel("preferred_gemini_model").executeAsOneOrNull()
        } returns "gemini-2.0-flash"

        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = mockDatabase,
            logger = null
        )

        val selected =
            negotiator.negotiateBestModel(availableModelsList, GeminiAIService.TaskTier.HEAVY)
        selected shouldBe "gemini-2.0-flash"

        // Should not negotiate or save since it used the cache
        verify(exactly = 0) { mockQueries.insertModel(any(), any(), any()) }
    }

    test("negotiateBestModel ignores cached model if it is blacklisted") {
        every {
            mockQueries.getSelectedModel("preferred_gemini_model").executeAsOneOrNull()
        } returns "gemini-2.5-flash"

        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = mockDatabase,
            logger = null
        )

        // Blacklist gemini-2.5-flash
        negotiator.blacklistModel("gemini-2.5-flash")

        // Should ignore cache, select next best available non-blacklisted model (gemini-2.0-flash), and save it
        val selected =
            negotiator.negotiateBestModel(availableModelsList, GeminiAIService.TaskTier.HEAVY)
        selected shouldBe "gemini-2.0-flash"

        verify(exactly = 1) {
            mockQueries.insertModel("preferred_gemini_model", "gemini-2.0-flash", any())
        }
    }

    test("evictFromCache deletes preferred model from database") {
        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = mockDatabase,
            logger = null
        )

        negotiator.evictFromCache("gemini-2.5-flash")
        verify(exactly = 1) { mockQueries.deleteModel("preferred_gemini_model") }
    }

    test("getAvailableModels parses model list successfully") {
        val jsonResponse = """
            {
                "models": [
                    {
                        "name": "models/gemini-2.5-flash",
                        "supportedGenerationMethods": ["generateContent"]
                    }
                ]
            }
        """.trimIndent()

        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = jsonResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) { json() }
        }

        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = mockClient,
            database = null,
            logger = null
        )

        val models = negotiator.getAvailableModels()
        models.size shouldBe 1
        models[0].name shouldBe "models/gemini-2.5-flash"
        models[0].supportedGenerationMethods shouldBe listOf("generateContent")
    }

    test("getAvailableModels returns empty list on network error") {
        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError
            )
        })

        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = mockClient,
            database = null,
            logger = null
        )

        val models = negotiator.getAvailableModels()
        models.isEmpty() shouldBe true
    }
})
