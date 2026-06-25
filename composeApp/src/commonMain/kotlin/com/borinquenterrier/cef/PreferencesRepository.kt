package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class PreferencesRepository(private val settings: Settings) : PreferencesPort {

    private val preferencesKey = "STUDY_PREFERENCES"

    override suspend fun getPreferences(): StudyPreferences = withContext(Dispatchers.Default) {
        val jsonString = settings.getString(preferencesKey, "")
        if (jsonString.isBlank()) {
            return@withContext StudyPreferences()
        }
        try {
            Json.decodeFromString(jsonString)
        } catch (e: Exception) {
            StudyPreferences()
        }
    }

    override suspend fun savePreferences(preferences: StudyPreferences) = withContext(Dispatchers.Default) {
        val jsonString = Json.encodeToString(preferences)
        settings.putString(preferencesKey, jsonString)
    }
}
