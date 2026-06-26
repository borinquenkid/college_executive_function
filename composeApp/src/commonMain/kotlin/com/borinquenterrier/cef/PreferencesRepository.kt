package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class PreferencesRepository(private val settings: Settings) : PreferencesPort {

    private val preferencesKey = "STUDY_PREFERENCES"

    fun readSync(): StudyPreferences {
        val json = settings.getString(preferencesKey, "")
        return if (json.isBlank()) StudyPreferences()
        else try { Json.decodeFromString(json) } catch (e: Exception) { StudyPreferences() }
    }

    val flow: kotlinx.coroutines.flow.MutableStateFlow<StudyPreferences> by lazy {
        kotlinx.coroutines.flow.MutableStateFlow(readSync())
    }

    override suspend fun getPreferences(): StudyPreferences = withContext(Dispatchers.Default) {
        readSync()
    }

    override suspend fun savePreferences(preferences: StudyPreferences) = withContext(Dispatchers.Default) {
        val jsonString = Json.encodeToString(preferences)
        settings.putString(preferencesKey, jsonString)
        flow.value = preferences
    }
}
