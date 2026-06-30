package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray

/**
 * Common implementation for interacting with the Google Gemini API.
 */
class GeminiAIService private constructor(
    private val apiKey: String?,
    private val accessToken: String?,
    private val logger: Logger?,
    private val database: AppDatabase?,
    private val settings: com.russhwolf.settings.Settings?,
    private val customClient: HttpClient?,
    private val delayFn: suspend (Long) -> Unit
) {
    /** Production constructor. apiKey, logger, settings are required. database is optional — omitting disables persistent model blacklist caching. */
    constructor(
        apiKey: String,
        logger: Logger,
        settings: com.russhwolf.settings.Settings,
        database: AppDatabase? = null
    ) : this(
        apiKey = apiKey,
        accessToken = null,
        logger = logger,
        database = database,
        settings = settings,
        customClient = null,
        delayFn = { ms -> kotlinx.coroutines.delay(ms) }
    )

    /** Test constructor — injects a mock HTTP client; optional logger/settings for tests that verify those paths. */
    internal constructor(
        apiKey: String,
        customClient: HttpClient,
        logger: Logger? = null,
        settings: com.russhwolf.settings.Settings? = null,
        delayFn: suspend (Long) -> Unit = { ms -> kotlinx.coroutines.delay(ms) }
    ) : this(
        apiKey = apiKey,
        accessToken = null,
        logger = logger,
        database = null,
        settings = settings,
        customClient = customClient,
        delayFn = delayFn
    )

    private val tag = "GeminiAI"
    private val telemetryManager = settings?.let { TelemetryManager(it) }
    private val client = customClient ?: HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        // Bounded timeouts so a hung request fails fast and is retried *within* its paced queue
        // slot, instead of dangling to an unbounded socket timeout (the failure seen in traces:
        // `socket_timeout=unknown`). Generous enough for heavy prompts; not unlimited.
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000   // whole request ceiling
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 90_000     // max idle between data chunks
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
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

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

    suspend fun postToModel(modelName: String, body: JsonObject): HttpResponse =
        requestExecutor.postToModel(modelName, body)

    private suspend fun <T> executeWithRetry(
        maxAttempts: Int,
        tier: TaskTier,
        family: PromptFamily,
        body: (modelName: String) -> JsonObject,
        parseResponse: (responseText: String) -> T
    ): T = requestExecutor.executeWithRetry(maxAttempts, tier, family, body, parseResponse)

    suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event> {
        val isText = fragments.firstOrNull()?.type == SourceType.TEXT
        if (isText && fragments.size > SourceFragmentBatcher.BATCH_SIZE) {
            // Direct callers (tests, legacy paths) that pass all fragments at once still get
            // correct batching. EventGenerationService pre-batches and calls with small slices.
            val batches = SourceFragmentBatcher.batch(fragments)
            val allEvents = mutableListOf<Event>()
            for (batch in batches) {
                val combinedJson = buildJsonArray {
                    batch.forEach { fragment ->
                        add(Json.parseToJsonElement(fragment.toJson()))
                    }
                }.toString()
                allEvents.addAll(generateCalendarEventsFromPrompt(
                    AiPrompts.getSourceEventExtractionPrompt(combinedJson)
                ))
            }
            return allEvents
        } else {
            val combinedJson = buildJsonArray {
                fragments.forEach { fragment ->
                    add(Json.parseToJsonElement(fragment.toJson()))
                }
            }.toString()
            return generateCalendarEventsFromPrompt(
                AiPrompts.getSourceEventExtractionPrompt(combinedJson)
            )
        }
    }

    suspend fun generateCalendarEventsFromPrompt(prompt: String): List<Event> =
        generateEventsFromPrompt(prompt, PromptFamily.EVENT_EXTRACTION)

    /**
     * Shared event-generation path. [family] selects which queue paces the call so that
     * extraction ([PromptFamily.EVENT_EXTRACTION]) and study-plan generation
     * ([PromptFamily.STUDY_PLAN]) run on independent queues and can't starve each other.
     */
    private suspend fun generateEventsFromPrompt(prompt: String, family: PromptFamily): List<Event> {
        return executeWithRetry(
            maxAttempts = 5,
            tier = TaskTier.HEAVY,
            family = family,
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
        return generateEventsFromPrompt(
            AiPrompts.getSyllabusStudyPlanPrompt(syllabusText, existingSchedule, preferences),
            PromptFamily.STUDY_PLAN
        )
    }

    suspend fun decomposeTask(taskTitle: String, dueDate: String): List<DecomposedTask> {
        val prompt = AiPrompts.getTaskDecompositionPrompt(taskTitle, dueDate)
        return executeWithRetry(
            maxAttempts = 5,
            tier = TaskTier.LIGHT,
            family = PromptFamily.STUDY_PLAN,
            body = { _ -> buildGeminiBody(prompt) },
            parseResponse = { responseText -> parseDecomposeTaskJson(responseText) }
        )
    }

    suspend fun generateChatResponse(prompt: String): String {
        return try {
            executeWithRetry(
                maxAttempts = 3,
                tier = TaskTier.LIGHT,
                family = PromptFamily.CHAT,
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
                family = PromptFamily.CATEGORIZATION,
                body = { _ ->
                    buildGeminiBody(
                        AiPrompts.getDocumentIntelligencePrompt(text),
                        responseMimeType = null
                    )
                },
                parseResponse = { responseText -> responseText }
            )
        } catch (e: Exception) {
            if (e.message.orEmpty().contains("QuotaExhausted", ignoreCase = true)) throw e
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
                family = PromptFamily.CATEGORIZATION,
                body = { _ -> buildGeminiBody(prompt) },
                parseResponse = { responseText -> parseCategorizeSourceJson(responseText) }
            )
        } catch (e: Exception) {
            if (e.message.orEmpty().contains("QuotaExhausted", ignoreCase = true)) throw e
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
