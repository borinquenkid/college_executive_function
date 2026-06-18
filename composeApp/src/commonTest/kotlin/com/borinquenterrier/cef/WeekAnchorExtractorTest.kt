package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class WeekAnchorExtractorTest : FunSpec({

    fun fragment(text: String, page: Int = 1) =
        SourceFragment(text = text, pageNumber = page, type = SourceType.TEXT)

    // ── no-op cases ────────────────────────────────────────────────────────────

    test("returns unchanged fragments when no week anchors exist anywhere") {
        val frags = listOf(
            fragment("Midterm on October 15. Final paper due November 20."),
            fragment("Homework 1 due Monday.")
        )
        val result = WeekAnchorExtractor.inject(frags)
        result shouldBe frags
        result.forEach { it.metadata shouldNotContainKey "weekAnchors" }
    }

    test("returns unchanged fragments when week anchors exist but no bare Week N references") {
        val frags = listOf(
            fragment("Week 1: June 8–14, 2026: Introduction"),
            fragment("Week 2: June 15–21, 2026: Deep dive")
        )
        val result = WeekAnchorExtractor.inject(frags)
        result.forEach { it.metadata shouldNotContainKey "weekAnchors" }
    }

    // ── injection cases ────────────────────────────────────────────────────────

    test("injects weekAnchors into page with bare Week N reference when anchor is on another page") {
        val summaryPage = fragment("Issue Brief #1 — Due Week 4\nIssue Brief #2 — Due Week 6", page = 1)
        val week4Page = fragment("Week 4: June 29–July 5, 2026\nWednesday: Issue Brief #1 due", page = 4)
        val week6Page = fragment("Week 6: July 13–19, 2026\nWednesday: Issue Brief #2 due", page = 6)

        val result = WeekAnchorExtractor.inject(listOf(summaryPage, week4Page, week6Page))

        // Page 1 should get the anchor table because it references Week 4 and Week 6 but has no anchors
        result[0].metadata shouldContainKey "weekAnchors"
        result[0].metadata["weekAnchors"]!! shouldContain "Week 4: June 29"
        result[0].metadata["weekAnchors"]!! shouldContain "Week 6: July 13"

        // Pages 4 and 6 already define their own anchors — should NOT get injected
        result[1].metadata shouldNotContainKey "weekAnchors"
        result[2].metadata shouldNotContainKey "weekAnchors"
    }

    test("anchor table is sorted by week number") {
        val p1 = fragment("Due Week 3\nDue Week 1", page = 1)
        val p2 = fragment("Week 3: June 22–28, 2026", page = 2)
        val p3 = fragment("Week 1: June 8–14, 2026", page = 3)

        val result = WeekAnchorExtractor.inject(listOf(p1, p2, p3))
        val anchors = result[0].metadata["weekAnchors"]!!
        val week1Pos = anchors.indexOf("Week 1")
        val week3Pos = anchors.indexOf("Week 3")
        (week1Pos < week3Pos) shouldBe true
    }

    test("handles en-dash and hyphen date separators") {
        val p1 = fragment("Assignment due Week 2", page = 1)
        val p2 = fragment("Week 2: June 15-21, 2026", page = 2)     // hyphen
        val p3 = fragment("Week 3: June 22–28, 2026", page = 3)     // en-dash

        val result = WeekAnchorExtractor.inject(listOf(p1, p2, p3))
        result[0].metadata["weekAnchors"]!! shouldContain "Week 2"
    }

    test("does not inject into a fragment that already contains all anchors it references") {
        val selfContained = fragment(
            "Week 4: June 29–July 5, 2026\nIssue Brief #1 due Wednesday of Week 4", page = 1
        )
        val result = WeekAnchorExtractor.inject(listOf(selfContained))
        result[0].metadata shouldNotContainKey "weekAnchors"
    }

    test("preserves existing metadata when injecting weekAnchors") {
        val p1 = fragment("Due Week 5", page = 1).copy(metadata = mapOf("source" to "stlcc"))
        val p2 = fragment("Week 5: July 6–12, 2026", page = 2)

        val result = WeekAnchorExtractor.inject(listOf(p1, p2))
        result[0].metadata shouldContainKey "weekAnchors"
        result[0].metadata shouldContainKey "source"
        result[0].metadata["source"] shouldBe "stlcc"
    }

    test("handles STLCC-style document: summary table on page 1 gets anchors from pages 1-8") {
        val summaryPage = fragment(
            """
            Issue Brief #1: Secrecy, Identity    50 points    Week 4
            Issue Brief #2: Ethics, Deception    75 points    Week 6
            Issue Brief #3: Connecting Systems   75 points    Week 7
            Final Paper                         100 points    Week 8
            """.trimIndent(), page = 1
        )
        val pages = (1..8).map { week ->
            val start = 8 + (week - 1) * 7
            fragment("Week $week: June ${start}–${start + 6}, 2026\nContent for week $week", page = week + 1)
        }

        val result = WeekAnchorExtractor.inject(listOf(summaryPage) + pages)

        // Summary page gets all anchors
        val anchors = result[0].metadata["weekAnchors"]!!
        anchors shouldContain "Week 4"
        anchors shouldContain "Week 6"
        anchors shouldContain "Week 7"
        anchors shouldContain "Week 8"

        // Individual week pages already have anchors — no injection needed
        pages.indices.forEach { i ->
            result[i + 1].metadata shouldNotContainKey "weekAnchors"
        }
    }
})
