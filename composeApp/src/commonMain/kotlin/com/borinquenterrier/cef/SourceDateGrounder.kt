package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Deterministic date-in-source detection for deliverable events.
 *
 * A DEADLINE/FINALS asserts that a graded item is due on a specific date. If that date appears
 * nowhere in the syllabus text, the deliverable is *ungrounded* — most likely confabulated. This
 * object only **classifies** (grounded vs ungrounded); the policy applied to the ungrounded set
 * (drop, warn, or route to a date-picker dialog) is decided by the caller.
 *
 * Matching is regex over a normalised copy of the source, covering the formats syllabi commonly
 * use: ISO (`2026-07-15`), numeric (`7/15`, `07/15/2026`, `7-15-2026`), and month-name
 * (`July 15`, `Jul 15`, `15 July`, `15th of July`, `Sept 3`). Word boundaries prevent near-miss
 * matches (`July 15` must not satisfy day 150). "Creative" formats a teacher might invent
 * (`the Friday before break`, `week 6`) will not match — by design those land in the ungrounded
 * set for downstream review rather than being silently trusted.
 */
object SourceDateGrounder {

    /** Categories whose date is expected to be stated explicitly in the source. */
    val DATE_GROUNDED_CATEGORIES = setOf(AcademicCategory.DEADLINE, AcademicCategory.FINALS)

    private val MONTHS = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december"
    )
    private val MONTH_ABBR = listOf(
        "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
    )

    private val ORDINAL = Regex("""(\d+)(st|nd|rd|th)\b""")
    private val PUNCT = Regex("""[.,]""")
    private val WHITESPACE = Regex("""\s+""")

    /** Lowercase, strip ordinal suffixes, drop punctuation, collapse whitespace. */
    internal fun normalize(text: String): String =
        text.lowercase()
            .replace(ORDINAL) { it.groupValues[1] }
            .replace(PUNCT, " ")
            .replace(WHITESPACE, " ")

    fun isDateReferenced(date: LocalDate, sourceText: String): Boolean {
        val norm = normalize(sourceText)
        return patternsFor(date).any { it.containsMatchIn(norm) }
    }

    private fun patternsFor(date: LocalDate): List<Regex> {
        val y = date.year
        val m = date.monthNumber
        val d = date.dayOfMonth
        val mPad = m.toString().padStart(2, '0')
        val dPad = d.toString().padStart(2, '0')
        val monthWords = buildList {
            add(MONTHS[m - 1])
            add(MONTH_ABBR[m - 1])
            if (m == 9) add("sept")
        }

        val pats = mutableListOf<Regex>()
        // ISO: 2026-07-15 or 2026/07/15
        pats += Regex("""\b$y[-/]$mPad[-/]$dPad\b""")
        // Numeric M/D, optional zero-pad, optional /year, '/' or '-' separators
        pats += Regex("""\b0?$m[/-]0?$d(?:[/-]\d{2,4})?\b""")
        // Month-name forms (commas/periods already normalised to spaces; "of" tolerated)
        for (mw in monthWords) {
            pats += Regex("""\b$mw\s+0?$d\b""")
            pats += Regex("""\b0?$d\s+(?:of\s+)?$mw\b""")
        }
        return pats
    }

    data class Classification(val grounded: List<Event>, val ungrounded: List<Event>)

    /**
     * Partitions [events] into grounded vs ungrounded. Only [DATE_GROUNDED_CATEGORIES] are
     * subject to the check; every other category is kept as grounded (its date is not expected
     * to appear verbatim in the syllabus).
     */
    fun classifyDeliverables(events: List<Event>, sourceText: String): Classification {
        val grounded = mutableListOf<Event>()
        val ungrounded = mutableListOf<Event>()
        for (event in events) {
            if (event.category in DATE_GROUNDED_CATEGORIES && !isDateReferenced(event.date, sourceText)) {
                ungrounded += event
            } else {
                grounded += event
            }
        }
        return Classification(grounded, ungrounded)
    }
}
