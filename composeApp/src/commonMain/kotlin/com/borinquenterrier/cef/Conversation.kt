package com.borinquenterrier.cef

/**
 * Per-conversation source pin (design 2.1, Part A). A conversation either ranks across every source
 * ([All]) or is locked to a single [Source]. Serialized to the `sourceScope` column as `ALL` or
 * `SOURCE:<sourceId>`.
 */
sealed interface ChatSourceScope {
    fun serialize(): String

    object All : ChatSourceScope {
        override fun serialize(): String = ALL_TOKEN
    }

    data class Source(val sourceId: String) : ChatSourceScope {
        override fun serialize(): String = SOURCE_PREFIX + sourceId
    }

    companion object {
        private const val ALL_TOKEN = "ALL"
        private const val SOURCE_PREFIX = "SOURCE:"

        /**
         * Lenient parse for values read back from storage: anything that is not a well-formed
         * `SOURCE:<id>` (including a blank/legacy value) falls back to [All] rather than throwing,
         * so a stray row can never crash conversation hydration.
         */
        fun parse(value: String): ChatSourceScope {
            if (!value.startsWith(SOURCE_PREFIX)) return All
            val id = value.removePrefix(SOURCE_PREFIX)
            return if (id.isEmpty()) All else Source(id)
        }
    }
}

/**
 * A named chat conversation (design 2.1, Part A). Persisted as `ConversationEntity`. Ids are
 * deterministic — the codebase learned the "random ids -> duplicate rows" lesson in
 * EventGenerationService, so [create] derives the id from the creation timestamp rather than a
 * random UUID.
 */
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceScope: ChatSourceScope = ChatSourceScope.All,
    val summary: String? = null,
    /**
     * Id of the newest message already folded into [summary] (design 2.1, Part B), or null if
     * nothing has been folded yet. An id — not a timestamp — so the boundary is a positional
     * lookup in the always-fully-ordered message list rather than a value comparison that two
     * same-millisecond messages could straddle incorrectly.
     */
    val summarizedThroughMessageId: String? = null
) {
    companion object {
        const val DEFAULT_TITLE = "New chat"

        /** Title shown for the implicit conversation that predates the management UI. */
        const val DEFAULT_CONVERSATION_TITLE = "Chat"

        /**
         * Builds a conversation with a deterministic, timestamp-derived id. [createdAt] (epoch
         * millis) is unique per creation, so the id is unique without a random source.
         */
        fun create(
            createdAt: Long,
            title: String = DEFAULT_TITLE,
            sourceScope: ChatSourceScope = ChatSourceScope.All
        ): Conversation = Conversation(
            id = ContentHasher.hashString("conversation|$createdAt|$title"),
            title = title,
            createdAt = createdAt,
            updatedAt = createdAt,
            sourceScope = sourceScope
        )
    }
}
