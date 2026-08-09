package com.borinquenterrier.cef

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * Fills class-meeting dates the AI missed by inferring the course's recurring meeting
 * pattern from the CLASS events it *did* extract.
 *
 * A syllabus states its meeting days outright ("Mon/Wed, 8 weeks"), but extraction tags
 * each date independently — so a Wednesday crowded with deadlines occasionally comes back
 * with no CLASS event at all (2026-08-09 eval run: 15/16 dates, Jul 1 missing). For a
 * student who relies on the generated calendar instead of their own working memory, a
 * silently absent class session is the one error they can't self-correct. This pass makes
 * the meeting grid deterministic instead of hoping the model is consistent per-date.
 *
 * Deliberately conservative:
 *  - needs ≥[MIN_CLASS_DATES] distinct CLASS dates before inferring anything, and a weekday
 *    must recur in at least half the observed weeks (≥3 times) to count as a meeting day —
 *    one-off workshops and review sessions never form a pattern;
 *  - fills at most [MAX_FILL] gaps — more missing dates means a mid-semester schedule
 *    change (8-week/12-week splits), not a model miss, and inventing sessions there would
 *    be worse than the miss;
 *  - never fills a date carrying holiday/break/"no class" evidence, and never extends
 *    beyond the observed first..last class date range;
 *  - synthesized events are date-only (no fabricated clock time) and carry a warning so
 *    the UI shows them as inferred rather than passing them off as extracted fact.
 */
object ClassMeetingReconciler {

    private const val MIN_CLASS_DATES = 6
    private const val MAX_FILL = 2

    private val NO_CLASS_MARKERS = listOf("no class", "holiday", "break")

    fun fillMissedMeetings(events: List<Event>): List<Event> {
        val classDates = events
            .filter { it.category == AcademicCategory.CLASS }
            .map { it.date }
            .distinct()
            .sorted()
        if (classDates.size < MIN_CLASS_DATES) return events

        val first = classDates.first()
        val last = classDates.last()
        val weeksSpanned = first.daysUntil(last) / 7 + 1

        val meetingDays: Set<DayOfWeek> = classDates
            .groupingBy { it.dayOfWeek }
            .eachCount()
            .filterValues { it >= maxOf(3, (weeksSpanned + 1) / 2) }
            .keys
        if (meetingDays.isEmpty()) return events

        val observed = classDates.toSet()
        val missing = generateSequence(first) { it.plus(DatePeriod(days = 1)) }
            .takeWhile { it <= last }
            .filter { it.dayOfWeek in meetingDays && it !in observed }
            .toList()
        if (missing.isEmpty() || missing.size > MAX_FILL) return events

        val fillable = missing.filterNot { date -> hasNoClassEvidence(events, date) }
        if (fillable.isEmpty()) return events

        return events + fillable.map { date ->
            val dayName = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            DayEvent(
                title = "Class meeting",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.CLASS,
                date = date,
                warning = "Added automatically: this course meets every $dayName, but no class " +
                    "session was found on this date — double-check your syllabus."
            )
        }
    }

    private fun hasNoClassEvidence(events: List<Event>, date: LocalDate): Boolean =
        events.any { event ->
            event.date == date && (
                event.category == AcademicCategory.HOLIDAY ||
                    event.category == AcademicCategory.SEMESTER_BOUND ||
                    NO_CLASS_MARKERS.any { it in event.title.lowercase() }
                )
        }
}
