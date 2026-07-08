package com.borinquenterrier.cef

/**
 * Per-turn token budget allocation for the chat prompt (design 2.1, Part B). Computes how many
 * tokens remain for verbatim recent history once the context window, reserved model output,
 * fixed prompt scaffolding, source blocks, and the rolling summary are all accounted for.
 */
object ChatBudgetAllocator {
    /** Headroom reserved for the model's answer; chat responses are rarely huge. */
    const val RESERVED_OUTPUT_TOKENS = 2_048

    /** Rough estimate of the fixed MEMORANDUM BRIEF scaffolding + guardrail text in every prompt. */
    const val SYSTEM_INSTRUCTIONS_TOKENS = 400

    /**
     * Tokens available for verbatim history, given [contextWindow] and the other reserved/known
     * costs. Never negative — a pathological input still yields a valid (zero) budget rather than
     * a negative one.
     */
    fun historyBudget(
        contextWindow: Int,
        sourceBlocksTokens: Int,
        summaryTokens: Int,
        questionTokens: Int
    ): Int {
        val reserved = RESERVED_OUTPUT_TOKENS + SYSTEM_INSTRUCTIONS_TOKENS
        val budget = contextWindow - reserved - sourceBlocksTokens - summaryTokens - questionTokens
        return budget.coerceAtLeast(0)
    }
}
