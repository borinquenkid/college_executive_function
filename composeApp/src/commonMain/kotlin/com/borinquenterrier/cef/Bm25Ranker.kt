package com.borinquenterrier.cef

import kotlin.math.ln

/** [index] into the original documents list passed to [Bm25Ranker.rank], with its BM25 [score]. */
data class RankedDocument(val index: Int, val score: Double)

/**
 * Ranks a list of documents against a query using BM25 (Robertson/Sparck-Jones), the standard
 * lexical relevance scoring function used by search engines before/instead of embeddings.
 * Rewards documents containing rarer, more discriminative query terms (IDF) and normalizes for
 * document length so a short, on-topic passage doesn't lose to a long one that merely repeats a
 * term more often.
 */
object Bm25Ranker {
    private const val K1 = 1.5
    private const val B = 0.75
    private val WORD = Regex("""[a-z0-9]+""")
    private const val MIN_WORD_LEN = 3

    private fun tokenize(text: String): List<String> =
        WORD.findAll(text.lowercase()).map { it.value }.filter { it.length >= MIN_WORD_LEN }.toList()

    /**
     * Returns one [RankedDocument] per entry in [documents], sorted by descending BM25 score
     * against [query]. A score of 0.0 means no query term (after tokenization) appeared in that
     * document at all.
     */
    fun rank(query: String, documents: List<String>): List<RankedDocument> {
        if (documents.isEmpty()) return emptyList()

        val queryTerms = tokenize(query).toSet()
        if (queryTerms.isEmpty()) return documents.indices.map { RankedDocument(it, 0.0) }

        val docTokens = documents.map { tokenize(it) }
        val docLengths = docTokens.map { it.size }
        val avgDocLength = docLengths.average().takeIf { it > 0.0 } ?: 1.0
        val n = documents.size

        // +1 smoothing keeps IDF non-negative even when a term appears in every document —
        // important for small corpora (a handful of sentences/paragraphs), unlike classic
        // Robertson-Sparck-Jones IDF which can go negative there.
        val idfByTerm = queryTerms.associateWith { term ->
            val nq = docTokens.count { term in it }
            ln(1.0 + (n - nq + 0.5) / (nq + 0.5))
        }

        return docTokens.mapIndexed { i, tokens ->
            val termFreq = tokens.groupingBy { it }.eachCount()
            val docLen = docLengths[i]
            val score = queryTerms.sumOf { term ->
                val f = termFreq[term] ?: 0
                if (f == 0) 0.0
                else idfByTerm.getValue(term) * (f * (K1 + 1)) / (f + K1 * (1 - B + B * docLen / avgDocLength))
            }
            RankedDocument(i, score)
        }.sortedByDescending { it.score }
    }
}
