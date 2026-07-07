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
})
