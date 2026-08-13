package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

@Serializable
private data class RawCriticEvent(
    val title: String = "Untitled Event",
    val type: String = "DAY",
    val date: String = "2024-01-01",
    val category: String = "REGULAR",
    val warning: String? = null,
    // Blank (not "09:00") so an absent time is distinguishable from a real one — a TIME
    // event with no parseable start is downgraded to a date-only DayEvent rather than
    // silently given an invented clock time a student might trust.
    val startTime: String = "",
    val endTime: String = ""
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

    private fun parseCategory(raw: String, logger: Logger?): AcademicCategory = try {
        AcademicCategory.valueOf(raw)
    } catch (e: Exception) {
        logger?.e("CriticActor", "Failed to parse category '$raw', defaulting to REGULAR: ${e.message}")
        AcademicCategory.REGULAR
    }

    private fun parseEventDate(raw: String, logger: Logger?): LocalDate = try {
        LocalDate.parse(raw)
    } catch (e: Exception) {
        logger?.e("CriticActor", "Failed to parse date '$raw', defaulting to 2024-01-01: ${e.message}")
        LocalDate(2024, 1, 1)
    }

    private fun parseStartTime(raw: RawCriticEvent, logger: Logger?): LocalTime? {
        if (raw.type != "TIME") return null
        return try {
            LocalTime.parse(raw.startTime)
        } catch (e: Exception) {
            logger?.e("CriticActor", "Unparseable startTime '${raw.startTime}' — downgrading TIME event to date-only: ${e.message}")
            null
        }
    }

    // A missing end is low-risk once the start is real — assume an hour rather than dropping
    // the time the student actually needs to show up at.
    private fun resolveEndTime(raw: RawCriticEvent, start: LocalTime, logger: Logger?): LocalTime {
        val rawEnd = try {
            LocalTime.parse(raw.endTime)
        } catch (e: Exception) {
            logger?.e("CriticActor", "Unparseable endTime '${raw.endTime}', assuming 1h duration: ${e.message}")
            null
        }
        if (rawEnd != null && rawEnd > start) return rawEnd

        val plusHourMins = start.hour * 60 + start.minute + 60
        return if (plusHourMins < 24 * 60) LocalTime(plusHourMins / 60, plusHourMins % 60)
        else LocalTime(23, 59, 59)
    }

    private fun parseEventFromJson(element: JsonElement, logger: Logger?): Event? {
        return try {
            val raw = json.decodeFromJsonElement(RawCriticEvent.serializer(), element)
            val category = parseCategory(raw.category, logger)
            val date = parseEventDate(raw.date, logger)
            val start = parseStartTime(raw, logger)

            if (start != null) {
                TimeEvent(
                    title = raw.title, source = EventSource.AI_GENERATED, category = category,
                    date = date, startTime = start, endTime = resolveEndTime(raw, start, logger),
                    warning = raw.warning
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
