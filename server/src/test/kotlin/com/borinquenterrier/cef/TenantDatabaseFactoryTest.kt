package com.borinquenterrier.cef

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class TenantDatabaseFactoryTest {

    private lateinit var baseDir: File
    private lateinit var factory: TenantDatabaseFactory

    @BeforeTest
    fun setUp() {
        baseDir = Files.createTempDirectory("cef-tenant-test").toFile()
        factory = TenantDatabaseFactory(baseDir.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        baseDir.deleteRecursively()
    }

    // ── path derivation ──────────────────────────────────────────────────────

    @Test
    fun `dbFileFor creates the 2-level hash directory path under baseDir`() {
        val file = factory.dbFileFor("alice")
        assertTrue(file.absolutePath.startsWith(baseDir.absolutePath))
        assertEquals("alice.db", file.name)
        // parent is xx, grandparent is baseDir/yy  →  baseDir/yy/xx/alice.db
        assertEquals(2, file.parentFile.name.length)
        assertEquals(2, file.parentFile.parentFile.name.length)
    }

    @Test
    fun `dbFileFor is deterministic`() {
        assertEquals(factory.dbFileFor("alice"), factory.dbFileFor("alice"))
    }

    @Test
    fun `dbFileFor produces different files for different studentIds`() {
        assertNotEquals(factory.dbFileFor("alice"), factory.dbFileFor("bob"))
    }

    // ── directory creation ───────────────────────────────────────────────────

    @Test
    fun `openDriver creates missing parent directories`() {
        val driver = factory.openDriver("new-student")
        driver.close()
        assertTrue(factory.dbFileFor("new-student").parentFile.exists())
    }

    @Test
    fun `openDriver creates the database file on disk`() {
        val driver = factory.openDriver("new-student")
        driver.close()
        assertTrue(factory.dbFileFor("new-student").exists())
    }

    // ── schema ───────────────────────────────────────────────────────────────

    @Test
    fun `openDriver initialises the AppDatabase schema`() {
        val driver = factory.openDriver("schema-student")
        val db = AppDatabase(driver)
        // If schema was not created this would throw; a successful query proves it
        val sources = db.appDatabaseQueries.selectAllSources().executeAsList()
        assertTrue(sources.isEmpty())
        driver.close()
    }

    @Test
    fun `openDriver is idempotent — reopening an existing database does not throw`() {
        val d1 = factory.openDriver("idempotent-student")
        d1.close()
        val d2 = factory.openDriver("idempotent-student")
        d2.close()
    }

    // ── WAL mode ─────────────────────────────────────────────────────────────

    @Test
    fun `openDriver enables WAL journal mode`() {
        val driver = factory.openDriver("wal-student")
        val mode = driver.executeQuery(
            identifier = null,
            sql = "PRAGMA journal_mode",
            mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
            parameters = 0
        ).value
        driver.close()
        assertEquals("wal", mode?.lowercase())
    }

    // ── integration with TenantConnectionCache ───────────────────────────────

    @Test
    fun `factory can be used as the driverFactory for TenantConnectionCache`() {
        val cache = TenantConnectionCache(
            capacity = 10,
            baseDir = baseDir.absolutePath,
            driverFactory = { path -> factory.openDriver(File(path).nameWithoutExtension) }
        )
        // Just verifies the wiring compiles and runs without error
        val driver = kotlinx.coroutines.runBlocking { cache.getOrOpen("wired-student") }
        assertNotNull(driver)
        kotlinx.coroutines.runBlocking { cache.closeAll() }
    }
}
