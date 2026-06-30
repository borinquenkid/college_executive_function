package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import kotlinx.datetime.LocalDate

/**
 * Deterministic content grounding for study plans.
 *
 * A STUDY_BLOCK is, by definition, preparation for a graded deliverable. It is "grounded" only
 * if the plan actually contains a deliverable (DEADLINE / FINALS / REGULAR) that it could be
 * preparing for — i.e. one falling within a short forward window after the block. A study block
 * that prepares for nothing real is a confabulation ("events from a document that does not
 * exist") and is dropped. Deliverable events themselves are always kept here (their faithfulness
 * is handled by year-grounding + the critic).
 */
class StudyPlanGrounderTest : FunSpec({

    fun day(category: AcademicCategory, date: String, title: String = category.name) =
        DayEvent(title = title, source = EventSource.AI_GENERATED, category = category, date = LocalDate.parse(date))

    test("keeps a study block anchored to a deliverable within the forward window") {
        val block = day(AcademicCategory.STUDY_BLOCK, "2026-07-10")
        val deadline = day(AcademicCategory.DEADLINE, "2026-07-15", "Essay due")
        val result = StudyPlanGrounder.ground(listOf(block, deadline))
        result.grounded.shouldContainExactlyInAnyOrder(block, deadline)
        result.droppedOrphanStudyBlocks shouldBe 0
    }

    test("keeps a study block on the same day as its deliverable") {
        val block = day(AcademicCategory.STUDY_BLOCK, "2026-07-15")
        val finals = day(AcademicCategory.FINALS, "2026-07-15", "Final exam")
        StudyPlanGrounder.ground(listOf(block, finals)).grounded.shouldContainExactlyInAnyOrder(block, finals)
    }

    test("drops a study block whose only deliverable is in the past") {
        val deadline = day(AcademicCategory.DEADLINE, "2026-07-15")
        val orphan = day(AcademicCategory.STUDY_BLOCK, "2026-07-20") // deliverable already passed
        val result = StudyPlanGrounder.ground(listOf(deadline, orphan))
        result.grounded shouldBe listOf(deadline)
        result.droppedOrphanStudyBlocks shouldBe 1
    }

    test("drops a study block beyond the forward window") {
        val block = day(AcademicCategory.STUDY_BLOCK, "2026-07-01")
        val deadline = day(AcademicCategory.DEADLINE, "2026-09-01") // ~62 days out
        StudyPlanGrounder.ground(listOf(block, deadline)).grounded shouldBe listOf(deadline)
    }

    test("drops ALL study blocks when the plan has no deliverables (the confabulation case)") {
        val blocks = listOf(
            day(AcademicCategory.STUDY_BLOCK, "2026-07-10"),
            day(AcademicCategory.STUDY_BLOCK, "2026-07-12"),
        )
        val result = StudyPlanGrounder.ground(blocks)
        result.grounded shouldBe emptyList()
        result.droppedOrphanStudyBlocks shouldBe 2
    }

    test("never drops deliverable-category events") {
        val events = listOf(
            day(AcademicCategory.DEADLINE, "2026-07-15"),
            day(AcademicCategory.FINALS, "2026-08-01"),
            day(AcademicCategory.REGULAR, "2026-07-20"),
        )
        StudyPlanGrounder.ground(events).grounded.shouldContainExactlyInAnyOrder(*events.toTypedArray())
    }

    test("empty input yields empty output") {
        val result = StudyPlanGrounder.ground(emptyList())
        result.grounded shouldBe emptyList()
        result.droppedOrphanStudyBlocks shouldBe 0
    }
})
