package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class SqlDelightChatRepository(
    private val database: AppDatabase
) : ChatRepository {

    private val queries get() = database.appDatabaseQueries

    override suspend fun ensureConversation(conversationId: String, title: String) =
        withContext(Dispatchers.Default) {
            ensureConversationRow(conversationId, title)
        }

    override suspend fun saveMessage(message: ChatMessage, tokenEstimate: Long) =
        withContext(Dispatchers.Default) {
            ensureConversationRow(message.conversationId, DEFAULT_TITLE)
            val entity = ChatMessageMapper.toEntity(message, tokenEstimate)
            queries.insertChatMessage(
                id = entity.id,
                conversationId = entity.conversationId,
                role = entity.role,
                content = entity.content,
                createdAt = entity.createdAt,
                tokenEstimate = entity.tokenEstimate
            )
            Unit
        }

    override suspend fun getMessages(conversationId: String): List<ChatMessage> =
        withContext(Dispatchers.Default) {
            queries.selectMessagesByConversation(conversationId)
                .executeAsList()
                .map(ChatMessageMapper::toDomain)
        }

    override suspend fun deleteMessages(conversationId: String) =
        withContext(Dispatchers.Default) {
            queries.deleteMessagesByConversation(conversationId)
            Unit
        }

    /** Inserts the conversation row only when absent, so an existing title is never overwritten. */
    private fun ensureConversationRow(conversationId: String, title: String) {
        if (queries.selectConversationById(conversationId).executeAsOneOrNull() != null) return
        val now = Clock.System.now().toEpochMilliseconds()
        queries.insertConversation(
            id = conversationId,
            title = title,
            createdAt = now,
            updatedAt = now,
            sourceScope = "ALL",
            summary = null
        )
    }

    private companion object {
        const val DEFAULT_TITLE = "Chat"
    }
}
