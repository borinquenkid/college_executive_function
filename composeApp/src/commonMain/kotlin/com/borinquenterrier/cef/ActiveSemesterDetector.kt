package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

data class SemesterRange(val start: LocalDate, val end: LocalDate) {
    val label: String = when (start.monthNumber) {
        1 -> "Spring ${start.year}"
        5 -> "Summer ${start.year}"
        else -> "Fall ${start.year}"
    }
    operator fun contains(date: LocalDate) = date >= start && date <= end
}

/**
 * Detects whether a source document covers multiple semesters in the same year,
 * and if so, identifies which semester is currently active based on today's date.
 *
 * This powers the GroundingGuardAIService's semester-level filter: a multi-semester
 * academic calendar (Spring/Summer/Fall all in one PDF) must be narrowed to the
 * active semester so that Fall events don't bleed into a Summer extraction.
 */
object ActiveSemesterDetector {

    // Matches "Summer 2026", "Fall 2025", "Spring 2026", etc. — year-anchored only.
    // Bare keywords like "spring break" or "fall semester" without a year are intentionally excluded.
    private val SEMESTER_YEAR_PATTERN = Regex(
        """(spring|summer|fall|autumn|winter)\s+(20\d{2})""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Returns true when the source text explicitly names two or more distinct semesters
     * within the same calendar year (e.g. "Spring 2026" AND "Summer 2026").
     * A source that only names one semester per year (e.g. "Fall 2025 ... Spring 2026") returns false.
     */
    fun hasMultipleSemestersForSameYear(sourceText: String): Boolean {
        val matches = SEMESTER_YEAR_PATTERN.findAll(sourceText)
            .map { it.groupValues[1].lowercase() to it.groupValues[2].toInt() }
            .toSet()
        return matches.map { it.second }
            .distinct()
            .any { year -> matches.count { it.second == year } >= 2 }
    }

    /**
     * Returns the standard semester window (Spring/Summer/Fall) from the source years
     * that contains [today], or null if no match is found.
     *
     * Semester windows overlap at boundaries (Summer starts May 1, Spring ends May 31;
     * Fall starts Aug 1, Summer ends Aug 31). When two windows both contain today, the
     * first match wins in the order: Spring → Summer → Fall.
     */
    fun detect(sourceText: String, today: LocalDate): SemesterRange? {
        val years = GeminiResponseParser.YEAR_PATTERN.findAll(sourceText)
            .map { it.value.toInt() }
            .toSet()
        if (years.isEmpty()) return null
        return years.flatMap { semestersFor(it) }.firstOrNull { today in it }
    }

    private fun semestersFor(year: Int): List<SemesterRange> = listOf(
        SemesterRange(LocalDate(year, 1, 1), LocalDate(year, 5, 31)),
        SemesterRange(LocalDate(year, 5, 1), LocalDate(year, 8, 31)),
        SemesterRange(LocalDate(year, 8, 1), LocalDate(year, 12, 31))
    )
}
