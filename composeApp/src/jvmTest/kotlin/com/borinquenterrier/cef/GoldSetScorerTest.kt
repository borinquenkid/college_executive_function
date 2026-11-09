package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

class GoldSetScorerTest : FunSpec({

    fun gold(title: String, date: String) = GoldEvent(title, LocalDate.parse(date))
    fun ev(title: String, date: String): Event =
        DayEvent(title = title, source = EventSource.AI_GENERATED, date = LocalDate.parse(date))

    test("perfect extraction scores full recall and date accuracy") {
        val s = GoldSetScorer.score(listOf(gold("Exam 1", "2026-09-28")), listOf(ev("Exam 1", "2026-09-28")))

        s.matchedCount shouldBe 1
        s.dateCorrectCount shouldBe 1
        s.missing shouldHaveSize 0
    }

    test("a missing event is reported and lowers recall") {
        val s = GoldSetScorer.score(listOf(gold("Exam 1", "2026-09-28"), gold("Final", "2026-12-18")), listOf(ev("Exam 1", "2026-09-28")))

        s.matchedCount shouldBe 1
        s.missing.map { it.title } shouldBe listOf("Final")
    }

    test("a matched event on the wrong date counts as matched but not date-correct") {
        val s = GoldSetScorer.score(listOf(gold("Exam 1", "2026-09-28")), listOf(ev("Exam 1", "2026-09-29")))

        s.matchedCount shouldBe 1
        s.dateCorrectCount shouldBe 0
        s.dateMismatches shouldHaveSize 1
    }

    test("'Exam I' is not satisfied by 'Exam II' when the right event exists (substring trap)") {
        val expected = listOf(gold("Exam I", "2025-09-17"), gold("Exam II", "2025-10-08"))
        val extracted = listOf(ev("Exam II", "2025-10-08"), ev("Exam I", "2025-09-17"))

        val s = GoldSetScorer.score(expected, extracted)

        s.dateCorrectCount shouldBe 2
    }

    test("one extracted event cannot satisfy two expected events") {
        val expected = listOf(gold("Topic due", "2026-09-10"), gold("Topic due", "2026-11-05"))

        val s = GoldSetScorer.score(expected, listOf(ev("Topic due", "2026-09-10")))

        s.matchedCount shouldBe 1
        s.missing shouldHaveSize 1
    }

    test("title similarity tolerates inserted words but not different content words") {
        GoldSetScorer.titlesSimilar("Last day to drop without a grade", "Drop without a grade") shouldBe true
        GoldSetScorer.titlesSimilar("Drop without a grade", "Drop with a W") shouldBe false
        GoldSetScorer.titlesSimilar("", "Exam") shouldBe false
    }

    test("every checked-in gold file parses, points at an existing PDF, and has dated events") {
        val docs = GoldSet.load()

        docs.size shouldBeGreaterThanOrEqual 10
        docs.forEach { d ->
            GoldSet.pdfFile(d).exists() shouldBe true
            d.events.isNotEmpty() shouldBe true
        }
        docs.sumOf { it.events.size } shouldBeGreaterThanOrEqual 50
        docs.map { it.institution }.toSet() shouldBe setOf("missouri_st", "stlcc", "ut_austin")
    }

    test("forbiddenHits flags extracted events whose title matches a mustNotExtract entry") {
        val extracted = listOf(
            ev("Algebra Refresher - Day 1", "2026-01-20"),
            ev("Midterm Exam 1", "2026-02-17"),
            ev("Algebra Refresher", "2026-01-21")
        )
        val hits = GoldSetScorer.forbiddenHits(listOf("Algebra Refresher", "Trigonometry Refresher"), extracted)
        hits.size shouldBe 2
        hits.all { it.contains("Algebra Refresher") } shouldBe true
        GoldSetScorer.forbiddenHits(emptyList(), extracted) shouldBe emptyList()
    }

    test("a gold file's mustNotExtract list parses; absent means empty") {
        val withNeg = GoldSet.parse(
            "x",
            """{"pdf":"a.pdf","institution":"i","events":[{"title":"Exam","date":"2026-01-01"}],"mustNotExtract":["Tutoring"]}"""
        )
        withNeg.mustNotExtract shouldBe listOf("Tutoring")
        GoldSet.parse("y", """{"pdf":"a.pdf","institution":"i","events":[]}""").mustNotExtract shouldBe emptyList()
    }
})
