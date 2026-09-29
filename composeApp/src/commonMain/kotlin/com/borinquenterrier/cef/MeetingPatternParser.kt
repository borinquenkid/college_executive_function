package com.borinquenterrier.cef

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/** A course's stated recurring meeting: weekdays plus, when unambiguous, the class time. */
data class MeetingPattern(
    val days: Set<DayOfWeek>,
    val start: LocalTime? = null,
    val end: LocalTime? = null
) {
    val hasTime: Boolean get() = start != null && end != null
}

/**
 * Deterministically reads a syllabus's stated meeting pattern ("Meeting Days: MoWeFr / Meeting Time:
 * 09:00AM - 09:50AM", "Tue & Thu 3:30 pm to 4:45 pm", "1:00-1:50pm MWF").
 *
 * The model reads these inconsistently — the same CS 2300 syllabus came back with date-only class
 * sessions on one run and 15:30 sessions on the next (2026-09-29) — so the pattern is parsed here
 * and applied by [MeetingScheduleApplier] instead.
 *
 * A day token is paired with the nearest time range within [MAX_GAP] characters. Candidates whose
 * closest preceding label is an office marker are office hours and ignored; candidates labeled as a
 * meeting/lecture/section win over unlabeled ones (e.g. an exam line "Monday, September 28, 12:00 PM–
 * 12:50 PM"). The largest day set wins; if its candidates disagree on time (two sections at different
 * hours) only the days are returned.
 */
object MeetingPatternParser {
    private const val MAX_GAP = 40
    private const val LABEL_LOOKBACK = 80

    // Full names take an optional plural ("Class Lecture: Mondays 1-2 PM", UT BIO 325L).
    private const val DAY_WORD =
        "(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)s?|Mon|Tues|Tue|Wed|Thurs|Thur|Thu|Fri|Sat|Sun"
    private val DAY_TOKEN = Regex(
        "(?<![A-Za-z])(?:" +
            "(?:$DAY_WORD)\\.?(?:\\s*(?:,\\s*and|,|&|/|and)\\s*(?:$DAY_WORD)\\.?)*" +
            "|(?:Mo|Tu|We|Th|Fr|Sa|Su){2,5}" +
            "|[MTWRF](?:/[MTWRF])+" +
            "|[MTWRF]{2,5}" +
            ")(?![A-Za-z])"
    )
    private val TIME_RANGE = Regex(
        """(\d{1,2})(?::(\d{2}))?\s*(?:([ap])\.?m\.?)?:?\s*(?:-|–|—|to)\s*(\d{1,2})(?::(\d{2}))?\s*(?:([ap])\.?m\.?)?""",
        RegexOption.IGNORE_CASE
    )

    private val OFFICE_MARKERS = listOf("office", "o:ce", "o@ce", "oﬃce", "ofﬁce")
    private val LABEL_MARKERS = listOf("meet", "lecture", "sect", "class time", "recitation")

    private const val COMPACT_ORDER = "MTWRF"
    private val COMPACT_DAYS = mapOf(
        'M' to DayOfWeek.MONDAY, 'T' to DayOfWeek.TUESDAY, 'W' to DayOfWeek.WEDNESDAY,
        'R' to DayOfWeek.THURSDAY, 'F' to DayOfWeek.FRIDAY
    )
    private val WORD_PREFIXES = listOf(
        "mo" to DayOfWeek.MONDAY, "tu" to DayOfWeek.TUESDAY, "we" to DayOfWeek.WEDNESDAY,
        "th" to DayOfWeek.THURSDAY, "fr" to DayOfWeek.FRIDAY, "sa" to DayOfWeek.SATURDAY,
        "su" to DayOfWeek.SUNDAY
    )

    private data class Candidate(val days: Set<DayOfWeek>, val start: LocalTime, val end: LocalTime, val labeled: Boolean)

    fun parse(text: String): MeetingPattern? {
        val times = TIME_RANGE.findAll(text).mapNotNull { m -> parseTime(m)?.let { m.range to it } }.toList()
        if (times.isEmpty()) return null

        val candidates = DAY_TOKEN.findAll(text).mapNotNull { dayMatch ->
            val days = parseDays(dayMatch.value) ?: return@mapNotNull null
            val (timeRange, time) = times.minByOrNull { gap(dayMatch.range, it.first) } ?: return@mapNotNull null
            if (gap(dayMatch.range, timeRange) > MAX_GAP) return@mapNotNull null
            val first = minOf(dayMatch.range.first, timeRange.first)
            when (labelBefore(text, first)) {
                Label.OFFICE -> null
                Label.MEETING -> Candidate(days, time.first, time.second, labeled = true)
                Label.NONE -> Candidate(days, time.first, time.second, labeled = false)
            }
        }.toList()

        val pool = candidates.filter { it.labeled }.ifEmpty { candidates }
        if (pool.isEmpty()) return null
        val maxSize = pool.maxOf { it.days.size }
        val widest = pool.filter { it.days.size == maxSize }
        val days = widest.map { it.days }.distinct().singleOrNull() ?: return null
        val slot = widest.map { it.start to it.end }.distinct().singleOrNull()
        return MeetingPattern(days, slot?.first, slot?.second)
    }

    private enum class Label { OFFICE, MEETING, NONE }

    private fun labelBefore(text: String, index: Int): Label {
        val window = text.substring(maxOf(0, index - LABEL_LOOKBACK), index).lowercase()
        val office = OFFICE_MARKERS.maxOf { window.lastIndexOf(it) }
        // "Mondays: 2-2:30 after lecture" inside an office-hours block refers to the lecture; it
        // doesn't label what follows (UT BIO 325L spring, 2026-09-29).
        val meeting = LABEL_MARKERS.maxOf { label ->
            Regex("""(?<!after |before )${Regex.escape(label)}""").findAll(window).lastOrNull()?.range?.first ?: -1
        }
        return when {
            office < 0 && meeting < 0 -> Label.NONE
            office > meeting -> Label.OFFICE
            else -> Label.MEETING
        }
    }

    private fun gap(a: IntRange, b: IntRange): Int =
        maxOf(0, maxOf(a.first, b.first) - minOf(a.last, b.last) - 1)

    internal fun parseDays(token: String): Set<DayOfWeek>? {
        if (token.none { it.isLowerCase() }) {
            val letters = token.filter { it.isLetter() }
            val indices = letters.map { COMPACT_ORDER.indexOf(it) }
            // Canonical order and no repeats ("MWF", "TR") — rejects stray capitals like "FM".
            if (indices.any { it < 0 } || indices.zipWithNext().any { (a, b) -> a >= b }) return null
            return letters.mapNotNull { COMPACT_DAYS[it] }.toSet()
        }
        val words = if (Regex("(?:Mo|Tu|We|Th|Fr|Sa|Su)+").matches(token)) {
            token.chunked(2)
        } else {
            token.split(Regex("[^A-Za-z]+")).filter { it.isNotBlank() && !it.equals("and", ignoreCase = true) }
        }
        val days = words.map { word ->
            WORD_PREFIXES.firstOrNull { word.lowercase().startsWith(it.first) }?.second ?: return null
        }
        return days.toSet()
    }

    private fun parseTime(m: MatchResult): Pair<LocalTime, LocalTime>? {
        val g = m.groupValues
        val (sh, sm, sMer, eh, em, eMer) = listOf(g[1], g[2], g[3], g[4], g[5], g[6])
        // Require minutes or a meridiem so page/chapter ranges ("1.1–1.3", "14–18") never match.
        if (sm.isEmpty() && em.isEmpty() && sMer.isEmpty() && eMer.isEmpty()) return null
        val endMer = eMer.lowercase().ifEmpty { null }
        val startMer = sMer.lowercase().ifEmpty { null }
        val end = toTime(eh.toInt(), em.toIntOrNull() ?: 0, endMer ?: startMer) ?: return null
        val start = if (startMer != null) {
            toTime(sh.toInt(), sm.toIntOrNull() ?: 0, startMer)
        } else {
            // "1:00-1:50pm" takes the end's meridiem, unless that would put start after end ("11:00-12:15pm").
            toTime(sh.toInt(), sm.toIntOrNull() ?: 0, endMer)?.takeIf { it < end }
                ?: toTime(sh.toInt(), sm.toIntOrNull() ?: 0, if (endMer == "p") "a" else endMer)
        } ?: return null
        return if (start < end) start to end else null
    }

    private fun toTime(hour: Int, minute: Int, meridiem: String?): LocalTime? {
        if (minute !in 0..59) return null
        val h = when (meridiem) {
            "a" -> if (hour in 1..12) hour % 12 else return null
            "p" -> if (hour in 1..12) hour % 12 + 12 else return null
            // No meridiem anywhere: class-schedule convention — 1-7 is afternoon, 8-11 morning.
            else -> when (hour) {
                in 1..7 -> hour + 12
                in 8..23 -> hour
                else -> return null
            }
        }
        return LocalTime(h, minute)
    }

    private operator fun <T> List<T>.component6(): T = this[5]
}
