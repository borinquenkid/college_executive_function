package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoutineRepository(private val settings: Settings) {

    private val routineKey = "ROUTINE_ITEMS_LIST"

    fun getRoutine(): List<RoutineItem> {
        val jsonString = settings.getString(routineKey, "[]")
        return Json.decodeFromString(jsonString)
    }

    fun saveRoutine(items: List<RoutineItem>) {
        val jsonString = Json.encodeToString(items)
        settings.putString(routineKey, jsonString)
    }
}
