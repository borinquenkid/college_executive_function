package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class GeminiModelNegotiator(
    private val apiKey: String?,
    private val accessToken: String?,
    private val client: HttpClient,
    private val database: AppDatabase?,
    private val logger: Logger?
) {
    private val tag = "GeminiModelNegotiator"

    companion object {
        private val blacklistedModels = mutableMapOf<String, Long>()
        private const val BLACKLIST_DURATION_MS = 60 * 60 * 1000L // 1 hour
        private const val PREFERRED_MODEL_KEY = "preferred_gemini_model"

        internal fun clearBlacklistForTesting() = blacklistedModels.clear()

        internal val HEAVY_PREFERENCES = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash-lite"
        )

        internal val LIGHT_PREFERENCES = listOf(
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash-lite",
            "gemini-2.0-flash",
            "gemini-2.5-flash"
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun blacklistModel(modelName: String) {
        val expiry = Clock.System.now().toEpochMilliseconds() + BLACKLIST_DURATION_MS
        blacklistedModels[modelName] = expiry
    }

    fun evictFromCache(modelName: String) {
        try {
            database?.appDatabaseQueries?.deleteModel(PREFERRED_MODEL_KEY)
        } catch (e: Exception) {
            logger?.e(tag, "Failed to evict model $modelName from cache: ${e.message}", e)
        }
    }

    suspend fun getAvailableModels(): List<ModelInfo> {
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
            logger?.e(tag, "Exception fetching models: ${e.message}", e)
            emptyList()
        }
    }

    fun negotiateBestModel(
        available: List<ModelInfo>,
        tier: GeminiAIService.TaskTier = GeminiAIService.TaskTier.HEAVY
    ): String {
        val currentTime = Clock.System.now().toEpochMilliseconds()

        val cachedModel = database?.appDatabaseQueries?.getSelectedModel(PREFERRED_MODEL_KEY)
            ?.executeAsOneOrNull()
        if (cachedModel != null) {
            val expiry = blacklistedModels[cachedModel]
            if (expiry == null || currentTime > expiry) {
                logger?.d(tag, "Using cached model from database: $cachedModel")
                return cachedModel
            } else {
                logger?.d(
                    tag,
                    "Cached model $cachedModel is currently blacklisted. Re-negotiating..."
                )
            }
        }

        val generationCapable =
            available.filter { it.supportedGenerationMethods.contains("generateContent") }
        val names = generationCapable.map { it.name.removePrefix("models/") }

        logger?.d(tag, "Negotiation Step - Available names: ${names.joinToString(", ")}")

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

        logger?.d(
            tag,
            "Negotiating best model. Available: ${names.size}, Text-capable & non-blacklisted: ${textCapableNames.size}"
        )

        val preferences =
            if (tier == GeminiAIService.TaskTier.HEAVY) HEAVY_PREFERENCES else LIGHT_PREFERENCES

        logger?.d(tag, "Task tier: $tier — preference order: ${preferences.joinToString(", ")}")

        val selected = preferences.firstOrNull { pref -> textCapableNames.contains(pref) }
            ?: textCapableNames.firstOrNull { it.contains("flash") && !it.contains("tts") }
            ?: textCapableNames.firstOrNull()
            ?: "gemini-2.0-flash"

        try {
            database?.appDatabaseQueries?.insertModel(PREFERRED_MODEL_KEY, selected, currentTime)
            logger?.d(tag, "Saved newly negotiated model to database: $selected")
        } catch (e: Exception) {
            logger?.e(tag, "Failed to save model to cache: ${e.message}", e)
        }

        return selected
    }
}

@Serializable
data class ModelListResponse(val models: List<ModelInfo>)

@Serializable
data class ModelInfo(val name: String, val supportedGenerationMethods: List<String>)
