package com.borinquenterrier.cef

import kotlin.math.ln

class TFIDFScorer {
    fun scoreDocuments(
        documents: List<Pair<SourceItem, SourceFragment>>,
        documentWords: List<List<String>>,
        queryTerms: Set<String>,
        documentFrequencies: Map<String, Int>
    ): List<Pair<Pair<SourceItem, SourceFragment>, Double>> {
        val totalDocuments = documents.size.toDouble()

        return documents.mapIndexed { index, doc ->
            val words = documentWords[index]
            val score = if (words.isEmpty()) {
                0.0
            } else {
                calculateTFIDF(words, queryTerms, documentFrequencies, totalDocuments)
            }
            doc to score
        }
    }

    private fun calculateTFIDF(
        words: List<String>,
        queryTerms: Set<String>,
        documentFrequencies: Map<String, Int>,
        totalDocuments: Double
    ): Double {
        var score = 0.0
        val termFrequencies = words.groupingBy { it }.eachCount()

        for (term in queryTerms) {
            val tf = termFrequencies[term] ?: 0
            if (tf > 0) {
                val df = documentFrequencies[term] ?: 0
                val idf = ln(1.0 + totalDocuments / (1.0 + df))
                val normalizedTF = tf.toDouble() / words.size
                score += normalizedTF * idf
            }
        }

        return score
    }
}
