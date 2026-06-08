package com.borinquenterrier.cef

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Clock
import com.borinquenterrier.cef.db.AppDatabase

/**
 * Common implementation for interacting with the Google Gemini API.
 */
class GeminiAIService(
    private val apiKey: String? = null,
    private val accessToken: String? = null,
    private val logger: Logger? = null,
    private val database: AppDatabase? = null,
    private val settings: com.russhwolf.settings.Settings? = null,
    private val customClient: HttpClient? = null,
    /** Injectable delay — override in tests to skip real sleeps. */
    private val delayFn: suspend (Long) -> Unit = { ms -> kotlinx.coroutines.delay(ms) }
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

        fun parseEventsJson(responseText: String, telemetry: TelemetryManager? = null): List<Event> =
            GeminiResponseParser.parseEventsJson(responseText, telemetry)

        internal fun parseDecomposeTaskJson(responseText: String): List<DecomposedTask> =
            GeminiResponseParser.parseDecomposeTaskJson(responseText)

        internal fun parseCategorizeSourceJson(responseText: String): SourceCategory =
            GeminiResponseParser.parseCategorizeSourceJson(responseText)

        internal fun clearBlacklistForTesting() {
            GeminiModelNegotiator.clearBlacklistForTesting()
        }
    }

    private fun buildGeminiBody(
        prompt: String,
        temperature: Double = 0.0,
        responseMimeType: String? = "application/json"
    ): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            addJsonObject {
                putJsonArray("parts") {
                    addJsonObject { put("text", prompt) }
                }
            }
        }
        putJsonObject("generationConfig") {
            if (responseMimeType == "application/json") {
                put("responseMimeType", responseMimeType)
            }
            put("temperature", temperature)
        }
    }

    private suspend fun postToModel(modelName: String, body: JsonObject): HttpResponse {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
        val authUrl = if (apiKey != null) "$url?key=$apiKey" else url
        return client.post(authUrl) {
            contentType(ContentType.Application.Json)
            if (apiKey == null && accessToken != null) {
                header("Authorization", "Bearer $accessToken")
            }
            setBody(body)
        }
    }

    private suspend fun <T> executeWithRetry(
        maxAttempts: Int = 5,
        tier: TaskTier = TaskTier.HEAVY,
        body: (modelName: String) -> JsonObject,
        parseResponse: (responseText: String) -> T
    ): T {
        val available = modelNegotiator.getAvailableModels()
        var attempts = 0
        var lastError: Exception? = null
        var lastNegotiatedModel: String? = null

        while (attempts < maxAttempts) {
            val modelName = modelNegotiator.negotiateBestModel(available, tier)
            lastNegotiatedModel = modelName
            try {
                val httpResponse = postToModel(modelName, body(modelName))
                val responseBody = httpResponse.bodyAsText()

                // Fatal errors — throw immediately
                if (httpResponse.status == HttpStatusCode.Unauthorized) {
                    logger?.e(tag, "401 Unauthorized: Your API Key or Access Token is invalid/expired.")
                    throw Exception("Unauthorized")
                }
                if (httpResponse.status == HttpStatusCode.Forbidden) {
                    logger?.e(tag, "403 Forbidden: Ensure the Gemini API is enabled in your Google Cloud Project.")
                    throw Exception("Forbidden")
                }

                // Structural errors — blacklist model, try next
                if (httpResponse.status == HttpStatusCode.NotFound) {
                    modelNegotiator.blacklistModel(modelName)
                    modelNegotiator.evictFromCache(modelName)
                    logger?.d(tag, "⚠️ Model $modelName returned 404 (Not Found). Blacklisted. Trying next model...")
                    attempts++
                    continue
                }
                if (httpResponse.status == HttpStatusCode.BadRequest && responseBody.contains("response modalities")) {
                    modelNegotiator.blacklistModel(modelName)
                    modelNegotiator.evictFromCache(modelName)
                    logger?.d(tag, "⚠️ Model $modelName does not support text responses. Blacklisted. Trying next model...")
                    attempts++
                    continue
                }

                // Quota exhaustion (RPD)
                if (httpResponse.status == HttpStatusCode.TooManyRequests) {
                    val hasRetryHint = responseBody.contains("retry in", ignoreCase = true)
                    val hasQuotaWord = responseBody.contains("quota", ignoreCase = true)
                    val hasExhaustionWord = responseBody.contains("exhausted", ignoreCase = true) ||
                        responseBody.contains("exceeded", ignoreCase = true) ||
                        responseBody.contains("limit", ignoreCase = true)
                    val isQuotaExhausted = !hasRetryHint && hasQuotaWord && hasExhaustionWord
                    if (isQuotaExhausted) {
                        logger?.e(tag, "🚫 Daily quota exhausted for model $modelName. No point retrying until quota resets.")
                        telemetryManager?.logRateLimitError()
                        throw Exception("QuotaExhausted: Your free-tier daily request limit has been reached. Try again tomorrow or upgrade your Google AI Studio plan.")
                    }
                }

                // Transient 5xx / 503 errors
                if (httpResponse.status == HttpStatusCode.ServiceUnavailable ||
                    httpResponse.status.value >= 500
                ) {
                    telemetryManager?.logRateLimitError()
                    modelNegotiator.blacklistModel(modelName)
                    modelNegotiator.evictFromCache(modelName)
                    logger?.e(tag, "⚠️ Model $modelName returned ${httpResponse.status}. Evicted from cache and blacklisted. Trying next model...")
                    attempts++
                    continue
                }

                // Transient 429 Too Many Requests
                if (httpResponse.status == HttpStatusCode.TooManyRequests) {
                    telemetryManager?.logRateLimitError()
                    attempts++

                    val delayMs: Long = resolveRetryDelay(
                        status = httpResponse.status,
                        headers = httpResponse.headers,
                        body = responseBody,
                        attempts = attempts,
                        tag = tag
                    )

                    if (delayMs > 10000L) {
                        throw Exception("QuotaExhausted: Rate limit delay of ${delayMs}ms exceeds the maximum wait threshold of 10 seconds.")
                    }

                    delayFn(delayMs)
                    continue
                }

                if (!httpResponse.status.isSuccess()) {
                    throw Exception("Gemini API Error (${httpResponse.status}): $responseBody")
                }

                // Success
                val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)
                val responseText = geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("Empty response from AI")

                return parseResponse(responseText)

            } catch (e: Exception) {
                if (e.message == "Unauthorized" || 
                    e.message == "Forbidden" || 
                    e.message?.startsWith("QuotaExhausted") == true
                ) throw e

                lastError = e
                logger?.e(tag, "Attempt ${attempts + 1} failed: ${e.message}")
                attempts++
                val delayMs = 1000L * (1 shl (attempts - 1))
                delayFn(delayMs)
            }
        }

        if (lastNegotiatedModel != null) {
            modelNegotiator.blacklistModel(lastNegotiatedModel)
            modelNegotiator.evictFromCache(lastNegotiatedModel)
            logger?.e(tag, "⚠️ Model $lastNegotiatedModel failed all $maxAttempts retry attempts. Evicted from cache and blacklisted.")
        }

        throw lastError ?: Exception("Failed after $maxAttempts attempts")
    }

    internal fun resolveRetryDelay(
        status: HttpStatusCode,
        headers: io.ktor.http.Headers,
        body: String,
        attempts: Int,
        tag: String
    ): Long {
        val bodyRetryMatch = Regex("""retry in (\d+(?:\.\d+)?)\s*s""", RegexOption.IGNORE_CASE)
            .find(body)
        if (bodyRetryMatch != null) {
            val seconds = bodyRetryMatch.groupValues[1].toDoubleOrNull()
            if (seconds != null) {
                val ms = (seconds * 1000).toLong() + 500L
                logger?.d(tag, "⏱️ Rate-limited — server body says retry in ${seconds}s. Waiting ${ms}ms.")
                return ms
            }
        }

        val resetHeader = headers["x-ratelimit-reset"] ?: headers["X-RateLimit-Reset"]
        if (resetHeader != null) {
            val resetEpoch = resetHeader.toLongOrNull()
            if (resetEpoch != null) {
                val nowSeconds = Clock.System.now().toEpochMilliseconds() / 1000L
                val waitSeconds = (resetEpoch - nowSeconds).coerceAtLeast(1L)
                val ms = waitSeconds * 1000L + 500L
                logger?.d(tag, "⏱️ Rate-limited — x-ratelimit-reset in ${waitSeconds}s. Waiting ${ms}ms.")
                return ms
            }
        }

        val retryAfter = headers["Retry-After"] ?: headers["retry-after"]
        if (retryAfter != null) {
            val seconds = retryAfter.toLongOrNull()
            if (seconds != null) {
                val ms = seconds * 1000L
                logger?.d(tag, "⏱️ Rate-limited — Retry-After: ${seconds}s. Waiting ${ms}ms.")
                return ms
            }
        }

        val baseDelay = if (status == HttpStatusCode.TooManyRequests) 2000L else 1000L
        val ms = baseDelay * (1 shl (attempts - 1))
        logger?.d(tag, "⚠️ Transient error ($status). No server hint — exponential backoff ${ms}ms (attempt $attempts).")
        return ms
    }

    suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event> {
        val combinedJson = buildJsonArray {
            fragments.forEach { fragment ->
                add(Json.parseToJsonElement(fragment.toJson()))
            }
        }.toString()
        return generateCalendarEventsFromPrompt(AiPrompts.getSourceEventExtractionPrompt(combinedJson))
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
                body = { _ -> buildGeminiBody(AiPrompts.getDocumentIntelligencePrompt(text), responseMimeType = null) },
                parseResponse = { responseText -> responseText }
            )
        } catch (e: Exception) {
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
            logger?.e(tag, "Failed to categorize source after retries, defaulting to OTHER. Error: ${e.message}")
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
