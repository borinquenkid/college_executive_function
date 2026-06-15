package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class PreferencesRepository(
    private val settings: Settings,
    private val logger: Logger? = null
) {

    private val preferencesKey = "STUDY_PREFERENCES"

    suspend fun getPreferences(): StudyPreferences = withContext(Dispatchers.Default) {
        val jsonString = settings.getString(preferencesKey, "")
        if (jsonString.isBlank()) {
            return@withContext StudyPreferences()
        }
        try {
            Json.decodeFromString(jsonString)
        } catch (e: Exception) {
            logger?.e("PreferencesRepository", "Failed to decode preferences", e)
            StudyPreferences()
        }
    }

    suspend fun savePreferences(preferences: StudyPreferences) = withContext(Dispatchers.Default) {
        val jsonString = Json.encodeToString(preferences)
        settings.putString(preferencesKey, jsonString)
    }
}
