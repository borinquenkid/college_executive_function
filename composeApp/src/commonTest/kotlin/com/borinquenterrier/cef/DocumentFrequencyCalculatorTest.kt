package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly

class DocumentFrequencyCalculatorTest : FunSpec({
    val calculator = DocumentFrequencyCalculator()

    test("calculateDocumentFrequencies counts documents containing each term") {
        val documents = listOf(
            listOf("machine", "learning", "algorithms"),
            listOf("deep", "learning", "networks"),
            listOf("machine", "learning", "classification")
        )
        val queryTerms = setOf("machine", "learning", "deep")

        val result = calculator.calculateDocumentFrequencies(queryTerms, documents)

        result shouldContainExactly mapOf(
            "machine" to 2,
            "learning" to 3,
            "deep" to 1
        )
    }

    test("calculateDocumentFrequencies returns zero for terms not in any document") {
        val documents = listOf(
            listOf("apple", "orange"),
            listOf("banana", "grape")
        )
        val queryTerms = setOf("apple", "missing", "grape")

        val result = calculator.calculateDocumentFrequencies(queryTerms, documents)

        result shouldContainExactly mapOf(
            "apple" to 1,
            "missing" to 0,
            "grape" to 1
        )
    }

    test("calculateDocumentFrequencies handles empty documents") {
        val documents = listOf(
            emptyList<String>(),
            listOf("word"),
            emptyList<String>()
        )
        val queryTerms = setOf("word", "missing")

        val result = calculator.calculateDocumentFrequencies(queryTerms, documents)

        result shouldContainExactly mapOf(
            "word" to 1,
            "missing" to 0
        )
    }

    test("calculateDocumentFrequencies counts duplicate terms once per document") {
        val documents = listOf(
            listOf("python", "python", "code"),
            listOf("python", "java"),
            listOf("code", "code", "code")
        )
        val queryTerms = setOf("python", "code")

        val result = calculator.calculateDocumentFrequencies(queryTerms, documents)

        result shouldContainExactly mapOf(
            "python" to 2,
            "code" to 2
        )
    }
})
