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
    private val database: AppDatabase? = null
) {
    private val tag = "GeminiAI"
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { 
                ignoreUnknownKeys = true 
                coerceInputValues = true
            })
        }
    }

    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }

    companion object {
        // Global blacklist to persist across service recreations during a session
        private val blacklistedModels = mutableMapOf<String, Long>()
        private const val BLACKLIST_DURATION_MS = 60 * 60 * 1000L // 1 hour
        private const val PREFERRED_MODEL_KEY = "preferred_gemini_model"

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

    private suspend fun negotiateBestModel(available: List<ModelInfo>): String {
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

        val preferences = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.5-pro",
            "gemini-2.0-flash",
            "gemini-2.0-flash-001",
            "gemini-2.0-flash-lite",
            "gemini-2.0-flash-lite-001",
            "gemini-1.5-flash",
            "gemini-1.5-flash-latest",
            "gemini-1.5-pro",
            "gemini-flash-latest",
            "gemini-flash-lite-latest",
            "gemini-pro-latest",
            "gemini-pro"
        )

        // Find best match among text-capable models
        val selected = preferences.firstOrNull { pref -> textCapableNames.contains(pref) }
            ?: textCapableNames.firstOrNull { it.contains("flash") && !it.contains("tts") }
            ?: textCapableNames.firstOrNull()
            ?: "gemini-2.5-flash"

        // 3. Save to Cache
        try {
            database?.appDatabaseQueries?.insertModel(PREFERRED_MODEL_KEY, selected, currentTime)
            logger?.d(tag, "Saved newly negotiated model to database: $selected")
        } catch (e: Exception) {
            logger?.e(tag, "Failed to save model to cache: ${e.message}")
        }

        return selected
    }

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
        val available = getAvailableModels()
        var attempts = 0
        val maxAttempts = 5
        var lastError: Exception? = null

        while (attempts < maxAttempts) {
            val modelName = negotiateBestModel(available)
            
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
            val authUrl = if (apiKey != null) "$url?key=$apiKey" else url

            try {
                val httpResponse: HttpResponse = client.post(authUrl) {
                    contentType(ContentType.Application.Json)
                    if (apiKey == null && accessToken != null) {
                        header("Authorization", "Bearer $accessToken")
                    }
                    setBody(buildJsonObject {
                        putJsonArray("contents") {
                            addJsonObject {
                                putJsonArray("parts") {
                                    addJsonObject { put("text", prompt) }
                                }
                            }
                        }
                        // Enable native JSON output mode for Gemini 1.5+
                        putJsonObject("generationConfig") {
                            put("responseMimeType", "application/json")
                            put("temperature", 0.0)
                        }
                    })
                }

                val responseBody = httpResponse.bodyAsText()
                
                if (httpResponse.status == HttpStatusCode.Unauthorized) {
                    logger?.e(tag, "401 Unauthorized: Your API Key or Access Token is invalid/expired.")
                    throw Exception("Unauthorized")
                }

                if (httpResponse.status == HttpStatusCode.Forbidden) {
                    logger?.e(tag, "403 Forbidden: Ensure the Gemini API is enabled in your Google Cloud Project.")
                    throw Exception("Forbidden")
                }

                if (httpResponse.status == HttpStatusCode.TooManyRequests ||
                    httpResponse.status == HttpStatusCode.NotFound ||
                    httpResponse.status == HttpStatusCode.ServiceUnavailable) {

                    val expiry = Clock.System.now().toEpochMilliseconds() + BLACKLIST_DURATION_MS
                    blacklistedModels[modelName] = expiry
                    database?.appDatabaseQueries?.deleteModel(PREFERRED_MODEL_KEY)

                    val delayMs = if (httpResponse.status == HttpStatusCode.TooManyRequests) 10000L else 2000L
                    logger?.d(tag, "⚠️ Model $modelName exhausted (${httpResponse.status}). Blacklisted. Retrying (${attempts + 1}/$maxAttempts) after ${delayMs/1000}s...")
                    attempts++
                    kotlinx.coroutines.delay(delayMs * attempts)
                    continue
                }

                // 400 with a modality error means this model doesn't support text/JSON output (e.g. TTS-only models).
                // Blacklist it and try the next best model rather than failing hard.
                if (httpResponse.status == HttpStatusCode.BadRequest && responseBody.contains("response modalities")) {
                    val expiry = Clock.System.now().toEpochMilliseconds() + BLACKLIST_DURATION_MS
                    blacklistedModels[modelName] = expiry
                    database?.appDatabaseQueries?.deleteModel(PREFERRED_MODEL_KEY)
                    logger?.d(tag, "⚠️ Model $modelName does not support text responses. Blacklisted. Retrying (${attempts + 1}/$maxAttempts)...")
                    attempts++
                    kotlinx.coroutines.delay(1000)
                    continue
                }

                if (!httpResponse.status.isSuccess()) {
                    throw Exception("Gemini API Error (${httpResponse.status}): $responseBody")
                }

                val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)
                val responseText = geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("Empty response from AI")

                // With responseMimeType: "application/json", responseText is already raw JSON.
                // However, some versions still wrap it in markdown. Let's be safe.
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
                    val category = try {
                        AcademicCategory.valueOf(categoryStr)
                    } catch (e: Exception) {
                        AcademicCategory.REGULAR
                    }

                    val date = try { LocalDate.parse(dateStr) } catch (e: Exception) { LocalDate(2024,1,1) }

                    if (type == "TIME") {
                        val startTimeStr = obj["startTime"]?.jsonPrimitive?.content ?: "09:00"
                        val endTimeStr = obj["endTime"]?.jsonPrimitive?.content ?: "10:00"
                        val start = try { LocalTime.parse(startTimeStr) } catch (e: Exception) { LocalTime(9,0) }
                        val end = try { LocalTime.parse(endTimeStr) } catch (e: Exception) { LocalTime(10,0) }
                        
                        TimeEvent(
                            title = title,
                            source = EventSource.AI_GENERATED,
                            category = category,
                            date = date,
                            startTime = start,
                            endTime = end,
                            warning = warning
                        )
                    } else {
                        DayEvent(
                            title = title,
                            source = EventSource.AI_GENERATED,
                            category = category,
                            date = date,
                            warning = warning
                        )
                    }
                }
            } catch (e: Exception) {
                lastError = e
                logger?.e(tag, "Attempt ${attempts + 1} failed: ${e.message}")
                attempts++
                kotlinx.coroutines.delay(1000)
            }
        }
        throw lastError ?: Exception("Failed to generate events after $maxAttempts attempts")
    }

    suspend fun generateStudyPlan(syllabusText: String, existingSchedule: String = ""): List<Event> {
        return generateCalendarEventsFromPrompt(AiPrompts.getSyllabusStudyPlanPrompt(syllabusText, existingSchedule))
    }

    suspend fun decomposeTask(taskTitle: String, dueDate: String): List<DecomposedTask> {
        val available = getAvailableModels()
        var attempts = 0
        val maxAttempts = 5
        var lastError: Exception? = null
        val prompt = AiPrompts.getTaskDecompositionPrompt(taskTitle, dueDate)

        while (attempts < maxAttempts) {
            val modelName = negotiateBestModel(available)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
            val authUrl = if (apiKey != null) "$url?key=$apiKey" else url

            try {
                val httpResponse: HttpResponse = client.post(authUrl) {
                    contentType(ContentType.Application.Json)
                    if (apiKey == null && accessToken != null) {
                        header("Authorization", "Bearer $accessToken")
                    }
                    setBody(buildJsonObject {
                        putJsonArray("contents") {
                            addJsonObject {
                                putJsonArray("parts") {
                                    addJsonObject { put("text", prompt) }
                                }
                            }
                        }
                        putJsonObject("generationConfig") {
                            put("responseMimeType", "application/json")
                            put("temperature", 0.0)
                        }
                    })
                }

                val responseBody = httpResponse.bodyAsText()

                if (httpResponse.status == HttpStatusCode.Unauthorized) throw Exception("Unauthorized")
                if (httpResponse.status == HttpStatusCode.Forbidden) throw Exception("Forbidden")

                if (httpResponse.status == HttpStatusCode.TooManyRequests ||
                    httpResponse.status == HttpStatusCode.NotFound ||
                    httpResponse.status == HttpStatusCode.ServiceUnavailable) {
                    val expiry = Clock.System.now().toEpochMilliseconds() + BLACKLIST_DURATION_MS
                    blacklistedModels[modelName] = expiry
                    database?.appDatabaseQueries?.deleteModel(PREFERRED_MODEL_KEY)
                    val delayMs = if (httpResponse.status == HttpStatusCode.TooManyRequests) 10000L else 2000L
                    logger?.d(tag, "Model $modelName exhausted. Retrying (${attempts + 1}/$maxAttempts)...")
                    attempts++
                    kotlinx.coroutines.delay(delayMs * attempts)
                    continue
                }

                if (httpResponse.status == HttpStatusCode.BadRequest && responseBody.contains("response modalities")) {
                    val expiry = Clock.System.now().toEpochMilliseconds() + BLACKLIST_DURATION_MS
                    blacklistedModels[modelName] = expiry
                    database?.appDatabaseQueries?.deleteModel(PREFERRED_MODEL_KEY)
                    logger?.d(tag, "⚠️ Model $modelName does not support text responses. Blacklisted. Retrying (${attempts + 1}/$maxAttempts)...")
                    attempts++
                    kotlinx.coroutines.delay(1000)
                    continue
                }

                if (!httpResponse.status.isSuccess()) {
                    throw Exception("Gemini API Error (${httpResponse.status}): $responseBody")
                }

                val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)
                val responseText = geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("Empty response from AI")

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

                return jsonArray.map { element ->
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
            } catch (e: Exception) {
                lastError = e
                logger?.e(tag, "Attempt ${attempts + 1} failed: ${e.message}")
                attempts++
                kotlinx.coroutines.delay(1000)
            }
        }
        throw lastError ?: Exception("Failed to decompose task after $maxAttempts attempts")
    }

    suspend fun generateChatResponse(prompt: String): String {
        val available = getAvailableModels()
        val modelName = negotiateBestModel(available)
        
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
        val authUrl = if (apiKey != null) "$url?key=$apiKey" else url

        return try {
            val httpResponse: HttpResponse = client.post(authUrl) {
                contentType(ContentType.Application.Json)
                if (apiKey == null && accessToken != null) {
                    header("Authorization", "Bearer $accessToken")
                }
                setBody(buildJsonObject {
                    putJsonArray("contents") {
                        addJsonObject {
                            putJsonArray("parts") {
                                addJsonObject { put("text", prompt) }
                            }
                        }
                    }
                })
            }

            if (!httpResponse.status.isSuccess()) {
                throw Exception("Gemini API Error: ${httpResponse.status}")
            }

            val geminiResponse = json.decodeFromString<GeminiResponse>(httpResponse.bodyAsText())
            geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No response from AI."
        } catch (e: Exception) {
            logger?.e(tag, "Failed to generate chat response: ${e.message}")
            "Error: ${e.message}"
        }
    }

    suspend fun analyzeDocument(text: String): String? {
        val available = getAvailableModels()
        val modelName = negotiateBestModel(available)
        
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
        val authUrl = if (apiKey != null) "$url?key=$apiKey" else url

        return try {
            val httpResponse: HttpResponse = client.post(authUrl) {
                contentType(ContentType.Application.Json)
                if (apiKey == null && accessToken != null) {
                    header("Authorization", "Bearer $accessToken")
                }
                setBody(buildJsonObject {
                    putJsonArray("contents") {
                        addJsonObject {
                            putJsonArray("parts") {
                                addJsonObject { put("text", AiPrompts.getDocumentIntelligencePrompt(text)) }
                            }
                        }
                    }
                    putJsonObject("generationConfig") {
                        put("responseMimeType", "application/json")
                    }
                })
            }

            if (!httpResponse.status.isSuccess()) {
                throw Exception("Gemini API Error: ${httpResponse.status}")
            }

            val geminiResponse = json.decodeFromString<GeminiResponse>(httpResponse.bodyAsText())
            geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            logger?.e(tag, "Failed to analyze document: ${e.message}")
            null
        }
    }

    suspend fun categorizeSource(text: String): SourceCategory {
        val textSample = if (text.length > 50000) text.substring(0, 50000) else text
        val prompt = AiPrompts.getSourceCategorizationPrompt(textSample)
        val available = getAvailableModels()
        var attempts = 0
        val maxAttempts = 3
        var lastError: Exception? = null

        while (attempts < maxAttempts) {
            val modelName = negotiateBestModel(available)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
            val authUrl = if (apiKey != null) "$url?key=$apiKey" else url

            try {
                val httpResponse: HttpResponse = client.post(authUrl) {
                    contentType(ContentType.Application.Json)
                    if (apiKey == null && accessToken != null) {
                        header("Authorization", "Bearer $accessToken")
                    }
                    setBody(buildJsonObject {
                        putJsonArray("contents") {
                            addJsonObject {
                                putJsonArray("parts") {
                                    addJsonObject { put("text", prompt) }
                                }
                            }
                        }
                        putJsonObject("generationConfig") {
                            put("responseMimeType", "application/json")
                            put("temperature", 0.0)
                        }
                    })
                }

                val responseBody = httpResponse.bodyAsText()

                if (httpResponse.status == HttpStatusCode.Unauthorized) throw Exception("Unauthorized")
                if (httpResponse.status == HttpStatusCode.Forbidden) throw Exception("Forbidden")

                if (httpResponse.status == HttpStatusCode.TooManyRequests ||
                    httpResponse.status == HttpStatusCode.NotFound ||
                    httpResponse.status == HttpStatusCode.ServiceUnavailable) {
                    val expiry = Clock.System.now().toEpochMilliseconds() + BLACKLIST_DURATION_MS
                    blacklistedModels[modelName] = expiry
                    database?.appDatabaseQueries?.deleteModel(PREFERRED_MODEL_KEY)
                    val delayMs = if (httpResponse.status == HttpStatusCode.TooManyRequests) 10000L else 2000L
                    logger?.d(tag, "Model $modelName exhausted. Retrying (${attempts + 1}/$maxAttempts)...")
                    attempts++
                    kotlinx.coroutines.delay(delayMs * attempts)
                    continue
                }

                if (httpResponse.status == HttpStatusCode.BadRequest && responseBody.contains("response modalities")) {
                    val expiry = Clock.System.now().toEpochMilliseconds() + BLACKLIST_DURATION_MS
                    blacklistedModels[modelName] = expiry
                    database?.appDatabaseQueries?.deleteModel(PREFERRED_MODEL_KEY)
                    attempts++
                    kotlinx.coroutines.delay(1000)
                    continue
                }

                if (!httpResponse.status.isSuccess()) {
                    throw Exception("Gemini API Error (${httpResponse.status}): $responseBody")
                }

                val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)
                val responseText = geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("Empty response from AI")

                val cleanJson = responseText.trim()
                    .removePrefix("```json")
                    .removeSuffix("```")
                    .trim()

                val root = json.parseToJsonElement(cleanJson)
                val categoryName = root.jsonObject["category"]?.jsonPrimitive?.content?.uppercase() ?: "OTHER"
                
                return when (categoryName) {
                    "SYLLABUS" -> SourceCategory.SYLLABUS
                    "READING MATERIAL", "READING_MATERIAL" -> SourceCategory.READING_MATERIAL
                    "LAB MANUAL", "LAB_MANUAL" -> SourceCategory.LAB_MANUAL
                    "LECTURE NOTES", "LECTURE_NOTES" -> SourceCategory.LECTURE_NOTES
                    else -> SourceCategory.OTHER
                }
            } catch (e: Exception) {
                lastError = e
                logger?.e(tag, "Categorization attempt ${attempts + 1} failed: ${e.message}")
                attempts++
                kotlinx.coroutines.delay(1000)
            }
        }
        logger?.e(tag, "Failed to categorize source after $maxAttempts attempts, defaulting to OTHER. Error: ${lastError?.message}")
        return SourceCategory.OTHER
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
