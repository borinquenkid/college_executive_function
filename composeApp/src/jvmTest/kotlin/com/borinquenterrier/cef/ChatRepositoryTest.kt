package com.borinquenterrier.cef

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class ChatRepositoryTest : FunSpec({

    lateinit var driver: SqlDriver
    lateinit var database: AppDatabase
    lateinit var repository: ChatRepository

    val convId = ChatMessage.DEFAULT_CONVERSATION_ID

    beforeEach {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)
        repository = SqlDelightChatRepository(database)
    }

    afterEach {
        driver.close()
    }

    test("saveMessage persists a message that getMessages returns intact") {
        val msg = ChatMessage.create("Hello!", ChatRole.USER, 100L)

        repository.saveMessage(msg)

        repository.getMessages(convId) shouldBe listOf(msg)
    }

    test("getMessages returns messages ordered by createdAt ascending") {
        val first = ChatMessage.create("first", ChatRole.USER, 100L)
        val second = ChatMessage.create("second", ChatRole.AI, 200L)

        repository.saveMessage(second)
        repository.saveMessage(first)

        repository.getMessages(convId).map { it.content } shouldBe listOf("first", "second")
    }

    test("messages survive a repository restart (fresh instance on the same database)") {
        repository.saveMessage(ChatMessage.create("persist me", ChatRole.USER, 100L))

        val restarted = SqlDelightChatRepository(database)

        restarted.getMessages(convId).map { it.content } shouldBe listOf("persist me")
    }

    test("saveMessage auto-creates the parent conversation row") {
        repository.saveMessage(ChatMessage.create("hi", ChatRole.USER, 1L, "brand-new"))

        database.appDatabaseQueries.selectConversationById("brand-new")
            .executeAsOneOrNull().shouldNotBeNull()
    }

    test("saveMessage is idempotent on the deterministic id (no duplicate rows)") {
        val msg = ChatMessage.create("dup", ChatRole.USER, 100L)

        repository.saveMessage(msg)
        repository.saveMessage(msg)

        repository.getMessages(convId).size shouldBe 1
    }

    test("deleteMessages removes all messages for a conversation") {
        repository.saveMessage(ChatMessage.create("a", ChatRole.USER, 1L))
        repository.saveMessage(ChatMessage.create("b", ChatRole.AI, 2L))

        repository.deleteMessages(convId)

        repository.getMessages(convId) shouldBe emptyList()
    }

    // --- Conversation management (design 2.1, Part A / Phase 2) ---

    test("createConversation then getConversation returns it intact") {
        val conversation = Conversation.create(createdAt = 10L, title = "Aid")

        repository.createConversation(conversation)

        repository.getConversation(conversation.id) shouldBe conversation
    }

    test("getConversations orders by updatedAt descending") {
        val older = Conversation.create(createdAt = 100L, title = "older")
        val newer = Conversation.create(createdAt = 200L, title = "newer")

        repository.createConversation(older)
        repository.createConversation(newer)

        repository.getConversations().map { it.title } shouldBe listOf("newer", "older")
    }

    test("renameConversation updates the title and bumps updatedAt") {
        val conversation = Conversation.create(createdAt = 10L, title = "old")
        repository.createConversation(conversation)

        repository.renameConversation(conversation.id, "new", updatedAt = 500L)

        val reloaded = repository.getConversation(conversation.id).shouldNotBeNull()
        reloaded.title shouldBe "new"
        reloaded.updatedAt shouldBe 500L
    }

    test("setSourceScope repins the conversation and survives reload") {
        val conversation = Conversation.create(createdAt = 10L, title = "pin me")
        repository.createConversation(conversation)

        repository.setSourceScope(conversation.id, ChatSourceScope.Source("s7"), updatedAt = 600L)

        val reloaded = repository.getConversation(conversation.id).shouldNotBeNull()
        reloaded.sourceScope shouldBe ChatSourceScope.Source("s7")
        reloaded.updatedAt shouldBe 600L
    }

    test("deleteConversation removes the conversation and its messages") {
        val conversation = Conversation.create(createdAt = 10L, title = "doomed")
        repository.createConversation(conversation)
        repository.saveMessage(ChatMessage.create("hi", ChatRole.USER, 1L, conversation.id))

        repository.deleteConversation(conversation.id)

        repository.getConversation(conversation.id) shouldBe null
        repository.getMessages(conversation.id) shouldBe emptyList()
    }

    test("getConversation returns null for an unknown id") {
        repository.getConversation("nope") shouldBe null
    }
})
