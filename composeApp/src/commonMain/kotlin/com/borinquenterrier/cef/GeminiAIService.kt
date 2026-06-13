package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray

/**
 * Common implementation for interacting with the Google Gemini API.
 */
class GeminiAIService(
    apiKey: String? = null,
    accessToken: String? = null,
    private val logger: Logger? = null,
    database: AppDatabase? = null,
    settings: com.russhwolf.settings.Settings? = null,
    customClient: HttpClient? = null,
    /** Injectable delay — override in tests to skip real sleeps. */
    delayFn: suspend (Long) -> Unit = { ms -> kotlinx.coroutines.delay(ms) }
) {
    private val tag = "GeminiAI"
    private val telemetryManager = settings?.let { TelemetryManager(it) }
    private val client = customClient ?: HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    private val modelNegotiator = GeminiModelNegotiator(
        apiKey = apiKey,
        accessToken = accessToken,
        client = client,
        database = database,
        logger = logger
    )

    private val requestExecutor = GeminiRequestExecutor(
        client = client,
        apiKey = apiKey,
        accessToken = accessToken,
        logger = logger,
        telemetryManager = telemetryManager,
        modelNegotiator = modelNegotiator,
        delayFn = delayFn
    )

    enum class TaskTier { HEAVY, LIGHT }

    companion object {
        fun extractSourceYears(sourceText: String): Set<Int> =
            GeminiResponseParser.extractSourceYears(sourceText)

        fun filterToSourceYears(events: List<Event>, sourceYears: Set<Int>): List<Event> =
            GeminiResponseParser.filterToSourceYears(events, sourceYears)

        fun parseEventsJson(
            responseText: String,
            telemetry: TelemetryManager? = null
        ): List<Event> =
            GeminiResponseParser.parseEventsJson(responseText, telemetry)

        internal fun parseDecomposeTaskJson(responseText: String): List<DecomposedTask> =
            GeminiResponseParser.parseDecomposeTaskJson(responseText)

        internal fun parseCategorizeSourceJson(responseText: String): SourceCategory =
            GeminiResponseParser.parseCategorizeSourceJson(responseText)

        internal fun clearBlacklistForTesting() {
            GeminiModelNegotiator.clearBlacklistForTesting()
            GeminiRequestExecutor.clearRateLimitResetForTesting()
        }
    }

    private fun buildGeminiBody(
        prompt: String,
        temperature: Double = 0.0,
        responseMimeType: String? = "application/json"
    ): JsonObject = GeminiBodyBuilder.buildJsonRequestBody(prompt, temperature, responseMimeType)

    private suspend fun <T> executeWithRetry(
        maxAttempts: Int = 5,
        tier: TaskTier = TaskTier.HEAVY,
        body: (modelName: String) -> JsonObject,
        parseResponse: (responseText: String) -> T
    ): T = requestExecutor.executeWithRetry(maxAttempts, tier, body, parseResponse)

    suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event> {
        val combinedJson = buildJsonArray {
            fragments.forEach { fragment ->
                add(Json.parseToJsonElement(fragment.toJson()))
            }
        }.toString()
        return generateCalendarEventsFromPrompt(
            AiPrompts.getSourceEventExtractionPrompt(
                combinedJson
            )
        )
    }

    suspend fun generateCalendarEventsFromPrompt(prompt: String): List<Event> {
        return executeWithRetry(
            maxAttempts = 5,
            tier = TaskTier.HEAVY,
            body = { _ -> buildGeminiBody(prompt) },
            parseResponse = { responseText ->
                try {
                    parseEventsJson(responseText)
                } catch (e: Exception) {
                    telemetryManager?.logJsonError()
                    throw e
                }
            }
        )
    }

    suspend fun generateStudyPlan(
        syllabusText: String,
        existingSchedule: String = "",
        preferences: StudyPreferences = StudyPreferences()
    ): List<Event> {
        return generateCalendarEventsFromPrompt(
            AiPrompts.getSyllabusStudyPlanPrompt(syllabusText, existingSchedule, preferences)
        )
    }

    suspend fun decomposeTask(taskTitle: String, dueDate: String): List<DecomposedTask> {
        val prompt = AiPrompts.getTaskDecompositionPrompt(taskTitle, dueDate)
        return executeWithRetry(
            maxAttempts = 5,
            tier = TaskTier.LIGHT,
            body = { _ -> buildGeminiBody(prompt) },
            parseResponse = { responseText -> parseDecomposeTaskJson(responseText) }
        )
    }

    suspend fun generateChatResponse(prompt: String): String {
        return try {
            executeWithRetry(
                maxAttempts = 3,
                tier = TaskTier.LIGHT,
                body = { _ -> buildGeminiBody(prompt, responseMimeType = null) },
                parseResponse = { responseText -> responseText }
            )
        } catch (e: Exception) {
            logger?.e(tag, "Failed to generate chat response: ${e.message}")
            "Error: ${e.message}"
        }
    }

    suspend fun analyzeDocument(text: String): String? {
        return try {
            executeWithRetry(
                maxAttempts = 3,
                tier = TaskTier.HEAVY,
                body = { _ ->
                    buildGeminiBody(
                        AiPrompts.getDocumentIntelligencePrompt(text),
                        responseMimeType = null
                    )
                },
                parseResponse = { responseText -> responseText }
            )
        } catch (e: Exception) {
            if (e.message?.contains("QuotaExhausted", ignoreCase = true) == true) {
                throw e
            }
            logger?.e(tag, "Failed to analyze document: ${e.message}")
            null
        }
    }

    suspend fun categorizeSource(text: String): SourceCategory {
        val textSample = if (text.length > 50000) text.substring(0, 50000) else text
        val prompt = AiPrompts.getSourceCategorizationPrompt(textSample)

        return try {
            executeWithRetry(
                maxAttempts = 3,
                tier = TaskTier.LIGHT,
                body = { _ -> buildGeminiBody(prompt) },
                parseResponse = { responseText -> parseCategorizeSourceJson(responseText) }
            )
        } catch (e: Exception) {
            if (e.message?.contains("QuotaExhausted", ignoreCase = true) == true) {
                throw e
            }
            logger?.e(
                tag,
                "Failed to categorize source after retries, defaulting to OTHER. Error: ${e.message}"
            )
            SourceCategory.OTHER
        }
    }
}

@Serializable
data class GeminiResponse(val candidates: List<Candidate> = emptyList())

@Serializable
data class Content(val parts: List<Part>)

@Serializable
data class Part(val text: String)

@Serializable
data class Candidate(val content: Content)
