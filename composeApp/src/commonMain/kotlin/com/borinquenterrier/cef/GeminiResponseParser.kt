package com.borinquenterrier.cef

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Serializable
private data class RawGeminiEvent(
    val title: String = "Untitled Event",
    val type: String = "DAY",
    val date: String = "2024-01-01",
    val category: String = "REGULAR",
    val warning: String? = null,
    val gradeWeight: Float? = null,
    val startTime: String = "09:00",
    val endTime: String = "10:00"
)

@Serializable
private data class RawGeminiTask(
    val title: String = "Sub-task",
    val daysBeforeDue: Double = 1.0,
    val description: String = ""
)

class SourceValidationException(message: String) : Exception(message)

@Serializable
private data class RawCategorization(
    val category: String = "OTHER",
    val isValid: Boolean = true,
    val reason: String = ""
)

object GeminiResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
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
        val jsonArray = extractJsonArray(responseText, "events")
        return jsonArray.map { element ->
            toEvent(json.decodeFromJsonElement(RawGeminiEvent.serializer(), element), telemetry)
        }
    }

    private fun toEvent(raw: RawGeminiEvent, telemetry: TelemetryManager?): Event {
        val category = try {
            AcademicCategory.valueOf(raw.category)
        } catch (e: Exception) {
            AcademicCategory.REGULAR
        }
        val date = try {
            LocalDate.parse(raw.date)
        } catch (e: Exception) {
            telemetry?.logJsonError()
            LocalDate(2024, 1, 1)
        }

        return if (raw.type == "TIME") {
            TimeEvent(
                title = raw.title,
                source = EventSource.AI_GENERATED,
                date = date,
                startTime = parseClockTime(raw.startTime, LocalTime(9, 0), telemetry),
                endTime = parseClockTime(raw.endTime, LocalTime(10, 0), telemetry),
                category = category,
                warning = raw.warning,
                gradeWeight = raw.gradeWeight
            )
        } else {
            DayEvent(
                title = raw.title,
                source = EventSource.AI_GENERATED,
                category = category,
                date = date,
                warning = raw.warning,
                gradeWeight = raw.gradeWeight
            )
        }
    }

    /** Parses an "HH:mm" string, falling back to [default] (and logging telemetry) on failure. */
    private fun parseClockTime(value: String, default: LocalTime, telemetry: TelemetryManager?): LocalTime =
        try {
            val parts = value.split(":")
            LocalTime(parts[0].toInt(), parts[1].toInt())
        } catch (e: Exception) {
            telemetry?.logJsonError()
            default
        }

    fun parseDecomposeTaskJson(responseText: String): List<DecomposedTask> {
        val jsonArray = extractJsonArray(responseText, "tasks")
        return jsonArray.map { element ->
            val raw = json.decodeFromJsonElement(RawGeminiTask.serializer(), element)
            DecomposedTask(
                title = raw.title,
                daysBeforeDue = raw.daysBeforeDue.toInt(),
                description = raw.description
            )
        }
    }

    fun parseCategorizeSourceJson(responseText: String): SourceCategory {
        val element = json.parseToJsonElement(stripCodeFences(responseText))
        val raw = json.decodeFromJsonElement(RawCategorization.serializer(), element)
        if (!raw.isValid) {
            throw SourceValidationException(raw.reason.ifBlank { "Document does not contain the required elements for its category." })
        }
        return when (raw.category.uppercase()) {
            "SYLLABUS" -> SourceCategory.SYLLABUS
            "CALENDAR" -> SourceCategory.CALENDAR
            "READING MATERIAL", "READING_MATERIAL" -> SourceCategory.READING_MATERIAL
            "LAB MANUAL", "LAB_MANUAL" -> SourceCategory.LAB_MANUAL
            "LECTURE NOTES", "LECTURE_NOTES" -> SourceCategory.LECTURE_NOTES
            else -> SourceCategory.OTHER
        }
    }

    private fun stripCodeFences(responseText: String): String =
        responseText.trim()
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()

    /** Parses [responseText] as either a bare JSON array, or an object with the array nested under [arrayKey]. */
    private fun extractJsonArray(responseText: String, arrayKey: String): JsonArray {
        val cleanJson = stripCodeFences(responseText)
        val root = json.parseToJsonElement(cleanJson)
        return when {
            root is JsonArray -> root
            root is JsonObject && root.containsKey(arrayKey) -> root[arrayKey]!!.jsonArray
            else -> throw Exception("Unexpected JSON structure: $cleanJson")
        }
    }
}
