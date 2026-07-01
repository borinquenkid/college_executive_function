package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Restricts a list of events to the active semester window configured in [StudyPreferences]
 * (semesterStart..semesterEnd, inclusive). When either bound is unset or unparseable the window
 * is null and events pass through unchanged — the same lenient behavior ingestion has always used.
 *
 * Extracted from SourceAdder so ingestion and the calendar view share one definition of
 * "in the active semester": remote-synced STUDENT events never went through ingestion, so the
 * view must apply the same window itself.
 */
object SemesterFilter {

    /** Parses the semester bounds, or null if either is missing/invalid (→ no filtering). */
    fun window(prefs: StudyPreferences): Pair<LocalDate, LocalDate>? {
        val start = prefs.semesterStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val end = prefs.semesterEnd?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return if (start != null && end != null) start to end else null
    }

    fun apply(events: List<Event>, window: Pair<LocalDate, LocalDate>?): List<Event> {
        if (window == null) return events
        return events.filter { EventDeduplicator.dateOf(it) in window.first..window.second }
    }

    fun apply(events: List<Event>, prefs: StudyPreferences): List<Event> =
        apply(events, window(prefs))
}
