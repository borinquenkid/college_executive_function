package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PreferencesRepository(private val settings: Settings) {

    private val preferencesKey = "STUDY_PREFERENCES"

    fun getPreferences(): StudyPreferences {
        val jsonString = settings.getString(preferencesKey, "")
        if (jsonString.isBlank()) {
            return StudyPreferences()
        }
        return try {
            Json.decodeFromString(jsonString)
        } catch (e: Exception) {
            StudyPreferences()
        }
    }

    fun savePreferences(preferences: StudyPreferences) {
        val jsonString = Json.encodeToString(preferences)
        settings.putString(preferencesKey, jsonString)
    }
}
