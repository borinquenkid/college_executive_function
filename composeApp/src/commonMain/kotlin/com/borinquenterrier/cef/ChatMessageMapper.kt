package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.ChatMessageEntity

/** Maps between the [ChatMessage] domain model and its SQLDelight [ChatMessageEntity] row. */
object ChatMessageMapper {

    fun toEntity(message: ChatMessage, tokenEstimate: Long = 0L): ChatMessageEntity =
        ChatMessageEntity(
            id = message.id,
            conversationId = message.conversationId,
            role = message.role.name,
            content = message.content,
            createdAt = message.createdAt,
            tokenEstimate = tokenEstimate
        )

    fun toDomain(entity: ChatMessageEntity): ChatMessage =
        ChatMessage(
            id = entity.id,
            conversationId = entity.conversationId,
            role = ChatRole.fromStorage(entity.role),
            content = entity.content,
            createdAt = entity.createdAt
        )
}
