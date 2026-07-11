package com.borinquenterrier.cef

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * One completed term's distilled facts (ADR 0004 / ROADMAP Phase 13, XM-2). Deliberately narrow:
 * per-term stats only. It never attempts to decide whether a course in one term is "the same
 * course" as one in another term — see ADR 0004's `BIO337` finding (same course number, different
 * subject, two different terms) for why that comparison is unsafe to make on course-code equality
 * alone. Each [StudentTermProfile] stands on its own; nothing here merges across terms.
 */
data class StudentTermProfile(
    val termStart: LocalDate,
    val termEnd: LocalDate,
    val courseLoad: Int,
    val categoryDistribution: Map<AcademicCategory, Int>,
    val deadlineCadenceByWeekday: Map<DayOfWeek, Int>
)

object TermProfileAggregator {

    /**
     * Aggregates one term's worth of events into a [StudentTermProfile], or null for an empty
     * term (nothing to summarize). [courseLoad] counts distinct [Event.sourceId] values — the
     * source document an event was generated from is this app's existing notion of "a course",
     * so no course-code parsing is needed and no cross-term identity question arises within a
     * single term's own aggregation.
     */
    fun aggregate(events: List<Event>): StudentTermProfile? {
        if (events.isEmpty()) return null

        val maxDate = events.maxOf { it.date }
        val (termStart, termEnd) = SemesterResolver.getSemesterRange(maxDate)

        val courseLoad = events.mapNotNull { it.sourceId }.distinct().size
        val categoryDistribution = events.groupingBy { it.category }.eachCount()
        val deadlineCadenceByWeekday = events
            .filter { it.category == AcademicCategory.DEADLINE }
            .groupingBy { it.date.dayOfWeek }
            .eachCount()

        return StudentTermProfile(termStart, termEnd, courseLoad, categoryDistribution, deadlineCadenceByWeekday)
    }

    /**
     * Renders a small, fixed-size text block from multiple terms' profiles for injection into
     * [ContextAgent]'s prompt (XM-4) — not an LLM call, plain formatting. Callers are responsible
     * for the min-2-terms floor (ADR 0004): this function summarizes whatever it's given.
     */
    fun summarize(profiles: List<StudentTermProfile>): String {
        if (profiles.isEmpty()) return ""

        val avgCourseLoad = profiles.map { it.courseLoad }.average()
        val combinedCategoryCounts = mutableMapOf<AcademicCategory, Int>()
        profiles.forEach { profile ->
            profile.categoryDistribution.forEach { (category, count) ->
                combinedCategoryCounts[category] = (combinedCategoryCounts[category] ?: 0) + count
            }
        }
        val topCategories = combinedCategoryCounts.entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString(", ") { "${it.key}" }

        val combinedWeekdayCounts = mutableMapOf<DayOfWeek, Int>()
        profiles.forEach { profile ->
            profile.deadlineCadenceByWeekday.forEach { (day, count) ->
                combinedWeekdayCounts[day] = (combinedWeekdayCounts[day] ?: 0) + count
            }
        }
        val topDeadlineDays = combinedWeekdayCounts.entries
            .sortedByDescending { it.value }
            .take(2)
            .joinToString(", ") { "${it.key}" }

        val roundedAvgCourseLoad = kotlin.math.round(avgCourseLoad).toInt()
        return "Across ${profiles.size} prior terms, this student typically takes " +
            "~$roundedAvgCourseLoad course(s) per term. " +
            "Most common event types: $topCategories." +
            (if (topDeadlineDays.isNotBlank()) " Deadlines cluster on: $topDeadlineDays." else "")
    }
}
