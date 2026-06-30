package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate

/**
 * End-to-end wiring: GroundingGuardAIService.generateStudyPlan must apply the deterministic
 * content grounding (StudyPlanGrounder) on top of year-grounding, dropping orphan study blocks.
 */
class GroundingGuardStudyPlanTest : FunSpec({

    fun day(category: AcademicCategory, date: String, title: String = category.name) =
        DayEvent(title = title, source = EventSource.AI_GENERATED, category = category, date = LocalDate.parse(date))

    test("drops an orphan STUDY_BLOCK while keeping the anchored one and the deliverable") {
        val deadline = day(AcademicCategory.DEADLINE, "2026-07-15", "Essay due")
        val anchored = day(AcademicCategory.STUDY_BLOCK, "2026-07-12", "Study for essay")
        val orphan = day(AcademicCategory.STUDY_BLOCK, "2026-09-01", "Study for nothing")

        val delegate = mockk<AIService>(relaxed = true)
        coEvery { delegate.generateStudyPlan(any(), any(), any()) } returns listOf(deadline, anchored, orphan)

        val guard = GroundingGuardAIService(delegate)
        // Source references the deadline's date (so date-grounding keeps it); the orphan study
        // block must still be dropped by anchor grounding.
        val result = guard.generateStudyPlan("Fall 2026 schedule. Essay due July 15, 2026.", "", StudyPreferences())

        result.shouldContainExactlyInAnyOrder(deadline, anchored)
        result.shouldNotContain(orphan)
    }

    test("drops every STUDY_BLOCK when the plan has no deliverable to anchor to") {
        val plan = listOf(
            day(AcademicCategory.STUDY_BLOCK, "2026-07-10", "Study A"),
            day(AcademicCategory.STUDY_BLOCK, "2026-07-12", "Study B"),
        )
        val delegate = mockk<AIService>(relaxed = true)
        coEvery { delegate.generateStudyPlan(any(), any(), any()) } returns plan

        val result = GroundingGuardAIService(delegate)
            .generateStudyPlan("Syllabus 2026, no concrete deadlines.", "", StudyPreferences())

        result.shouldContainExactlyInAnyOrder() // empty
    }
})
