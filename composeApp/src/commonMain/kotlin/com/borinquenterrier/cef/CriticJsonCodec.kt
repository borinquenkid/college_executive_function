package com.borinquenterrier.cef

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
private data class RawCriticEvent(
    val title: String = "Untitled Event",
    val type: String = "DAY",
    val date: String = "2024-01-01",
    val category: String = "REGULAR",
    val warning: String? = null,
    val startTime: String = "09:00",
    val endTime: String = "10:00"
)

@Serializable
private data class RawCriticTask(
    val title: String = "Sub-task",
    val daysBeforeDue: Double = 1.0,
    val description: String = ""
)

object CriticJsonCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        prettyPrint = false
    }

    fun serializeEvents(events: List<Event>): String {
        return buildJsonArray {
            events.forEach { event ->
                addJsonObject {
                    put("title", event.title)
                    put("type", if (event is TimeEvent) "TIME" else "DAY")
                    put("category", event.category.name)
                    put("date", event.date.toString())
                    if (event is TimeEvent) {
                        put("startTime", event.startTime.toString())
                        put("endTime", event.endTime.toString())
                    }
                    if (event.warning != null) {
                        put("warning", event.warning)
                    }
                }
            }
        }.toString()
    }

    fun parseEvents(jsonText: String, logger: Logger? = null): List<Event> {
        val cleanJson = jsonText.trim()
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()
        val root = json.parseToJsonElement(cleanJson)
        val jsonArray = if (root is JsonArray) {
            root
        } else if (root is JsonObject && root.containsKey("events")) {
            root["events"]!!.jsonArray
        } else {
            throw Exception("Unexpected JSON structure: $cleanJson")
        }
        return jsonArray.mapNotNull { parseEventFromJson(it, logger) }
    }

    private fun parseEventFromJson(element: JsonElement, logger: Logger?): Event? {
        return try {
            val raw = json.decodeFromJsonElement(RawCriticEvent.serializer(), element)
            val category = try { AcademicCategory.valueOf(raw.category) }
                           catch (e: Exception) { AcademicCategory.REGULAR }
            val date = try { kotlinx.datetime.LocalDate.parse(raw.date) }
                       catch (e: Exception) { kotlinx.datetime.LocalDate(2024, 1, 1) }

            if (raw.type == "TIME") {
                val start = try { kotlinx.datetime.LocalTime.parse(raw.startTime) }
                            catch (e: Exception) { kotlinx.datetime.LocalTime(9, 0) }
                val end = try { kotlinx.datetime.LocalTime.parse(raw.endTime) }
                          catch (e: Exception) { kotlinx.datetime.LocalTime(10, 0) }
                TimeEvent(
                    title = raw.title, source = EventSource.AI_GENERATED, category = category,
                    date = date, startTime = start, endTime = end, warning = raw.warning
                )
            } else {
                DayEvent(
                    title = raw.title, source = EventSource.AI_GENERATED,
                    category = category, date = date, warning = raw.warning
                )
            }
        } catch (e: Exception) {
            logger?.e("CriticActor", "Failed to parse event element: ${e.message}")
            null
        }
    }

    fun serializeTasks(tasks: List<DecomposedTask>): String {
        return buildJsonArray {
            tasks.forEach { task ->
                addJsonObject {
                    put("title", task.title)
                    put("daysBeforeDue", task.daysBeforeDue)
                    put("description", task.description)
                }
            }
        }.toString()
    }

    fun parseTasks(jsonText: String, logger: Logger? = null): List<DecomposedTask> {
        val cleanJson = jsonText.trim()
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()
        val root = json.parseToJsonElement(cleanJson)
        val jsonArray = when {
            root is JsonArray -> root
            root is JsonObject && root.containsKey("tasks") -> root["tasks"]!!.jsonArray
            else -> throw Exception("Unexpected JSON structure: $cleanJson")
        }
        return jsonArray.mapNotNull { parseTaskFromJson(it, logger) }
    }

    private fun parseTaskFromJson(element: JsonElement, logger: Logger?): DecomposedTask? {
        return try {
            val raw = json.decodeFromJsonElement(RawCriticTask.serializer(), element)
            DecomposedTask(
                title = raw.title,
                daysBeforeDue = raw.daysBeforeDue.toInt(),
                description = raw.description
            )
        } catch (e: Exception) {
            logger?.e("CriticActor", "Failed to parse task element: ${e.message}")
            null
        }
    }
}
