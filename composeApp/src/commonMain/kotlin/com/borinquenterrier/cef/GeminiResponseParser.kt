package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

@Serializable
private data class RawGeminiEvent(
    val title: String = "Untitled Event",
    val type: String = "DAY",
    val date: String = "2024-01-01",
    val category: String = "REGULAR",
    val warning: String? = null,
    val gradeWeight: Float? = null,
    // Blank (not "09:00") so an absent time is distinguishable from a real one — a TIME
    // event with no parseable start is downgraded to a date-only DayEvent rather than
    // silently given an invented clock time a student might trust.
    val startTime: String = "",
    val endTime: String = "",
    // Set by the model when `date` was derived from a "Week N" reference instead of an
    // explicit calendar date. The date is then recomputed deterministically from the
    // week-anchor table (WeekAnchorDateResolver) — the model's own arithmetic never wins.
    val weekNumber: Int? = null,
    val dayName: String? = null
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

    /**
     * Deterministically corrects the model's signature year confabulation before
     * [filterToSourceYears] drops the events outright. A schedule that lists dates with no
     * year ("Sept. 30 & Oct. 2") forces the model to infer one, and on a batch that lacks
     * the document's term header it tends to land on the year it believes is current —
     * exactly one year off from the document's real term (confirmed live 2026-08-25:
     * HIS 378W fall-2025 batch 2 came back dated 2026 and lost 16 real events).
     *
     * An event whose year is missing from [sourceYears] but sits exactly one year from
     * exactly one source year is remapped to that year, month/day preserved. Anything
     * further off (2099-style confabulation), ambiguous (two adjacent source years), or
     * calendar-invalid after the swap (Feb 29) is returned unchanged for
     * [filterToSourceYears] to drop.
     */
    fun remapOffByOneYears(events: List<Event>, sourceYears: Set<Int>): List<Event> {
        if (sourceYears.isEmpty()) return events
        return events.map { event ->
            val date = when (event) {
                is TimeEvent -> event.date
                is DayEvent -> event.date
            }
            if (date.year in sourceYears) return@map event
            val target = sourceYears.singleOrNull { it == date.year - 1 || it == date.year + 1 }
                ?: return@map event
            val remapped = runCatching { LocalDate(target, date.month, date.day) }
                .getOrNull() ?: return@map event
            when (event) {
                is TimeEvent -> event.copy(date = remapped)
                is DayEvent -> event.copy(date = remapped)
            }
        }
    }

    fun parseEventsJson(
        responseText: String,
        telemetry: TelemetryManager? = null,
        weekAnchors: Map<Int, LocalDate> = emptyMap()
    ): List<Event> {
        val jsonArray = extractJsonArray(responseText, "events")
        return jsonArray.map { element ->
            toEvent(json.decodeFromJsonElement(RawGeminiEvent.serializer(), element), telemetry, weekAnchors)
        }
    }

    private fun toEvent(
        raw: RawGeminiEvent,
        telemetry: TelemetryManager?,
        weekAnchors: Map<Int, LocalDate> = emptyMap()
    ): Event {
        val category = try {
            AcademicCategory.valueOf(raw.category)
        } catch (e: Exception) {
            telemetry?.logJsonError()
            AcademicCategory.REGULAR
        }
        val modelDate = try {
            LocalDate.parse(raw.date)
        } catch (e: Exception) {
            telemetry?.logJsonError()
            LocalDate(2024, 1, 1)
        }

        val (date, warning) = groundWeekDerivedDate(raw, weekAnchors, modelDate)

        val start = if (raw.type == "TIME") parseClockTime(raw.startTime, telemetry) else null
        return if (start != null) {
            // A missing/invalid end is low-risk once the start is real — assume an hour rather
            // than dropping the time the student actually needs to show up at.
            val rawEnd = parseClockTime(raw.endTime, telemetry)
            val validEnd = if (rawEnd != null && rawEnd > start) rawEnd else null
            val plusHourMins = start.hour * 60 + start.minute + 60
            // If no valid end exists and adding 1 hour would overflow midnight, the AI placed this
            // event at an unschedulable time — treat it as an all-day DayEvent rather than a
            // 1-second sentinel.
            if (validEnd == null && plusHourMins >= 24 * 60) {
                DayEvent(
                    title = raw.title,
                    source = EventSource.AI_GENERATED,
                    date = date,
                    category = category,
                    warning = warning,
                    gradeWeight = raw.gradeWeight
                )
            } else {
                val end = validEnd ?: LocalTime(plusHourMins / 60, plusHourMins % 60)
                TimeEvent(
                    title = raw.title,
                    source = EventSource.AI_GENERATED,
                    date = date,
                    startTime = start,
                    endTime = end,
                    category = category,
                    warning = warning,
                    gradeWeight = raw.gradeWeight
                )
            }
        } else {
            DayEvent(
                title = raw.title,
                source = EventSource.AI_GENERATED,
                category = category,
                date = date,
                warning = warning,
                gradeWeight = raw.gradeWeight
            )
        }
    }

    /**
     * Week-derived dates are recomputed from the deterministic anchor table; the model's own
     * calendar arithmetic is only kept when there is nothing to ground it against. Returns the
     * date to use plus the (possibly annotated) warning.
     */
    private fun groundWeekDerivedDate(
        raw: RawGeminiEvent,
        weekAnchors: Map<Int, LocalDate>,
        modelDate: LocalDate
    ): Pair<LocalDate, String?> {
        val date = if (raw.weekNumber != null) {
            WeekAnchorDateResolver.resolve(weekAnchors, raw.weekNumber, raw.dayName, modelDate)
        } else {
            modelDate
        }
        if (date == modelDate) return date to raw.warning
        val note = "Date grounded to the Week ${raw.weekNumber} anchor table (model computed ${raw.date})."
        return date to (raw.warning?.let { "$it; $note" } ?: note)
    }

    /** Parses an "HH:mm" string, returning null (and logging telemetry) on failure. */
    private fun parseClockTime(
        value: String,
        telemetry: TelemetryManager?
    ): LocalTime? =
        try {
            val parts = value.split(":")
            LocalTime(parts[0].toInt(), parts[1].toInt())
        } catch (e: Exception) {
            telemetry?.logJsonError()
            null
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
