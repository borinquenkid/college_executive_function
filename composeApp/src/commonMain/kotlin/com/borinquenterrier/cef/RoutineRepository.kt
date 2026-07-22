package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class RoutineRepository(private val settings: Settings) {

    private val routineKey = "ROUTINE_EVENTS_LIST"

    suspend fun getRoutineEvents(): List<TimeEvent> = withContext(Dispatchers.Default) {
        val jsonString = settings.getString(routineKey, "[]")
        try {
            Json.decodeFromString(jsonString)
        } catch (e: Exception) {
            println("[RoutineRepository] Failed to decode stored routine events, returning empty list: $e")
            emptyList()
        }
    }

    suspend fun saveRoutineEvents(events: List<TimeEvent>) = withContext(Dispatchers.Default) {
        val jsonString = Json.encodeToString(events)
        settings.putString(routineKey, jsonString)
    }
}
