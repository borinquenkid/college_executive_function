package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ConversationMapperTest : FunSpec({

    test("toDomain(toEntity(conversation)) round-trips an ALL-scope conversation intact") {
        val conversation = Conversation.create(
            createdAt = 1_700_000_000_000L,
            title = "Financial Aid questions"
        )

        ConversationMapper.toDomain(ConversationMapper.toEntity(conversation)) shouldBe conversation
    }

    test("toDomain(toEntity(conversation)) round-trips a single-source pin and summary") {
        val conversation = Conversation.create(
            createdAt = 42L,
            title = "Syllabus deep-dive",
            sourceScope = ChatSourceScope.Source("source-123")
        ).copy(updatedAt = 99L, summary = "Discussed the late policy.")

        ConversationMapper.toDomain(ConversationMapper.toEntity(conversation)) shouldBe conversation
    }

    test("scope serialize/parse round-trips both variants") {
        ChatSourceScope.parse(ChatSourceScope.All.serialize()) shouldBe ChatSourceScope.All
        ChatSourceScope.parse(ChatSourceScope.Source("s1").serialize()) shouldBe
            ChatSourceScope.Source("s1")
    }

    test("parse is lenient: blank, legacy, and malformed scope strings fall back to All") {
        ChatSourceScope.parse("ALL") shouldBe ChatSourceScope.All
        ChatSourceScope.parse("") shouldBe ChatSourceScope.All
        ChatSourceScope.parse("SOURCE:") shouldBe ChatSourceScope.All
        ChatSourceScope.parse("garbage") shouldBe ChatSourceScope.All
    }

    test("create derives a deterministic id from the creation timestamp and title") {
        val a = Conversation.create(createdAt = 1L, title = "Chat")
        val b = Conversation.create(createdAt = 1L, title = "Chat")
        a.id shouldBe b.id

        val later = Conversation.create(createdAt = 2L, title = "Chat")
        (a.id == later.id) shouldBe false
    }

    test("create seeds updatedAt equal to createdAt and defaults to All scope") {
        val conversation = Conversation.create(createdAt = 5L)

        conversation.updatedAt shouldBe 5L
        conversation.sourceScope shouldBe ChatSourceScope.All
    }
})
