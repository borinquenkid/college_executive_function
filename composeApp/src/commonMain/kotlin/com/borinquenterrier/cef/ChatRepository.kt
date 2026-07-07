package com.borinquenterrier.cef

/**
 * Persistence for chat conversations and their messages (design 2.1, Part A).
 * Implementations must keep DB access off the main thread.
 */
interface ChatRepository {
    /** Creates the conversation row if it does not already exist. Idempotent. */
    suspend fun ensureConversation(conversationId: String, title: String)

    /** Persists a single message, auto-creating its parent conversation row if needed. */
    suspend fun saveMessage(message: ChatMessage, tokenEstimate: Long = 0L)

    /** Returns the conversation's messages ordered oldest-first. */
    suspend fun getMessages(conversationId: String): List<ChatMessage>

    /** Removes every message in the conversation (the conversation row itself is left intact). */
    suspend fun deleteMessages(conversationId: String)
}
