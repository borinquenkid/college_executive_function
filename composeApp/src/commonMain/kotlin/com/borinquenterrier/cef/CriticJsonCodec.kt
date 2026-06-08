package com.borinquenterrier.cef

import kotlinx.serialization.json.*

object CriticJsonCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
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
            val obj = element.jsonObject
            val title = obj["title"]?.jsonPrimitive?.content ?: "Untitled Event"
            val type = obj["type"]?.jsonPrimitive?.content ?: "DAY"
            val dateStr = obj["date"]?.jsonPrimitive?.content ?: "2024-01-01"
            val categoryStr = obj["category"]?.jsonPrimitive?.content ?: "REGULAR"
            val warning = obj["warning"]?.jsonPrimitive?.content
            val category = try {
                AcademicCategory.valueOf(categoryStr)
            } catch (e: Exception) {
                AcademicCategory.REGULAR
            }
            val date = try { kotlinx.datetime.LocalDate.parse(dateStr) }
                       catch (e: Exception) { kotlinx.datetime.LocalDate(2024, 1, 1) }

            if (type == "TIME") {
                val startTimeStr = obj["startTime"]?.jsonPrimitive?.content ?: "09:00"
                val endTimeStr = obj["endTime"]?.jsonPrimitive?.content ?: "10:00"
                val start = try { kotlinx.datetime.LocalTime.parse(startTimeStr) }
                            catch (e: Exception) { kotlinx.datetime.LocalTime(9, 0) }
                val end = try { kotlinx.datetime.LocalTime.parse(endTimeStr) }
                          catch (e: Exception) { kotlinx.datetime.LocalTime(10, 0) }
                TimeEvent(
                    title = title, source = EventSource.AI_GENERATED, category = category,
                    date = date, startTime = start, endTime = end, warning = warning
                )
            } else {
                DayEvent(
                    title = title, source = EventSource.AI_GENERATED,
                    category = category, date = date, warning = warning
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
            val obj = element.jsonObject
            val daysBeforeDue = obj["daysBeforeDue"]?.jsonPrimitive?.let {
                it.intOrNull ?: it.content.toDoubleOrNull()?.toInt() ?: 1
            } ?: 1
            DecomposedTask(
                title = obj["title"]?.jsonPrimitive?.content ?: "Sub-task",
                daysBeforeDue = daysBeforeDue,
                description = obj["description"]?.jsonPrimitive?.content ?: ""
            )
        } catch (e: Exception) {
            logger?.e("CriticActor", "Failed to parse task element: ${e.message}")
            null
        }
    }
}
