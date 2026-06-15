package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class RoutineRepository(
    private val settings: Settings,
    private val logger: Logger? = null
) {

    private val routineKey = "ROUTINE_EVENTS_LIST"

    suspend fun getRoutineEvents(): List<TimeEvent> = withContext(Dispatchers.Default) {
        val jsonString = settings.getString(routineKey, "[]")
        try {
            Json.decodeFromString(jsonString)
        } catch (e: Exception) {
            logger?.e("RoutineRepository", "Failed to decode routine events", e)
            emptyList()
        }
    }

    suspend fun saveRoutineEvents(events: List<TimeEvent>) = withContext(Dispatchers.Default) {
        settings.putString(routineKey, Json.encodeToString(events))
    }
}
