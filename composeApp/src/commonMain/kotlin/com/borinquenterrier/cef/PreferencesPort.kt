package com.borinquenterrier.cef

/**
 * Domain-side contract for reading and writing study preferences.
 *
 * [PreferencesRepository] is the adapter that bridges this port to the
 * multiplatform-settings storage layer. [NoOp] is used as the default
 * everywhere preferences are optional — no silent null propagation.
 */
interface PreferencesPort {
    suspend fun getPreferences(): StudyPreferences
    suspend fun savePreferences(preferences: StudyPreferences)

    companion object {
        val NoOp: PreferencesPort = object : PreferencesPort {
            override suspend fun getPreferences() = StudyPreferences()
            override suspend fun savePreferences(preferences: StudyPreferences) {}
        }
    }
}
