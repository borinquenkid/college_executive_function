package com.borinquenterrier.cef

class TermNormalizer {
    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "but", "if", "then", "else", "when",
        "at", "by", "for", "from", "in", "into", "of", "off", "on", "onto",
        "out", "over", "to", "up", "with", "is", "are", "was", "were", "be",
        "been", "being", "have", "has", "had", "do", "does", "did", "this",
        "that", "these", "those", "they", "them", "their", "he", "him", "his",
        "she", "her", "hers", "it", "its", "you", "your", "yours", "we", "us", "our",
        "what", "which", "who", "whom", "whose", "where", "why", "how", "so"
    )

    fun extractQueryTerms(question: String): Set<String> {
        return question.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 && it !in stopWords }
            .toSet()
    }

    fun tokenizeFragment(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.isNotEmpty() }
    }
}
