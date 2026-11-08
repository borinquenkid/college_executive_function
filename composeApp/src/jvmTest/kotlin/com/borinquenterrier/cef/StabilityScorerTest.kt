package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class StabilityScorerTest : FunSpec({
    fun ev(date: String, title: String) = StabilityEvent(date, title)

    test("identical runs are stable on dates and events") {
        val run = listOf(ev("2026-09-01", "Exam 1"), ev("2026-10-01", "Quiz"))
        val s = StabilityScorer.score(listOf(run, run, run))
        s.dateSetStable shouldBe true
        s.eventSetStable shouldBe true
        s.distinctDateSets shouldBe 1
        s.unstableDates shouldBe emptyList()
    }

    test("title wording drift on the same date breaks event stability but not date stability") {
        val s = StabilityScorer.score(
            listOf(listOf(ev("2026-09-30", "Midterm 1")), listOf(ev("2026-09-30", "Exam 1")))
        )
        s.dateSetStable shouldBe true
        s.eventSetStable shouldBe false
    }

    test("case and punctuation do not count as drift") {
        val s = StabilityScorer.score(
            listOf(listOf(ev("2026-10-09", "Mid-term exam")), listOf(ev("2026-10-09", "MID TERM EXAM!")))
        )
        s.eventSetStable shouldBe true
    }

    test("a date present in only some runs is reported as unstable") {
        val s = StabilityScorer.score(
            listOf(
                listOf(ev("2026-09-01", "A"), ev("2026-09-02", "B")),
                listOf(ev("2026-09-01", "A")),
                listOf(ev("2026-09-01", "A"), ev("2026-09-02", "B"))
            )
        )
        s.dateSetStable shouldBe false
        s.distinctDateSets shouldBe 2
        s.unstableDates shouldBe listOf("2026-09-02")
        s.minEvents shouldBe 1
        s.maxEvents shouldBe 2
    }

    test("failed runs are excluded and fewer than two successes is not comparable") {
        val one = StabilityScorer.score(listOf(listOf(ev("2026-09-01", "A")), null, null))
        one.failedRuns shouldBe 2
        one.comparable shouldBe false
        one.dateSetStable shouldBe false
        val two = StabilityScorer.score(listOf(listOf(ev("2026-09-01", "A")), listOf(ev("2026-09-01", "A")), null))
        two.comparable shouldBe true
        two.dateSetStable shouldBe true
    }
})
