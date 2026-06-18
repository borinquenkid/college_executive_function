package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.LocalDate

class ActiveSemesterDetectorTest : FunSpec({

    // --- hasMultipleSemestersForSameYear ---

    test("returns true for source with Spring and Summer in the same year") {
        val source = "Spring 2026 begins January 20. Summer 2026 begins May 18."
        ActiveSemesterDetector.hasMultipleSemestersForSameYear(source) shouldBe true
    }

    test("returns true for source with all three semesters in same year") {
        val source = "Spring 2026 (Jan–May). Summer 2026 (May–Aug). Fall 2026 (Aug–Dec)."
        ActiveSemesterDetector.hasMultipleSemestersForSameYear(source) shouldBe true
    }

    test("returns false for single-semester source") {
        val source = "ENG 101 Summer 2026 — course syllabus and schedule."
        ActiveSemesterDetector.hasMultipleSemestersForSameYear(source) shouldBe false
    }

    test("returns false when semesters span different years") {
        val source = "Fall 2025 through Spring 2026 academic year."
        ActiveSemesterDetector.hasMultipleSemestersForSameYear(source) shouldBe false
    }

    test("returns false for source with no semester keywords") {
        val source = "Academic calendar for 2026. Important institutional dates."
        ActiveSemesterDetector.hasMultipleSemestersForSameYear(source) shouldBe false
    }

    test("is case-insensitive for semester names") {
        val source = "SPRING 2026 and FALL 2026 schedule."
        ActiveSemesterDetector.hasMultipleSemestersForSameYear(source) shouldBe true
    }

    test("ignores bare semester keywords without a year") {
        val source = "Spring break is April 15. The fall semester is rigorous."
        ActiveSemesterDetector.hasMultipleSemestersForSameYear(source) shouldBe false
    }

    // --- detect ---

    test("returns Summer semester when today is in June") {
        val source = "Spring 2026. Summer 2026. Fall 2026."
        val result = ActiveSemesterDetector.detect(source, LocalDate(2026, 6, 17))
        result shouldNotBe null
        result!!.label shouldBe "Summer 2026"
    }

    test("returns Spring semester when today is in March") {
        val source = "Spring 2026. Summer 2026. Fall 2026."
        val result = ActiveSemesterDetector.detect(source, LocalDate(2026, 3, 1))
        result!!.label shouldBe "Spring 2026"
    }

    test("returns Fall semester when today is in October") {
        val source = "Spring 2026. Summer 2026. Fall 2026."
        val result = ActiveSemesterDetector.detect(source, LocalDate(2026, 10, 1))
        result!!.label shouldBe "Fall 2026"
    }

    test("returns null when source has no year references") {
        val result = ActiveSemesterDetector.detect("No dates mentioned.", LocalDate(2026, 6, 17))
        result.shouldBeNull()
    }

    test("semester range contains its boundary dates") {
        // today = June 1 is unambiguously Summer (past the Spring end of May 31)
        val source = "Summer 2026."
        val range = ActiveSemesterDetector.detect(source, LocalDate(2026, 6, 1))!!
        range.label shouldBe "Summer 2026"
        (LocalDate(2026, 5, 1) in range) shouldBe true   // start boundary
        (LocalDate(2026, 8, 31) in range) shouldBe true  // end boundary
    }

    test("semester range excludes dates outside its bounds") {
        val source = "Summer 2026."
        val range = ActiveSemesterDetector.detect(source, LocalDate(2026, 6, 1))!!
        (LocalDate(2026, 4, 30) in range) shouldBe false
        (LocalDate(2026, 9, 1) in range) shouldBe false
    }

    test("correctly labels semester ranges") {
        val spring = SemesterRange(LocalDate(2026, 1, 1), LocalDate(2026, 5, 31))
        val summer = SemesterRange(LocalDate(2026, 5, 1), LocalDate(2026, 8, 31))
        val fall   = SemesterRange(LocalDate(2026, 8, 1), LocalDate(2026, 12, 31))
        spring.label shouldBe "Spring 2026"
        summer.label shouldBe "Summer 2026"
        fall.label   shouldBe "Fall 2026"
    }
})
