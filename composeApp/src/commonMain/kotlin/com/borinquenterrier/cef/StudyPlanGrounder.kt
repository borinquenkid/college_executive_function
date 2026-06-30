package com.borinquenterrier.cef

import kotlinx.datetime.daysUntil

/**
 * Deterministic content grounding for study plans — the safety net the year-level filter alone
 * could not provide.
 *
 * A `STUDY_BLOCK` is, by the prompt's own definition, preparation scheduled "in the days leading
 * up to" a graded deliverable. So a study block is content-grounded only if the plan actually
 * contains a deliverable (DEADLINE / FINALS / REGULAR) it could be preparing for: one falling
 * within [0, [ANCHOR_WINDOW_DAYS]] days **after** the block. A study block that prepares for
 * nothing real is a confabulation — "events generated from a document that does not exist" — and
 * is dropped. In particular, a plan with no deliverables at all loses every study block.
 *
 * Deliverable events themselves are always kept here; their faithfulness to the source is handled
 * by year-grounding ([GeminiResponseParser.filterToSourceYears]) and the critic.
 */
object StudyPlanGrounder {
    /** Maximum lead time, in days, a study block may sit ahead of the deliverable it prepares for. */
    const val ANCHOR_WINDOW_DAYS = 30

    private val DELIVERABLE_CATEGORIES = setOf(
        AcademicCategory.DEADLINE,
        AcademicCategory.FINALS,
        AcademicCategory.REGULAR,
    )

    data class Result(val grounded: List<Event>, val droppedOrphanStudyBlocks: Int)

    fun ground(events: List<Event>): Result {
        val deliverableDates = events
            .filter { it.category in DELIVERABLE_CATEGORIES }
            .map { it.date }

        val grounded = events.filter { event ->
            if (event.category != AcademicCategory.STUDY_BLOCK) return@filter true
            deliverableDates.any { deliverable ->
                val gap = event.date.daysUntil(deliverable)
                gap in 0..ANCHOR_WINDOW_DAYS
            }
        }
        return Result(grounded, events.size - grounded.size)
    }
}
