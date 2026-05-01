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
        
        // Filter out models that are still blacklisted
        val nonBlacklistedNames = names.filter { name ->
            val expiry = blacklistedModels[name]
            expiry == null || currentTime > expiry
        }

        logger?.d(tag, "Negotiating best model. Available: ${names.size}, Non-blacklisted: ${nonBlacklistedNames.size}")
        
        val preferences = listOf(
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-2.0-flash",
            "gemini-2-flash",
            "gemini-2-flash-lite",
            "gemini-2.0-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-3-flash",
            "gemini-pro"
        )

        // Find best match among non-blacklisted models
        val selected = preferences.firstOrNull { pref -> nonBlacklistedNames.contains(pref) }
            ?: nonBlacklistedNames.firstOrNull { it.contains("flash") }
            ?: nonBlacklistedNames.firstOrNull()
            ?: "gemini-1.5-flash"

        // 3. Save to Cache
        try {
            database?.appDatabaseQueries?.insertModel(PREFERRED_MODEL_KEY, selected, currentTime)
            logger?.d(tag, "Saved newly negotiated model to database: $selected")
        } catch (e: Exception) {
            logger?.e(tag, "Failed to save model to cache: ${e.message}")
        }

        return selected
    }

    suspend fun generateCalendarEvents(chunks: List<SourceChunk>): List<Event> {
        val combinedJson = buildJsonArray {
            chunks.forEach { chunk ->
                add(Json.parseToJsonElement(chunk.toJson()))
            }
        }.toString()
        return generateCalendarEventsFromPrompt(AiPrompts.getChunkEventExtractionPrompt(combinedJson))
    }

    suspend fun generateCalendarEventsFromPrompt(prompt: String): List<Event> {
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
                        // Enable native JSON output mode for Gemini 1.5+
                        putJsonObject("generationConfig") {
                            put("responseMimeType", "application/json")
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
                    
                    logger?.d(tag, "⚠️ Model $modelName exhausted (${httpResponse.status}). Retrying...")
                    attempts++
                    kotlinx.coroutines.delay(2000L * attempts)
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
                    val category = try {
                        AcademicCategory.valueOf(categoryStr)
                    } catch (e: Exception) {
                        AcademicCategory.REGULAR
                    }

                    if (type == "TIME") {
                        val startTime = obj["startTime"]?.jsonPrimitive?.content ?: "09:00"
                        val endTime = obj["endTime"]?.jsonPrimitive?.content ?: "10:00"
                        TimeEvent(
                            title = title,
                            source = EventSource.AI_GENERATED,
                            category = category,
                            date = LocalDate.parse(dateStr),
                            startTime = LocalTime.parse(startTime),
                            endTime = LocalTime.parse(endTime)
                        )
                    } else {
                        DayEvent(
                            title = title,
                            source = EventSource.AI_GENERATED,
                            category = category,
                            date = LocalDate.parse(dateStr)
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
}

@Serializable
data class GeminiResponse(val candidates: List<Candidate>)

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
