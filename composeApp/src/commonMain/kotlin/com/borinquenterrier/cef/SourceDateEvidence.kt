package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Cheap, deterministic check for whether a document contains anything a calendar date could have
 * been read from. A document with no month-day, numeric or ISO date cannot ground an extracted
 * event, so any event the model returns for it is invented (a due-date-free project handout came
 * back with "2024-01-01" and "today" dates, 2026-09-29).
 */
object SourceDateEvidence {
    private const val MONTH =
        "(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|June?|July?|Aug(?:ust)?|" +
            "Sept?(?:ember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"

    private val PATTERNS = listOf(
        Regex("""\b$MONTH\.?\s+\d{1,2}\b""", RegexOption.IGNORE_CASE),                       // Sept. 28, October 13
        Regex("""\b\d{1,2}(?:st|nd|rd|th)?\s+(?:of\s+)?$MONTH\b""", RegexOption.IGNORE_CASE), // 15 December
        Regex("""\b\d{1,2}/\d{1,2}\b"""),                                                      // 9/15
        Regex("""\b\d{4}-\d{2}-\d{2}\b"""),                                                    // 2026-09-03
    )

    fun hasDatableContent(text: String): Boolean = PATTERNS.any { it.containsMatchIn(text) }

    private val MONTH_CAPTURE = Regex("""(?i)\b($MONTH)\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:\s*[–—-]\s*(?:($MONTH)\.?\s+)?(\d{1,2})\b)?""")
    private val DAY_MONTH = Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($MONTH)\b""")
    // "9/29", "09/29/26", and compact lists "9/22, 29" (later bare numbers share the month).
    private val NUMERIC = Regex("""(?<![\d/])(\d{1,2})/(\d{1,2})(?:/\d{2,4})?(?![\d/])((?:\s*,\s*\d{1,2}(?![\d/]))*)""")
    private val ISO = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")

    private fun monthNumber(token: String): Int =
        listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
            .indexOf(token.take(3).lowercase()) + 1

    /**
     * Whether [text] states [date] — literally (numeric, month-name, ISO, compact "9/22, 29" lists) or
     * inside a stated range ("Sept. 28–Oct. 2"). Year is not checked; see [dropUnmentionedToday].
     */
    fun mentions(text: String, date: LocalDate): Boolean {
        val m = date.month.ordinal + 1
        val d = date.day
        if (ISO.findAll(text).any { it.value == date.toString() }) return true
        if (DAY_MONTH.findAll(text).any { it.groupValues[1].toInt() == d && monthNumber(it.groupValues[2]) == m }) return true
        if (NUMERIC.findAll(text).any { match ->
                match.groupValues[1].toInt() == m && (match.groupValues[2].toInt() == d ||
                    match.groupValues[3].split(',').mapNotNull { it.trim().toIntOrNull() }.contains(d))
            }) return true
        return MONTH_CAPTURE.findAll(text).any { match ->
            val startMonth = monthNumber(match.groupValues[1])
            val startDay = match.groupValues[2].toInt()
            if (match.groupValues[4].isEmpty()) return@any startMonth == m && startDay == d
            val endMonth = match.groupValues[3].ifEmpty { null }?.let { monthNumber(it) } ?: startMonth
            val endDay = match.groupValues[4].toInt()
            val key = m * 100 + d
            val from = startMonth * 100 + startDay
            val to = endMonth * 100 + endDay
            if (from <= to) key in from..to else key >= from || key <= to // range across New Year
        }
    }

    /**
     * The extraction prompt carries "Today's Date", and for items it cannot place the model falls back
     * to it — three unrelated Missouri S&T documents came back with events on 2026-09-29, the run date
     * (STAT 5353 "Exam 2", stated as November 20). Drop events dated [today] whose date the source
     * never states. Only [today] is checked, so legitimately computed dates are untouched.
     */
    fun dropUnmentionedToday(events: List<Event>, text: String, today: LocalDate): List<Event> {
        if (events.none { it.date == today } || mentions(text, today)) return events
        return events.filter { it.date != today }
    }
}
