package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.plusOrMinus
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

    // Exact-score tests: the ordering-only tests above let arithmetic mutants survive (T9 pilot).
    // Expected values hand-computed from BM25 with K1=1.5, B=0.75 and +1-smoothed IDF.
    test("scores a rare term in an average-length document at exactly ln(2)") {
        // n=2, term in 1 doc -> idf = ln(1 + 1.5/1.5) = ln 2; doc length == avg so the norm factor is 1.
        val ranked = Bm25Ranker.rank("exam", listOf("exam essay", "essay essay"))

        ranked.first().index shouldBe 0
        ranked.first().score shouldBe (0.6931471805599453 plusOrMinus 1e-9)
        ranked[1].score shouldBe 0.0
    }

    test("length normalization produces exact scores for short and long documents") {
        // n=2, term in both -> idf = ln 1.2; lengths 1 and 4, avg 2.5.
        val ranked = Bm25Ranker.rank("exam", listOf("exam", "exam alpha beta gamma"))

        ranked[0].index shouldBe 0
        ranked[0].score shouldBe (0.24975555725199255 plusOrMinus 1e-9)
        ranked[1].index shouldBe 1
        ranked[1].score shouldBe (0.14356028094012174 plusOrMinus 1e-9)
    }

    test("keeps words of exactly 3 characters (boundary of the minimum word length)") {
        val ranked = Bm25Ranker.rank("cat", listOf("the cat sat", "dogs run"))

        ranked.first().index shouldBe 0
        ranked.first().score shouldBeGreaterThan 0.0
    }

    test("scores 0.0 and does not divide by zero when every document tokenizes to nothing") {
        val ranked = Bm25Ranker.rank("exam", listOf("a b", "of on"))

        ranked.map { it.score } shouldBe listOf(0.0, 0.0)
    }
})
