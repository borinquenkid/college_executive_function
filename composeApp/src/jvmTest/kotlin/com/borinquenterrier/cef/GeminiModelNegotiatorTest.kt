package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
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

class GeminiModelNegotiatorTest : FunSpec({

    // Use a real in-memory SQLite database to avoid MockK's ValueClassSupport NPE
    // with SQLDelight 2.3.x execute methods that return QueryResult<Long>.
    lateinit var database: AppDatabase

    beforeTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)
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
        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = database,
            logger = null
        )

        // HEAVY prefers: gemini-2.5-flash, gemini-2.5-pro, gemini-2.5-flash-lite, gemini-3.5-flash
        val selected =
            negotiator.negotiateBestModel(availableModelsList, GeminiAIService.TaskTier.HEAVY)
        selected shouldBe "gemini-2.5-flash"

        // Verify model was persisted to the database
        database.appDatabaseQueries.getSelectedModel("preferred_gemini_model")
            .executeAsOneOrNull() shouldBe "gemini-2.5-flash"
    }

    test("negotiateBestModel respects LIGHT task tier preferences") {
        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = database,
            logger = null
        )

        // LIGHT prefers: gemini-2.5-flash-lite, gemini-flash-lite-latest, gemini-2.5-flash, gemini-flash-latest
        val selected =
            negotiator.negotiateBestModel(availableModelsList, GeminiAIService.TaskTier.LIGHT)
        selected shouldBe "gemini-2.5-flash-lite"

        database.appDatabaseQueries.getSelectedModel("preferred_gemini_model")
            .executeAsOneOrNull() shouldBe "gemini-2.5-flash-lite"
    }

    test("negotiateBestModel filters out non-text/unsupported models") {
        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = database,
            logger = null
        )

        // Only offer image and tts models, should fall back to default
        val unsupportedList = listOf(
            ModelInfo("models/gemini-2.5-flash-preview-tts", listOf("generateContent")),
            ModelInfo("models/gemini-2.5-pro-image", listOf("generateContent"))
        )

        val selected =
            negotiator.negotiateBestModel(unsupportedList, GeminiAIService.TaskTier.HEAVY)
        selected shouldBe "gemini-2.5-flash" // fallback default
    }

    test("negotiateBestModel uses cached model from database if not blacklisted") {
        // Pre-seed the database with a cached model
        database.appDatabaseQueries.insertModel(
            "preferred_gemini_model", "gemini-2.0-flash",
            System.currentTimeMillis()
        )

        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = database,
            logger = null
        )

        val selected =
            negotiator.negotiateBestModel(availableModelsList, GeminiAIService.TaskTier.HEAVY)
        selected shouldBe "gemini-2.0-flash"

        // Cache entry should still be "gemini-2.0-flash" (not overwritten)
        database.appDatabaseQueries.getSelectedModel("preferred_gemini_model")
            .executeAsOneOrNull() shouldBe "gemini-2.0-flash"
    }

    test("negotiateBestModel ignores cached model if it is blacklisted") {
        database.appDatabaseQueries.insertModel(
            "preferred_gemini_model", "gemini-2.5-flash",
            System.currentTimeMillis()
        )

        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = database,
            logger = null
        )

        // Blacklist gemini-2.5-flash
        negotiator.blacklistModel("gemini-2.5-flash")

        // Should ignore cache, select next best available non-blacklisted model, and save it
        val selected =
            negotiator.negotiateBestModel(availableModelsList, GeminiAIService.TaskTier.HEAVY)
        selected shouldBe "gemini-2.5-flash-lite"

        database.appDatabaseQueries.getSelectedModel("preferred_gemini_model")
            .executeAsOneOrNull() shouldBe "gemini-2.5-flash-lite"
    }

    test("evictFromCache deletes preferred model from database") {
        database.appDatabaseQueries.insertModel(
            "preferred_gemini_model", "gemini-2.5-flash",
            System.currentTimeMillis()
        )

        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = database,
            logger = null
        )

        negotiator.evictFromCache("gemini-2.5-flash")

        database.appDatabaseQueries.getSelectedModel("preferred_gemini_model")
            .executeAsOneOrNull() shouldBe null
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

    test("cascade: server error in session 1 evicts model so session 2 renegotiates fresh") {
        // Session 1: DB has a cached model that then hits a 503
        database.appDatabaseQueries.insertModel(
            "preferred_gemini_model", "gemini-2.5-flash-lite",
            System.currentTimeMillis()
        )

        val negotiator1 = GeminiModelNegotiator(
            apiKey = "fake-key", accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = database, logger = null
        )
        // GeminiErrorHandler.handleServerError does exactly these two calls
        negotiator1.blacklistModel("gemini-2.5-flash-lite")
        negotiator1.evictFromCache("gemini-2.5-flash-lite")

        // DB must now be empty
        database.appDatabaseQueries.getSelectedModel("preferred_gemini_model")
            .executeAsOneOrNull() shouldBe null

        // Session 2: new process, blacklist is reset, DB has no cached model
        GeminiAIService.clearBlacklistForTesting()
        val negotiator2 = GeminiModelNegotiator(
            apiKey = "fake-key", accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = database, logger = null
        )

        val selected = negotiator2.negotiateBestModel(availableModelsList, GeminiAIService.TaskTier.HEAVY)

        // Must NOT re-use the evicted model; must renegotiate and save the new choice
        selected shouldBe "gemini-2.5-flash"
        database.appDatabaseQueries.getSelectedModel("preferred_gemini_model")
            .executeAsOneOrNull() shouldBe "gemini-2.5-flash"
    }

    test("deprecated 2.0-flash models are never selected even as fallback") {
        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = database,
            logger = null
        )

        // Offer only deprecated models + the HEAVY preference winner so the fallback path is exercised
        val deprecatedAndOneGood = listOf(
            ModelInfo("models/gemini-2.0-flash", listOf("generateContent")),
            ModelInfo("models/gemini-2.0-flash-lite", listOf("generateContent")),
            ModelInfo("models/gemini-2.0-flash-001", listOf("generateContent")),
            ModelInfo("models/gemini-2.0-flash-lite-001", listOf("generateContent")),
            ModelInfo("models/gemini-2.5-flash", listOf("generateContent"))
        )
        val selected = negotiator.negotiateBestModel(deprecatedAndOneGood, GeminiAIService.TaskTier.HEAVY)
        selected shouldBe "gemini-2.5-flash"
    }

    test("deprecated flash models are skipped by the flash-contains fallback") {
        val negotiator = GeminiModelNegotiator(
            apiKey = "fake-key",
            accessToken = null,
            client = HttpClient(MockEngine { respond("") }),
            database = database,
            logger = null
        )

        // Blacklist all HEAVY preferences except gemini-2.5-pro so the preference path hits
        // gemini-2.5-flash (blacklisted), gemini-2.5-pro (available) → selects gemini-2.5-pro.
        // The key assertion: gemini-2.0-flash is in the list but must NOT be selected.
        negotiator.blacklistModel("gemini-2.5-flash")
        negotiator.blacklistModel("gemini-2.5-flash-lite")
        negotiator.blacklistModel("gemini-3.5-flash")

        val mixedList = listOf(
            ModelInfo("models/gemini-2.0-flash", listOf("generateContent")),
            ModelInfo("models/gemini-2.0-flash-lite", listOf("generateContent")),
            ModelInfo("models/gemini-2.0-flash-001", listOf("generateContent")),
            ModelInfo("models/gemini-2.0-flash-lite-001", listOf("generateContent")),
            ModelInfo("models/gemini-2.5-pro", listOf("generateContent"))
        )
        val selected = negotiator.negotiateBestModel(mixedList, GeminiAIService.TaskTier.HEAVY)
        // Without the denylist, the flash-contains fallback would pick gemini-2.0-flash
        selected shouldBe "gemini-2.5-pro"
        (selected in GeminiModelNegotiator.DEPRECATED_MODELS) shouldBe false
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
