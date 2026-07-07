package com.borinquenterrier.cef

/** Author of a chat turn. Replaces the former stringly-typed `author` field. */
enum class ChatRole {
    USER,
    AI;

    companion object {
        /**
         * Lenient parse for values read back from storage: unknown or legacy role strings fall
         * back to [AI] rather than throwing, so a stray row can never crash history hydration.
         */
        fun fromStorage(value: String): ChatRole = entries.firstOrNull { it.name == value } ?: AI
    }
}

/**
 * A single chat turn. Persisted per conversation (design 2.1). Ids are deterministic — the codebase
 * learned the "random ids -> duplicate rows" lesson in EventGenerationService, so [create] derives
 * the id from the message content rather than a random UUID.
 */
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: ChatRole,
    val content: String,
    val createdAt: Long
) {
    /** Short label shown in the message bubble header. */
    val displayLabel: String get() = if (role == ChatRole.USER) "User" else "AI"

    companion object {
        const val DEFAULT_CONVERSATION_ID = "default"

        /** Id of the non-persisted greeting placeholder shown as a conversation's empty state. */
        const val GREETING_ID = "greeting"

        /** Builds a message with a deterministic, content-derived id. */
        fun create(
            content: String,
            role: ChatRole,
            createdAt: Long,
            conversationId: String = DEFAULT_CONVERSATION_ID
        ): ChatMessage = ChatMessage(
            id = ContentHasher.hashString("$conversationId|${role.name}|$createdAt|$content"),
            conversationId = conversationId,
            role = role,
            content = content,
            createdAt = createdAt
        )

        /** In-memory greeting shown as the empty state of a fresh conversation; never persisted. */
        fun greeting(conversationId: String = DEFAULT_CONVERSATION_ID): ChatMessage = ChatMessage(
            id = GREETING_ID,
            conversationId = conversationId,
            role = ChatRole.AI,
            content = "Hello! How can I help you today?",
            createdAt = 0L
        )
    }
}
