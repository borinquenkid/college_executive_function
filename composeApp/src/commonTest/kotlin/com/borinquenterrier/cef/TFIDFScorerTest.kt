package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.mockk

class TFIDFScorerTest : FunSpec({
    val scorer = TFIDFScorer()

    fun mockDoc(): Pair<SourceItem, SourceFragment> = mockk<SourceItem>() to mockk<SourceFragment>()

    test("scoreDocuments returns list same size as input documents") {
        val docs = listOf(mockDoc(), mockDoc())
        val words: List<List<String>> = listOf(listOf("text"), listOf("more"))
        val queryTerms: Set<String> = setOf("text", "more")
        val df: Map<String, Int> = mapOf("text" to 1, "more" to 1)

        val result = scorer.scoreDocuments(docs, words, queryTerms, df)

        result shouldHaveSize 2
    }

    test("scoreDocuments assigns zero score to empty documents") {
        val docs = listOf(mockDoc(), mockDoc())
        val words: List<List<String>> = listOf(emptyList(), listOf("word"))
        val queryTerms: Set<String> = setOf("word")
        val df: Map<String, Int> = mapOf("word" to 1)

        val result = scorer.scoreDocuments(docs, words, queryTerms, df)

        result[0].second shouldBe 0.0
    }

    test("scoreDocuments scores relevant terms higher than irrelevant") {
        val docs = listOf(mockDoc(), mockDoc())
        val words: List<List<String>> = listOf(listOf("learning", "learning"), listOf("unrelated"))
        val queryTerms: Set<String> = setOf("learning", "unrelated")
        val df: Map<String, Int> = mapOf("learning" to 1, "unrelated" to 2)

        val result = scorer.scoreDocuments(docs, words, queryTerms, df)

        result[0].second shouldBeGreaterThan result[1].second
    }

    test("scoreDocuments assigns zero to documents with no query terms") {
        val docs = listOf(mockDoc())
        val words: List<List<String>> = listOf(listOf("python", "java"))
        val queryTerms: Set<String> = setOf("cpp", "rust")
        val df: Map<String, Int> = mapOf("cpp" to 0, "rust" to 0)

        val result = scorer.scoreDocuments(docs, words, queryTerms, df)

        result[0].second shouldBe 0.0
    }

    test("scoreDocuments weights term frequency correctly") {
        val docs = listOf(mockDoc(), mockDoc())
        val words: List<List<String>> = listOf(listOf("python"), listOf("python", "python", "python"))
        val queryTerms: Set<String> = setOf("python")
        val df: Map<String, Int> = mapOf("python" to 2)

        val result = scorer.scoreDocuments(docs, words, queryTerms, df)

        result[1].second shouldBeGreaterThan result[0].second
    }

    test("scoreDocuments weights IDF correctly for rare terms") {
        val docs = listOf(mockDoc(), mockDoc())
        val words: List<List<String>> = listOf(listOf("rare"), listOf("common"))
        val queryTerms: Set<String> = setOf("rare", "common")
        val df: Map<String, Int> = mapOf("rare" to 1, "common" to 10)

        val result = scorer.scoreDocuments(docs, words, queryTerms, df)

        result[0].second shouldBeGreaterThan result[1].second
    }

    test("scoreDocuments handles normalized term frequency correctly") {
        val docs = listOf(mockDoc(), mockDoc())
        val words: List<List<String>> = listOf(listOf("text", "short"), listOf("text", "text", "text", "text", "text"))
        val queryTerms: Set<String> = setOf("text")
        val df: Map<String, Int> = mapOf("text" to 2)

        val result = scorer.scoreDocuments(docs, words, queryTerms, df)

        result[1].second shouldBeGreaterThan result[0].second
    }
})
