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

    /**
     * Describes how demanding a task is, which drives model selection.
     *
     * - [HEAVY]: Long-context reasoning (syllabus parsing, study plans, document analysis).
     *   Prefers the most capable Flash model available.
     * - [LIGHT]: Short, latency-sensitive calls (chat, categorisation, task decomposition).
     *   Prefers the smallest Flash model to preserve daily quota.
     */
    enum class TaskTier { HEAVY, LIGHT }

    companion object {
        private val json = Json { 
            ignoreUnknownKeys = true 
            isLenient = true
        }

        // Global blacklist to persist across service recreations during a session
        private val blacklistedModels = mutableMapOf<String, Long>()
        private const val BLACKLIST_DURATION_MS = 60 * 60 * 1000L // 1 hour
        private const val PREFERRED_MODEL_KEY = "preferred_gemini_model"

        /**
         * Ordered model preferences by task tier.
         *
         * Only three models per tier — chosen because they are the only ones that:
         *  (a) exist on the free AI Studio tier,
         *  (b) have a 1M-token context window, and
         *  (c) are actively maintained by Google.
         *
         * Heavy tasks try the most capable Flash first (better reasoning for parsing).
         * Light tasks start with the smallest Flash (fastest, cheapest, preserves daily quota).
         */
        internal val HEAVY_PREFERENCES = listOf(
            "gemini-2.5-flash",       // best reasoning, 1M ctx, free tier
            "gemini-2.0-flash",       // reliable workhorse fallback
            "gemini-2.5-flash-lite",  // lite variant — less capable but still 1M ctx
            "gemini-2.0-flash-lite"   // last resort if 2.5/2.0 are exhausted
        )

        internal val LIGHT_PREFERENCES = listOf(
            "gemini-2.5-flash-lite",  // fastest & cheapest — confirmed free tier, 1M ctx
            "gemini-2.0-flash-lite",  // fallback if 2.5-lite unavailable
            "gemini-2.0-flash",       // step up if both lite variants unavailable
            "gemini-2.5-flash"        // last resort (overkill for light tasks)
        )

        /** Clears the in-memory blacklist. Call in test teardown to prevent cross-test contamination. */
        internal fun clearBlacklistForTesting() = blacklistedModels.clear()

        internal val YEAR_PATTERN = Regex("""\b(20\d{2})\b""")

        fun extractSourceYears(sourceText: String): Set<Int> =
            YEAR_PATTERN.findAll(sourceText).map { it.value.toInt() }.toSet()

        fun filterToSourceYears(events: List<Event>, sourceYears: Set<Int>): List<Event> {
            if (sourceYears.isEmpty()) return events
            return events.filter { event ->
                val year = when (event) {
                    is TimeEvent -> event.date.year
                    is DayEvent -> event.date.year
                }
                year in sourceYears
            }
        }

        fun parseEventsJson(responseText: String): List<Event> {
            val cleanJson = responseText.trim()
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()

            val root = json.parseToJsonElement(cleanJson)
            val jsonArray = if (root is JsonArray) {
                root
            } else if (root is JsonObject && root.containsKey("events")) {
                root["events"]!!.jsonArray
            } else {
                throw Exception("Unexpected JSON structure: $cleanJson")
            }

            return jsonArray.map { element ->
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: "Untitled Event"
                val type = obj["type"]?.jsonPrimitive?.content ?: "DAY"
                val dateStr = obj["date"]?.jsonPrimitive?.content ?: "2024-01-01"
                val categoryStr = obj["category"]?.jsonPrimitive?.content ?: "REGULAR"
                val warning = obj["warning"]?.jsonPrimitive?.content
                val gradeWeight = obj["gradeWeight"]?.jsonPrimitive?.let { prim ->
                    prim.doubleOrNull?.toFloat() ?: prim.content.toFloatOrNull()
                }
                val category = try {
                    AcademicCategory.valueOf(categoryStr)
                } catch (e: Exception) {
                    AcademicCategory.REGULAR
                }

                val date = try { LocalDate.parse(dateStr) } catch (e: Exception) { LocalDate(2024,1,1) }

                if (type == "TIME") {
                    val startTimeStr = obj["startTime"]?.jsonPrimitive?.content ?: "09:00"
                    val endTimeStr = obj["endTime"]?.jsonPrimitive?.content ?: "10:00"
                    val startTime = try {
                        val parts = startTimeStr.split(":")
                        LocalTime(parts[0].toInt(), parts[1].toInt())
                    } catch (e: Exception) { LocalTime(9, 0) }
                    val endTime = try {
                        val parts = endTimeStr.split(":")
                        LocalTime(parts[0].toInt(), parts[1].toInt())
                    } catch (e: Exception) { LocalTime(10, 0) }
                    TimeEvent(
                        title = title,
                        source = EventSource.AI_GENERATED,
                        date = date,
                        startTime = startTime,
                        endTime = endTime,
                        category = category,
                        warning = warning,
                        gradeWeight = gradeWeight
                    )
                } else {
                    DayEvent(
                        title = title,
                        source = EventSource.AI_GENERATED,
                        category = category,
                        date = date,
                        warning = warning,
                        gradeWeight = gradeWeight
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Core retry / backoff infrastructure
    // -------------------------------------------------------------------------

    /**
     * Builds a Gemini generateContent request body with the standard structure.
     * When [responseMimeType] is "application/json", native JSON output mode is enabled.
     * Pass null (or any non-json value) to omit the field and receive unstructured text.
     */
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

    /**
     * Posts to the Gemini generateContent endpoint for [modelName] with [body] and
     * returns the raw [HttpResponse]. Auth headers are applied automatically.
     */
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

    /**
     * Single-source-of-truth retry engine.
     *
     * Behaviour:
     *  - **Transient** (429, 503, any 5xx): exponential back-off; does NOT blacklist.
     *  - **Structural** (404, 400 modality): blacklists model locally, tries next best.
     *  - **Fatal** (401, 403, other 4xx): throws immediately.
     *
     * @param maxAttempts   Total allowed attempts (default 5).
     * @param tier          [TaskTier] hint — drives which model is preferred (default [TaskTier.HEAVY]).
     * @param body          Lambda that returns the [JsonObject] to POST for a given [modelName].
     * @param parseResponse Lambda that turns the raw response text into [T].
     */
    private suspend fun <T> executeWithRetry(
        maxAttempts: Int = 5,
        tier: TaskTier = TaskTier.HEAVY,
        body: (modelName: String) -> JsonObject,
        parseResponse: (responseText: String) -> T
    ): T {
        val available = getAvailableModels()
        var attempts = 0
        var lastError: Exception? = null

        while (attempts < maxAttempts) {
            val modelName = negotiateBestModel(available, tier)
            try {
                val httpResponse = postToModel(modelName, body(modelName))
                val responseBody = httpResponse.bodyAsText()

                // --- Fatal errors — throw immediately ---
                if (httpResponse.status == HttpStatusCode.Unauthorized) {
                    logger?.e(tag, "401 Unauthorized: Your API Key or Access Token is invalid/expired.")
                    throw Exception("Unauthorized")
                }
                if (httpResponse.status == HttpStatusCode.Forbidden) {
                    logger?.e(tag, "403 Forbidden: Ensure the Gemini API is enabled in your Google Cloud Project.")
                    throw Exception("Forbidden")
                }

                // --- Structural errors — blacklist model, try next ---
                if (httpResponse.status == HttpStatusCode.NotFound) {
                    val expiry = Clock.System.now().toEpochMilliseconds() + BLACKLIST_DURATION_MS
                    blacklistedModels[modelName] = expiry
                    database?.appDatabaseQueries?.deleteModel(PREFERRED_MODEL_KEY)
                    logger?.d(tag, "⚠️ Model $modelName returned 404 (Not Found). Blacklisted. Trying next model...")
                    attempts++
                    continue
                }
                if (httpResponse.status == HttpStatusCode.BadRequest && responseBody.contains("response modalities")) {
                    val expiry = Clock.System.now().toEpochMilliseconds() + BLACKLIST_DURATION_MS
                    blacklistedModels[modelName] = expiry
                    database?.appDatabaseQueries?.deleteModel(PREFERRED_MODEL_KEY)
                    logger?.d(tag, "⚠️ Model $modelName does not support text responses. Blacklisted. Trying next model...")
                    attempts++
                    continue
                }

                // --- Transient errors — extract server-supplied wait time, else exponential back-off ---
                if (httpResponse.status == HttpStatusCode.TooManyRequests ||
                    httpResponse.status == HttpStatusCode.ServiceUnavailable ||
                    httpResponse.status.value >= 500
                ) {
                    if (httpResponse.status == HttpStatusCode.TooManyRequests ||
                        httpResponse.status == HttpStatusCode.ServiceUnavailable
                    ) {
                        telemetryManager?.logRateLimitError()
                    }
                    attempts++

                    val delayMs: Long = resolveRetryDelay(
                        status = httpResponse.status,
                        headers = httpResponse.headers,
                        body = responseBody,
                        attempts = attempts,
                        tag = tag
                    )

                    delayFn(delayMs)
                    continue
                }

                // --- Other non-success errors ---
                if (!httpResponse.status.isSuccess()) {
                    throw Exception("Gemini API Error (${httpResponse.status}): $responseBody")
                }

                // --- Success ---
                val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)
                val responseText = geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("Empty response from AI")

                return parseResponse(responseText)

            } catch (e: Exception) {
                // Propagate fatal errors immediately
                if (e.message == "Unauthorized" || e.message == "Forbidden") throw e

                lastError = e
                logger?.e(tag, "Attempt ${attempts + 1} failed: ${e.message}")
                attempts++
                val delayMs = 1000L * (1 shl (attempts - 1))
                delayFn(delayMs)
            }
        }
        throw lastError ?: Exception("Failed after $maxAttempts attempts")
    }

    // -------------------------------------------------------------------------
    // Model discovery & negotiation
    // -------------------------------------------------------------------------

    /**
     * Determines how long to wait before the next retry after a transient error.
     *
     * Priority order (Gemini-specific):
     *  1. Response body contains "retry in X.Xs" — Gemini's most common signal
     *  2. `x-ratelimit-reset` epoch header — absolute reset timestamp (seconds)
     *  3. `Retry-After` header — standard RFC 7231 integer seconds
     *  4. Exponential back-off — fallback when no server hint is available
     */
    internal fun resolveRetryDelay(
        status: HttpStatusCode,
        headers: io.ktor.http.Headers,
        body: String,
        attempts: Int,
        tag: String
    ): Long {
        // 1. Body hint: "Please retry in 17.6s" or "retry in 30s"
        val bodyRetryMatch = Regex("""retry in (\d+(?:\.\d+)?)\s*s""", RegexOption.IGNORE_CASE)
            .find(body)
        if (bodyRetryMatch != null) {
            val seconds = bodyRetryMatch.groupValues[1].toDoubleOrNull()
            if (seconds != null) {
                val ms = (seconds * 1000).toLong() + 500L // +500ms buffer
                logger?.d(tag, "⏱️ Rate-limited — server body says retry in ${seconds}s. Waiting ${ms}ms.")
                return ms
            }
        }

        // 2. x-ratelimit-reset: absolute epoch seconds (compute delta from now)
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

        // 3. Standard Retry-After header (integer seconds)
        val retryAfter = headers["Retry-After"] ?: headers["retry-after"]
        if (retryAfter != null) {
            val seconds = retryAfter.toLongOrNull()
            if (seconds != null) {
                val ms = seconds * 1000L
                logger?.d(tag, "⏱️ Rate-limited — Retry-After: ${seconds}s. Waiting ${ms}ms.")
                return ms
            }
        }

        // 4. Exponential back-off fallback
        val baseDelay = if (status == HttpStatusCode.TooManyRequests) 2000L else 1000L
        val ms = baseDelay * (1 shl (attempts - 1))
        logger?.d(tag, "⚠️ Transient error ($status). No server hint — exponential backoff ${ms}ms (attempt $attempts).")
        return ms
    }

    private suspend fun getAvailableModels(): List<ModelInfo> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models"
        val authUrl = if (apiKey != null) "$url?key=$apiKey" else url
        
        return try {
            val response: HttpResponse = client.get(authUrl) {
                if (apiKey == null && accessToken != null) {
                    header("Authorization", "Bearer $accessToken")
                }
            }
            
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                logger?.e(tag, "Failed to get available models: ${response.status}. Body: $body")
                return emptyList()
            }

            val modelList = json.decodeFromString<ModelListResponse>(response.bodyAsText())
            modelList.models
        } catch (e: Exception) {
            logger?.e(tag, "Exception fetching models: ${e.message}")
            emptyList()
        }
    }

    private suspend fun negotiateBestModel(
        available: List<ModelInfo>,
        tier: TaskTier = TaskTier.HEAVY
    ): String {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        
        // 1. Check SQLite Cache first
        val cachedModel = database?.appDatabaseQueries?.getSelectedModel(PREFERRED_MODEL_KEY)?.executeAsOneOrNull()
        if (cachedModel != null) {
            val expiry = blacklistedModels[cachedModel]
            if (expiry == null || currentTime > expiry) {
                logger?.d(tag, "Using cached model from database: $cachedModel")
                return cachedModel
            } else {
                logger?.d(tag, "Cached model $cachedModel is currently blacklisted. Re-negotiating...")
            }
        }

        // 2. Perform negotiation
        val generationCapable = available.filter { it.supportedGenerationMethods.contains("generateContent") }
        val names = generationCapable.map { it.name.removePrefix("models/") }
        
        logger?.d(tag, "Negotiation Step - Available names: ${names.joinToString(", ")}")

        // Filter out non-text models (TTS, image, audio, specialized) and blacklisted ones
        val textCapableNames = names.filter { name ->
            val expiry = blacklistedModels[name]
            val notBlacklisted = expiry == null || currentTime > expiry
            val isTextCapable = !name.contains("tts") &&
                !name.contains("-image") &&
                !name.contains("-audio") &&
                !name.contains("robotics") &&
                !name.contains("lyria") &&
                !name.contains("deep-research") &&
                !name.contains("computer-use") &&
                !name.contains("nano-banana")
            notBlacklisted && isTextCapable
        }

        logger?.d(tag, "Negotiating best model. Available: ${names.size}, Text-capable & non-blacklisted: ${textCapableNames.size}")

        // Select ordered preferences based on task tier.
        // Heavy tasks (syllabus parsing, study plans) → most-capable Flash first.
        // Light tasks (chat, categorise) → smallest Flash first to preserve daily quota.
        val preferences = if (tier == TaskTier.HEAVY) HEAVY_PREFERENCES else LIGHT_PREFERENCES

        logger?.d(tag, "Task tier: $tier — preference order: ${preferences.joinToString(", ")}")

        // Find best match among text-capable models, with graceful fallbacks
        val selected = preferences.firstOrNull { pref -> textCapableNames.contains(pref) }
            ?: textCapableNames.firstOrNull { it.contains("flash") && !it.contains("tts") }
            ?: textCapableNames.firstOrNull()
            ?: "gemini-2.0-flash" // last-resort default (always exists on free tier)

        // 3. Save to Cache
        try {
            database?.appDatabaseQueries?.insertModel(PREFERRED_MODEL_KEY, selected, currentTime)
            logger?.d(tag, "Saved newly negotiated model to database: $selected")
        } catch (e: Exception) {
            logger?.e(tag, "Failed to save model to cache: ${e.message}")
        }

        return selected
    }

    // -------------------------------------------------------------------------
    // Public API methods — all backed by executeWithRetry
    // -------------------------------------------------------------------------

    suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event> {
        val sourceText = fragments.joinToString(" ") { it.text }
        val sourceYears = extractSourceYears(sourceText)

        val combinedJson = buildJsonArray {
            fragments.forEach { fragment ->
                add(Json.parseToJsonElement(fragment.toJson()))
            }
        }.toString()
        val events = generateCalendarEventsFromPrompt(AiPrompts.getSourceEventExtractionPrompt(combinedJson))
        val filtered = filterToSourceYears(events, sourceYears)
        val dropped = events.size - filtered.size
        if (dropped > 0) logger?.d(tag, "⚠️ Dropped $dropped confabulated event(s) outside source years $sourceYears")
        return filtered
    }

    suspend fun generateCalendarEventsFromPrompt(prompt: String): List<Event> {
        return executeWithRetry(
            maxAttempts = 5,
            tier = TaskTier.HEAVY,   // parsing a full syllabus — needs best reasoning
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
            tier = TaskTier.LIGHT,   // short structured prompt — lite model is sufficient
            body = { _ -> buildGeminiBody(prompt) },
            parseResponse = { responseText ->
                val cleanJson = responseText.trim()
                    .removePrefix("```json")
                    .removeSuffix("```")
                    .trim()

                val root = json.parseToJsonElement(cleanJson)
                val jsonArray = when {
                    root is JsonArray -> root
                    root is JsonObject && root.containsKey("tasks") -> root["tasks"]!!.jsonArray
                    else -> throw Exception("Unexpected JSON structure: $cleanJson")
                }

                jsonArray.map { element ->
                    val obj = element.jsonObject
                    val daysBeforeDue = obj["daysBeforeDue"]?.jsonPrimitive?.let {
                        it.intOrNull ?: it.content.toDoubleOrNull()?.toInt() ?: 1
                    } ?: 1
                    DecomposedTask(
                        title = obj["title"]?.jsonPrimitive?.content ?: "Sub-task",
                        daysBeforeDue = daysBeforeDue,
                        description = obj["description"]?.jsonPrimitive?.content ?: ""
                    )
                }
            }
        )
    }

    suspend fun generateChatResponse(prompt: String): String {
        return try {
            executeWithRetry(
                maxAttempts = 3,
                tier = TaskTier.LIGHT,   // conversational — fast response matters most
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
                tier = TaskTier.HEAVY,   // document can be large; reasoning quality matters
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
                tier = TaskTier.LIGHT,   // simple classification — lite model is ideal
                body = { _ -> buildGeminiBody(prompt) },
                parseResponse = { responseText ->
                    val cleanJson = responseText.trim()
                        .removePrefix("```json")
                        .removeSuffix("```")
                        .trim()

                    val root = json.parseToJsonElement(cleanJson)
                    val categoryName = root.jsonObject["category"]?.jsonPrimitive?.content?.uppercase() ?: "OTHER"

                    when (categoryName) {
                        "SYLLABUS" -> SourceCategory.SYLLABUS
                        "READING MATERIAL", "READING_MATERIAL" -> SourceCategory.READING_MATERIAL
                        "LAB MANUAL", "LAB_MANUAL" -> SourceCategory.LAB_MANUAL
                        "LECTURE NOTES", "LECTURE_NOTES" -> SourceCategory.LECTURE_NOTES
                        else -> SourceCategory.OTHER
                    }
                }
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

@Serializable
data class ModelListResponse(val models: List<ModelInfo>)

@Serializable
data class ModelInfo(val name: String, val supportedGenerationMethods: List<String>)
