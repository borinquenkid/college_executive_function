package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.LocalDate

/**
 * The two-channel split: grounded events go one way, ungrounded deliverables go to
 * [StudyPlanResult.needsResolution] carrying the source snippet the date-picker dialog will show.
 */
class StudyPlanResolverTest : FunSpec({

    fun day(category: AcademicCategory, date: String, title: String) =
        DayEvent(title = title, source = EventSource.AI_GENERATED, category = category, date = LocalDate.parse(date))

    test("an ungrounded deliverable whose title appears in the source carries a snippet for the picker") {
        val source = "Course 2026. The witness protection essay is due sometime around midterms."
        val phantom = day(AcademicCategory.DEADLINE, "2026-09-22", "Witness protection essay")

        val result = StudyPlanResolver.resolve(listOf(phantom), source)

        result.grounded shouldBe emptyList()
        result.needsResolution.size shouldBe 1
        result.needsResolution[0].event shouldBe phantom
        result.needsResolution[0].sourceSnippet.shouldNotBeNull().shouldContain("witness protection")
    }

    test("a fabricated deliverable with no source overlap has a null snippet") {
        val source = "Course 2026. Essay due July 15, 2026."
        val invented = day(AcademicCategory.DEADLINE, "2026-12-01", "Quantum chromodynamics problem set")

        val result = StudyPlanResolver.resolve(listOf(invented), source)

        result.needsResolution.size shouldBe 1
        result.needsResolution[0].sourceSnippet shouldBe null
    }

    test("grounded deliverables and their anchored blocks stay in the grounded channel") {
        val source = "Essay due July 15, 2026."
        val essay = day(AcademicCategory.DEADLINE, "2026-07-15", "Essay")
        val block = day(AcademicCategory.STUDY_BLOCK, "2026-07-13", "Study for essay")

        val result = StudyPlanResolver.resolve(listOf(essay, block), source)

        result.grounded.map { it.title }.toSet() shouldBe setOf("Essay", "Study for essay")
        result.needsResolution shouldBe emptyList()
    }
})
