package com.borinquenterrier.cef

/**
 * Splits a conversation's unsummarized messages into a verbatim tail (fits within a token budget)
 * and an older batch to fold into the rolling summary (design 2.1, Part B). Replaces the naive
 * `takeLast(MAX_HISTORY_TURNS)` cut, which silently dropped context instead of preserving it.
 */
object ChatCompactionPlanner {

    data class Plan(
        /** Most recent messages, oldest-first, that fit inside the history budget verbatim. */
        val verbatimTail: List<ChatMessage>,
        /** Older messages (oldest-first) that must be folded into the summary this turn. */
        val turnsToFold: List<ChatMessage>
    )

    /**
     * [messages] must be oldest-first and already exclude anything folded into the summary in a
     * previous compaction pass. Walks backward from the most recent message, keeping whatever
     * fits [historyTokenBudget]; everything older becomes [Plan.turnsToFold]. The latest turn is
     * always kept verbatim, even if it alone exceeds the budget, so the model always sees the
     * immediate question in context.
     */
    fun plan(messages: List<ChatMessage>, historyTokenBudget: Int): Plan {
        if (messages.isEmpty()) return Plan(emptyList(), emptyList())

        var used = 0
        var keepCount = 0
        for (message in messages.asReversed()) {
            val cost = TokenEstimator.estimate(message.content)
            if (used + cost > historyTokenBudget && keepCount > 0) break
            used += cost
            keepCount++
        }

        val splitIndex = messages.size - keepCount
        return Plan(
            verbatimTail = messages.subList(splitIndex, messages.size).toList(),
            turnsToFold = messages.subList(0, splitIndex).toList()
        )
    }
}
