package com.borinquenterrier.cef

import kotlin.math.ln

class FragmentRanker {
    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "but", "if", "then", "else", "when",
        "at", "by", "for", "from", "in", "into", "of", "off", "on", "onto",
        "out", "over", "to", "up", "with", "is", "are", "was", "were", "be",
        "been", "being", "have", "has", "had", "do", "does", "did", "this",
        "that", "these", "those", "they", "them", "their", "he", "him", "his",
        "she", "her", "hers", "it", "its", "you", "your", "yours", "we", "us", "our"
    )

    fun rankFragments(
        sources: List<SourceItem>,
        question: String,
        topK: Int = 15
    ): List<Pair<SourceItem, SourceFragment>> {
        val allPairs = sources.flatMap { source ->
            source.fragments.map { fragment -> source to fragment }
        }

        if (allPairs.isEmpty()) {
            return emptyList()
        }

        val queryTerms = extractQueryTerms(question)

        if (queryTerms.isEmpty()) {
            return allPairs.take(topK)
        }

        val pairWords = allPairs.map { (_, fragment) ->
            fragment.text.lowercase().split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotEmpty() }
        }

        val dfMap = calculateDocumentFrequencies(queryTerms, pairWords)
        val scoredPairs = scoreFragments(allPairs, pairWords, queryTerms, dfMap)

        return scoredPairs
            .sortedWith(compareByDescending<Pair<Pair<SourceItem, SourceFragment>, Double>> { it.second }
                .thenBy { allPairs.indexOf(it.first) })
            .map { it.first }
            .take(topK)
    }

    private fun extractQueryTerms(question: String): Set<String> {
        return question.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 && it !in stopWords }
            .toSet()
    }

    private fun calculateDocumentFrequencies(
        queryTerms: Set<String>,
        pairWords: List<List<String>>
    ): Map<String, Int> {
        return queryTerms.associateWith { term ->
            pairWords.count { words -> term in words }
        }
    }

    private fun scoreFragments(
        allPairs: List<Pair<SourceItem, SourceFragment>>,
        pairWords: List<List<String>>,
        queryTerms: Set<String>,
        dfMap: Map<String, Int>
    ): List<Pair<Pair<SourceItem, SourceFragment>, Double>> {
        val totalDocuments = allPairs.size.toDouble()

        return allPairs.mapIndexed { index, pair ->
            val words = pairWords[index]
            if (words.isEmpty()) {
                pair to 0.0
            } else {
                var score = 0.0
                val tfMap = words.groupingBy { it }.eachCount()
                for (term in queryTerms) {
                    val tf = tfMap[term] ?: 0
                    if (tf > 0) {
                        val df = dfMap[term] ?: 0
                        val idf = ln(1.0 + totalDocuments / (1.0 + df))
                        val normalizedTf = tf.toDouble() / words.size.toDouble()
                        score += normalizedTf * idf
                    }
                }
                pair to score
            }
        }
    }
}
