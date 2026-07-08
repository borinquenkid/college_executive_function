package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class Bm25RankerTest : FunSpec({

    test("ranks the document containing a rare query term above ones that don't") {
        val documents = listOf(
            "The course meets on Mondays and Wednesdays for lecture.",
            "The essay on witness protection is due Wednesday.",
            "Office hours are held every Wednesday afternoon."
        )
        val ranked = Bm25Ranker.rank("witness protection essay", documents)

        ranked.first().index shouldBe 1
        ranked.first().score shouldBeGreaterThan ranked[1].score
    }

    test("gives zero score to documents sharing no terms with the query") {
        val documents = listOf("Course introduction and syllabus overview.")
        val ranked = Bm25Ranker.rank("quantum chromodynamics midterm", documents)

        ranked.single().score shouldBe 0.0
    }

    test("shorter document scores higher than a long one when both mention the query term equally often") {
        val shortDoc = "Final exam is on August 10."
        val padding = (1..60).joinToString(" ") { "filler$it" }
        val longDoc = "$shortDoc $padding" // same term frequency as shortDoc, just padded with unrelated words

        val ranked = Bm25Ranker.rank("final exam date", listOf(shortDoc, longDoc))

        ranked.first().index shouldBe 0
    }

    test("returns one entry per document, all scored 0.0, for a blank query") {
        val documents = listOf("Some content.", "More content.")
        val ranked = Bm25Ranker.rank("   ", documents)

        ranked.map { it.score } shouldBe listOf(0.0, 0.0)
    }

    test("returns an empty list for an empty document list") {
        Bm25Ranker.rank("anything", emptyList()) shouldBe emptyList()
    }

    test("ignores trivial short words (length < 3) when scoring") {
        val documents = listOf("A B of on it is content here.")
        val ranked = Bm25Ranker.rank("a b of on it is", documents)

        ranked.single().score shouldBe 0.0
    }
})
