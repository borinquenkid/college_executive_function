package com.borinquenterrier.cef

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

object GeminiResponseParser {
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }

    val YEAR_PATTERN = Regex("""\b(20\d{2})\b""")

    fun extractSourceYears(sourceText: String): Set<Int> =
        YEAR_PATTERN.findAll(sourceText).map { it.value.toInt() }.toSet()

    fun filterToSourceYears(events: List<Event>, sourceYears: Set<Int>): List<Event> {
        if (sourceYears.isEmpty()) return events
        return events.filter { event ->
            val year = when (event) {
                is TimeEvent -> event.date.year
                is DayEvent -> event.date.year
            }
            year in sourceYears
        }
    }

    fun parseEventsJson(responseText: String, telemetry: TelemetryManager? = null): List<Event> {
        val cleanJson = responseText.trim()
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

        return jsonArray.map { element ->
            val obj = element.jsonObject
            val title = obj["title"]?.jsonPrimitive?.content ?: "Untitled Event"
            val type = obj["type"]?.jsonPrimitive?.content ?: "DAY"
            val dateStr = obj["date"]?.jsonPrimitive?.content ?: "2024-01-01"
            val categoryStr = obj["category"]?.jsonPrimitive?.content ?: "REGULAR"
            val warning = obj["warning"]?.jsonPrimitive?.content
            val gradeWeight = obj["gradeWeight"]?.jsonPrimitive?.let { prim ->
                prim.doubleOrNull?.toFloat() ?: prim.content.toFloatOrNull()
            }
            val category = try {
                AcademicCategory.valueOf(categoryStr)
            } catch (e: Exception) {
                AcademicCategory.REGULAR
            }

            val date = try { LocalDate.parse(dateStr) } catch (e: Exception) {
                telemetry?.logJsonError()
                LocalDate(2024, 1, 1)
            }

            if (type == "TIME") {
                val startTimeStr = obj["startTime"]?.jsonPrimitive?.content ?: "09:00"
                val endTimeStr = obj["endTime"]?.jsonPrimitive?.content ?: "10:00"
                val startTime = try {
                    val parts = startTimeStr.split(":")
                    LocalTime(parts[0].toInt(), parts[1].toInt())
                } catch (e: Exception) {
                    telemetry?.logJsonError()
                    LocalTime(9, 0)
                }
                val endTime = try {
                    val parts = endTimeStr.split(":")
                    LocalTime(parts[0].toInt(), parts[1].toInt())
                } catch (e: Exception) {
                    telemetry?.logJsonError()
                    LocalTime(10, 0)
                }
                TimeEvent(
                    title = title,
                    source = EventSource.AI_GENERATED,
                    date = date,
                    startTime = startTime,
                    endTime = endTime,
                    category = category,
                    warning = warning,
                    gradeWeight = gradeWeight
                )
            } else {
                DayEvent(
                    title = title,
                    source = EventSource.AI_GENERATED,
                    category = category,
                    date = date,
                    warning = warning,
                    gradeWeight = gradeWeight
                )
            }
        }
    }

    fun parseDecomposeTaskJson(responseText: String): List<DecomposedTask> {
        val cleanJson = responseText.trim()
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()

        val root = json.parseToJsonElement(cleanJson)
        val jsonArray = when {
            root is JsonArray -> root
            root is JsonObject && root.containsKey("tasks") -> root["tasks"]!!.jsonArray
            else -> throw Exception("Unexpected JSON structure: $cleanJson")
        }

        return jsonArray.map { element ->
            val obj = element.jsonObject
            val daysBeforeDue = obj["daysBeforeDue"]?.jsonPrimitive?.let {
                it.intOrNull ?: it.content.toDoubleOrNull()?.toInt() ?: 1
            } ?: 1
            DecomposedTask(
                title = obj["title"]?.jsonPrimitive?.content ?: "Sub-task",
                daysBeforeDue = daysBeforeDue,
                description = obj["description"]?.jsonPrimitive?.content ?: ""
            )
        }
    }

    fun parseCategorizeSourceJson(responseText: String): SourceCategory {
        val cleanJson = responseText.trim()
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()

        val root = json.parseToJsonElement(cleanJson)
        val categoryName = root.jsonObject["category"]?.jsonPrimitive?.content?.uppercase() ?: "OTHER"

        return when (categoryName) {
            "SYLLABUS" -> SourceCategory.SYLLABUS
            "READING MATERIAL", "READING_MATERIAL" -> SourceCategory.READING_MATERIAL
            "LAB MANUAL", "LAB_MANUAL" -> SourceCategory.LAB_MANUAL
            "LECTURE NOTES", "LECTURE_NOTES" -> SourceCategory.LECTURE_NOTES
            else -> SourceCategory.OTHER
        }
    }
}
