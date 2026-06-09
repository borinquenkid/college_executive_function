package com.borinquenterrier.cef

class FragmentRanker(
    private val termNormalizer: TermNormalizer = TermNormalizer(),
    private val dfCalculator: DocumentFrequencyCalculator = DocumentFrequencyCalculator(),
    private val tfIdfScorer: TFIDFScorer = TFIDFScorer()
) {
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

        val queryTerms = termNormalizer.extractQueryTerms(question)

        if (queryTerms.isEmpty()) {
            return allPairs.take(topK)
        }

        val documentWords = allPairs.map { (_, fragment) ->
            termNormalizer.tokenizeFragment(fragment.text)
        }

        val documentFrequencies = dfCalculator.calculateDocumentFrequencies(queryTerms, documentWords)
        val scoredPairs = tfIdfScorer.scoreDocuments(allPairs, documentWords, queryTerms, documentFrequencies)

        return scoredPairs
            .sortedWith(compareByDescending<Pair<Pair<SourceItem, SourceFragment>, Double>> { it.second }
                .thenBy { allPairs.indexOf(it.first) })
            .map { it.first }
            .take(topK)
    }
}
