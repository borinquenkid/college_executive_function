package com.borinquenterrier.cef

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SqlDelightAnalysisCacheRepositoryTest : FunSpec({

    lateinit var driver: SqlDriver
    lateinit var database: AppDatabase
    lateinit var repository: SqlDelightAnalysisCacheRepository

    beforeEach {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)
        repository = SqlDelightAnalysisCacheRepository(database)
    }

    afterEach {
        driver.close()
    }

    test("put and get cached analysis round-trip") {
        val analysis = CachedAnalysis(
            sourceHash = "hash123",
            cachedEventsJson = "[]",
            cachedMetadataJson = "metadata-json",
            createdAt = 123456789L
        )

        repository.putCache(analysis)

        val retrieved = repository.getCached("hash123")
        retrieved shouldNotBe null
        retrieved!!.sourceHash shouldBe "hash123"
        retrieved.cachedEventsJson shouldBe "[]"
        retrieved.cachedMetadataJson shouldBe "metadata-json"
        retrieved.createdAt shouldBe 123456789L
    }

    test("getCached returns null on miss") {
        val retrieved = repository.getCached("nonexistent")
        retrieved shouldBe null
    }

    test("evict removes entry") {
        val analysis = CachedAnalysis(
            sourceHash = "hash123",
            cachedEventsJson = "[]",
            cachedMetadataJson = "metadata-json",
            createdAt = 123456789L
        )

        repository.putCache(analysis)
        repository.evict("hash123")

        val retrieved = repository.getCached("hash123")
        retrieved shouldBe null
    }

    test("two different hashes do not collide") {
        val analysis1 = CachedAnalysis("hash1", "[]", "meta1", 100L)
        val analysis2 = CachedAnalysis("hash2", "[]", "meta2", 200L)

        repository.putCache(analysis1)
        repository.putCache(analysis2)

        repository.getCached("hash1")!!.cachedMetadataJson shouldBe "meta1"
        repository.getCached("hash2")!!.cachedMetadataJson shouldBe "meta2"
    }
})
