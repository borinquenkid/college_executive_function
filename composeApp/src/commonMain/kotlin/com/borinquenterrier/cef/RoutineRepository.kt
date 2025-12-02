package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoutineRepository(private val settings: Settings) {

    private val routineKey = "ROUTINE_EVENTS_LIST"

    fun getRoutineEvents(): List<TimeEvent> {
        val jsonString = settings.getString(routineKey, "[]")
        return Json.decodeFromString(jsonString)
    }

    fun saveRoutineEvents(events: List<TimeEvent>) {
        val jsonString = Json.encodeToString(events)
        settings.putString(routineKey, jsonString)
    }
}
