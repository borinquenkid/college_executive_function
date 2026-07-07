package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class SqlDelightChatRepository(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ChatRepository {

    private val queries get() = database.appDatabaseQueries

    override suspend fun ensureConversation(conversationId: String, title: String) =
        withContext(dispatcher) {
            ensureConversationRow(conversationId, title)
        }

    override suspend fun saveMessage(message: ChatMessage, tokenEstimate: Long) =
        withContext(dispatcher) {
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

    override suspend fun getConversations(): List<Conversation> =
        withContext(dispatcher) {
            queries.selectAllConversations()
                .executeAsList()
                .map(ConversationMapper::toDomain)
        }

    override suspend fun getConversation(id: String): Conversation? =
        withContext(dispatcher) {
            queries.selectConversationById(id)
                .executeAsOneOrNull()
                ?.let(ConversationMapper::toDomain)
        }

    override suspend fun createConversation(conversation: Conversation) =
        withContext(dispatcher) {
            val entity = ConversationMapper.toEntity(conversation)
            queries.insertConversation(
                id = entity.id,
                title = entity.title,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                sourceScope = entity.sourceScope,
                summary = entity.summary
            )
            Unit
        }

    override suspend fun renameConversation(id: String, title: String, updatedAt: Long) =
        withContext(dispatcher) {
            queries.updateConversationTitle(title = title, updatedAt = updatedAt, id = id)
            Unit
        }

    override suspend fun setSourceScope(id: String, scope: ChatSourceScope, updatedAt: Long) =
        withContext(dispatcher) {
            queries.updateConversationScope(
                sourceScope = scope.serialize(),
                updatedAt = updatedAt,
                id = id
            )
            Unit
        }

    override suspend fun deleteConversation(id: String) =
        withContext(dispatcher) {
            // Delete children first — ChatMessageEntity has a FK to ConversationEntity.
            queries.deleteMessagesByConversation(id)
            queries.deleteConversation(id)
            Unit
        }

    override suspend fun getMessages(conversationId: String): List<ChatMessage> =
        withContext(dispatcher) {
            queries.selectMessagesByConversation(conversationId)
                .executeAsList()
                .map(ChatMessageMapper::toDomain)
        }

    override suspend fun deleteMessages(conversationId: String) =
        withContext(dispatcher) {
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
        val DEFAULT_TITLE = Conversation.DEFAULT_CONVERSATION_TITLE
    }
}
