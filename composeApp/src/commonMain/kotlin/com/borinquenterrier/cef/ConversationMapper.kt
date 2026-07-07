package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.ConversationEntity

/** Maps between the [Conversation] domain model and its SQLDelight [ConversationEntity] row. */
object ConversationMapper {

    fun toEntity(conversation: Conversation): ConversationEntity =
        ConversationEntity(
            id = conversation.id,
            title = conversation.title,
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt,
            sourceScope = conversation.sourceScope.serialize(),
            summary = conversation.summary
        )

    fun toDomain(entity: ConversationEntity): Conversation =
        Conversation(
            id = entity.id,
            title = entity.title,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            sourceScope = ChatSourceScope.parse(entity.sourceScope),
            summary = entity.summary
        )
}
