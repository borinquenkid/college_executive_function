package com.borinquenterrier.cef

/**
 * Persistence for chat conversations and their messages (design 2.1, Part A).
 * Implementations must keep DB access off the main thread.
 */
interface ChatRepository {
    /** Creates the conversation row if it does not already exist. Idempotent. */
    suspend fun ensureConversation(conversationId: String, title: String)

    /** Returns every conversation, most-recently-updated first. */
    suspend fun getConversations(): List<Conversation>

    /** Returns a single conversation, or null if it has never been persisted. */
    suspend fun getConversation(id: String): Conversation?

    /** Inserts a new conversation row (or replaces one with the same id). */
    suspend fun createConversation(conversation: Conversation)

    /** Renames a conversation and bumps its updatedAt. No-op if the id is unknown. */
    suspend fun renameConversation(id: String, title: String, updatedAt: Long)

    /** Repins a conversation's source scope and bumps its updatedAt. No-op if the id is unknown. */
    suspend fun setSourceScope(id: String, scope: ChatSourceScope, updatedAt: Long)

    /** Deletes a conversation and all of its messages. */
    suspend fun deleteConversation(id: String)

    /** Persists a single message, auto-creating its parent conversation row if needed. */
    suspend fun saveMessage(message: ChatMessage, tokenEstimate: Long = 0L)

    /** Returns the conversation's messages ordered oldest-first. */
    suspend fun getMessages(conversationId: String): List<ChatMessage>

    /** Removes every message in the conversation (the conversation row itself is left intact). */
    suspend fun deleteMessages(conversationId: String)
}
