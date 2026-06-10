package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class VacuumBackupRunnerTest {

    private lateinit var tenantDir: File
    private lateinit var backupDir: File

    @BeforeTest
    fun setUp() {
        tenantDir = Files.createTempDirectory("cef-vacuum-tenant").toFile()
        backupDir = Files.createTempDirectory("cef-vacuum-backup").toFile()
    }

    @AfterTest
    fun tearDown() {
        tenantDir.deleteRecursively()
        backupDir.deleteRecursively()
    }

    // ── no databases ──────────────────────────────────────────────────────────

    @Test
    fun `runAll with no db files returns empty results`() = runBlocking {
        val runner = VacuumBackupRunner(backupDir = backupDir.absolutePath)
        val results = runner.runAll(tenantBaseDir = tenantDir.absolutePath)
        assertTrue(results.isEmpty())
    }

    // ── single database ───────────────────────────────────────────────────────

    @Test
    fun `runAll creates a backup file for a single tenant database`() = runBlocking {
        seedTenantDb("alice")

        val runner = VacuumBackupRunner(backupDir = backupDir.absolutePath)
        val results = runner.runAll(tenantBaseDir = tenantDir.absolutePath)

        assertEquals(1, results.size)
        assertTrue(results.first().success, "backup should succeed")
        assertTrue(File(results.first().backupPath).exists(), "backup file should exist on disk")
    }

    @Test
    fun `runAll result contains the correct source path`() = runBlocking {
        val dbFile = seedTenantDb("alice")

        val runner = VacuumBackupRunner(backupDir = backupDir.absolutePath)
        val results = runner.runAll(tenantBaseDir = tenantDir.absolutePath)

        assertEquals(dbFile.absolutePath, results.first().sourcePath)
    }

    @Test
    fun `backup file is a valid readable SQLite database`() = runBlocking {
        seedTenantDb("alice")

        val runner = VacuumBackupRunner(backupDir = backupDir.absolutePath)
        val results = runner.runAll(tenantBaseDir = tenantDir.absolutePath)

        val backupFile = File(results.first().backupPath)
        assertTrue(backupFile.length() > 0L, "backup file should not be empty")

        // Verify it's a valid SQLite file (first 6 bytes are "SQLite")
        val header = backupFile.inputStream().use { it.readNBytes(6) }
        assertEquals("SQLite", String(header))
    }

    // ── multiple databases ────────────────────────────────────────────────────

    @Test
    fun `runAll backs up all tenant databases`() = runBlocking {
        seedTenantDb("alice")
        seedTenantDb("bob")
        seedTenantDb("carol")

        val runner = VacuumBackupRunner(backupDir = backupDir.absolutePath)
        val results = runner.runAll(tenantBaseDir = tenantDir.absolutePath)

        assertEquals(3, results.size)
        assertTrue(results.all { it.success })
    }

    // ── error isolation ───────────────────────────────────────────────────────

    @Test
    fun `a failure for one db does not prevent other databases from being backed up`() = runBlocking {
        seedTenantDb("alice")
        seedTenantDb("bob")
        // Plant a corrupted db file for carol
        val corruptFile = File(tenantDir, "corrupt.db")
        corruptFile.writeText("this is not a valid sqlite file")

        val runner = VacuumBackupRunner(backupDir = backupDir.absolutePath)
        val results = runner.runAll(tenantBaseDir = tenantDir.absolutePath)

        val successful = results.filter { it.success }
        assertTrue(successful.size >= 2, "alice and bob should succeed despite carol's corruption")
    }

    @Test
    fun `failed backup result carries the exception`() = runBlocking {
        val corruptFile = File(tenantDir, "corrupt.db")
        corruptFile.writeText("not sqlite")

        val runner = VacuumBackupRunner(backupDir = backupDir.absolutePath)
        val results = runner.runAll(tenantBaseDir = tenantDir.absolutePath)

        val failed = results.firstOrNull { !it.success }
        assertNotNull(failed)
        assertNotNull(failed.error)
    }

    // ── backup directory creation ─────────────────────────────────────────────

    @Test
    fun `runAll creates the backup directory if it does not exist`() = runBlocking {
        val nonExistentBackup = File(tenantDir, "backups/nightly")
        assertFalse(nonExistentBackup.exists())

        seedTenantDb("alice")

        val runner = VacuumBackupRunner(backupDir = nonExistentBackup.absolutePath)
        runner.runAll(tenantBaseDir = tenantDir.absolutePath)

        assertTrue(nonExistentBackup.exists(), "backup directory should be created")
    }

    // ── backup naming ─────────────────────────────────────────────────────────

    @Test
    fun `backup file is named after the source database file`() = runBlocking {
        seedTenantDb("uniquename")

        val runner = VacuumBackupRunner(backupDir = backupDir.absolutePath)
        val results = runner.runAll(tenantBaseDir = tenantDir.absolutePath)

        val backupName = File(results.first().backupPath).name
        assertEquals("uniquename.db", backupName)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun seedTenantDb(studentId: String): File {
        val dbFile = File(tenantDir, "$studentId.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        driver.execute(null, "CREATE TABLE IF NOT EXISTS test (id TEXT PRIMARY KEY)", 0, null)
        driver.close()
        return dbFile
    }
}
