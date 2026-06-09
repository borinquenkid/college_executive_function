package com.borinquenterrier.cef

class DocumentFrequencyCalculator {
    fun calculateDocumentFrequencies(
        queryTerms: Set<String>,
        documentWords: List<List<String>>
    ): Map<String, Int> {
        return queryTerms.associateWith { term ->
            documentWords.count { words -> term in words }
        }
    }
}
