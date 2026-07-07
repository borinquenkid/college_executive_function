package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ChatMessageMapperTest : FunSpec({

    test("toDomain(toEntity(message)) round-trips a USER message with all fields intact") {
        val message = ChatMessage.create(
            content = "When is the midterm?",
            role = ChatRole.USER,
            createdAt = 1_700_000_000_000L,
            conversationId = "conv-1"
        )

        ChatMessageMapper.toDomain(ChatMessageMapper.toEntity(message)) shouldBe message
    }

    test("toDomain(toEntity(message)) round-trips an AI message") {
        val message = ChatMessage.create("Assignments lose 10% per day late.", ChatRole.AI, 42L)

        ChatMessageMapper.toDomain(ChatMessageMapper.toEntity(message)) shouldBe message
    }

    test("toEntity carries the token estimate onto the entity") {
        val message = ChatMessage.create("hi", ChatRole.USER, 1L)

        ChatMessageMapper.toEntity(message, tokenEstimate = 7L).tokenEstimate shouldBe 7L
    }

    test("fromStorage falls back to AI for unknown/legacy role strings, but preserves known ones") {
        ChatRole.fromStorage("USER") shouldBe ChatRole.USER
        ChatRole.fromStorage("AI") shouldBe ChatRole.AI
        ChatRole.fromStorage("ASSISTANT") shouldBe ChatRole.AI
        ChatRole.fromStorage("") shouldBe ChatRole.AI
    }

    test("create derives a deterministic id from content, role, timestamp and conversation") {
        val a = ChatMessage.create("hello", ChatRole.USER, 1L, "c1")
        val b = ChatMessage.create("hello", ChatRole.USER, 1L, "c1")
        a.id shouldBe b.id

        val differentRole = ChatMessage.create("hello", ChatRole.AI, 1L, "c1")
        (a.id == differentRole.id) shouldBe false
    }
})
