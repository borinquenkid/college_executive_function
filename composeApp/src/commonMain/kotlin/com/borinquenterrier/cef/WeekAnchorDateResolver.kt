package com.borinquenterrier.cef

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Deterministic date arithmetic for week-anchored schedules.
 *
 * Documents like the STLCC ENG 101 weekly schedule never state per-session dates — only
 * week ranges ("Week 6: July 13–19, 2026") plus weekday labels. The extraction model is
 * asked to report WHICH week and weekday it read an event under ([EventBuilder]'s
 * `weekNumber`/`dayName` fields); this object recomputes the calendar date from the
 * anchor table so the model's own arithmetic is never load-bearing. The 2026-08-16 eval
 * run shifted an entire batch of Week 6–8 dates +1 day by treating the ranges as
 * Sunday-anchored — a copy step the model gets right even when its math is wrong.
 *
 * Same philosophy as [ClassMeetingReconciler]: the LLM reads, the code computes.
 */
object WeekAnchorDateResolver {

    private val RANGE_START = Regex("""^([A-Za-z]+)\s+(\d{1,2})""")
    private val YEAR = Regex("""\b(\d{4})\b""")

    private val MONTHS = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december"
    )

    // Index = offset from Monday. A plain list (not DayOfWeek.entries) because DayOfWeek is a
    // typealias to the Java enum on JVM, where Kotlin's `entries` accessor isn't guaranteed.
    private val DAY_NAMES = listOf(
        "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
    )

    /**
     * Builds a week-number → Monday map from every anchor definition found in the
     * fragments' text or their injected `weekAnchors` metadata (a pre-batched summary
     * page may carry the table only via [WeekAnchorExtractor]'s injection).
     *
     * A range that starts mid-week (e.g. a Sunday-anchored "June 7–13") snaps to the
     * first Monday inside it. Yearless anchors borrow the year only when every anchor
     * that does state one agrees; otherwise they are skipped rather than guessed.
     */
    fun buildTable(fragments: List<SourceFragment>): Map<Int, LocalDate> {
        data class RawAnchor(val week: Int, val month: Int, val day: Int, val year: Int?)

        val raw = mutableListOf<RawAnchor>()
        val texts = fragments.flatMap { listOfNotNull(it.text, it.metadata["weekAnchors"]) }
        for (text in texts) {
            for ((week, range) in WeekAnchorExtractor.findAnchors(text)) {
                val start = RANGE_START.find(range) ?: continue
                val month = parseMonth(start.groupValues[1]) ?: continue
                val day = start.groupValues[2].toIntOrNull() ?: continue
                val year = YEAR.find(range)?.value?.toIntOrNull()
                raw += RawAnchor(week, month, day, year)
            }
        }

        val statedYears = raw.mapNotNull { it.year }.toSet()
        val borrowedYear = statedYears.singleOrNull()

        val table = mutableMapOf<Int, LocalDate>()
        for (anchor in raw) {
            val year = anchor.year ?: borrowedYear ?: continue
            val start = runCatching { LocalDate(year, anchor.month, anchor.day) }.getOrNull() ?: continue
            table[anchor.week] = mondayOf(start)
        }
        return table
    }

    /**
     * Returns the date to use for an event the model derived from "Week [weekNumber]".
     *
     *  - A parseable [dayName] is authoritative: Monday of that week + the weekday offset,
     *    regardless of what the model computed.
     *  - No [dayName] (summary-table rows like "Due Week 6"): the model may know the day
     *    from context we can't see, so an in-week [modelDate] is kept; an out-of-week one
     *    snaps to Wednesday — the same mid-week default [EventBuilder]'s prompt documents.
     *  - Unknown week or an unparseable label: [modelDate] is kept — never ground a date
     *    against evidence we don't actually have.
     */
    fun resolve(
        table: Map<Int, LocalDate>,
        weekNumber: Int,
        dayName: String?,
        modelDate: LocalDate
    ): LocalDate {
        val monday = table[weekNumber] ?: return modelDate
        val dayOffset = parseDayOffset(dayName)
        return when {
            dayOffset != null -> monday.plus(DatePeriod(days = dayOffset))
            dayName.isNullOrBlank() -> {
                val inWeek = modelDate >= monday && modelDate <= monday.plus(DatePeriod(days = 6))
                if (inWeek) modelDate else monday.plus(DatePeriod(days = 2))
            }
            else -> modelDate
        }
    }

    private fun parseMonth(name: String): Int? {
        if (name.length < 3) return null
        val normalized = name.lowercase()
        val index = MONTHS.indexOfFirst { it.startsWith(normalized) || normalized.startsWith(it) }
        return if (index >= 0) index + 1 else null
    }

    private fun parseDayOffset(raw: String?): Int? {
        val normalized = raw?.trim()?.uppercase() ?: return null
        if (normalized.length < 3) return null
        val index = DAY_NAMES.indexOfFirst {
            it.startsWith(normalized) || normalized.startsWith(it)
        }
        return index.takeIf { it >= 0 }
    }

    private fun mondayOf(start: LocalDate): LocalDate {
        var date = start
        while (date.dayOfWeek != DayOfWeek.MONDAY) date = date.plus(DatePeriod(days = 1))
        return date
    }
}
