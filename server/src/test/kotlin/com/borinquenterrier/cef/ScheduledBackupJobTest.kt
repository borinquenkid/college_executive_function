package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class ScheduledBackupJobTest {

    private lateinit var tenantDir: File
    private lateinit var backupDir: File

    @BeforeTest
    fun setUp() {
        tenantDir = Files.createTempDirectory("cef-scheduled-backup-tenant").toFile()
        backupDir = Files.createTempDirectory("cef-scheduled-backup-out").toFile()
    }

    @AfterTest
    fun tearDown() {
        tenantDir.deleteRecursively()
        backupDir.deleteRecursively()
    }

    @Test
    fun `runOnce backs up every tenant database without throwing`() = runBlocking {
        seedTenantDb("alice")
        seedTenantDb("bob")

        val job = ScheduledBackupJob(
            tenantBaseDir = tenantDir.absolutePath,
            backupDir = backupDir.absolutePath,
            intervalHours = 24
        )
        job.runOnce()

        val backedUp = backupDir.listFiles()?.map { it.name }.orEmpty().toSet()
        assertEquals(setOf("alice.db", "bob.db"), backedUp)
    }

    @Test
    fun `runOnce does not throw when the tenant directory is empty`() = runBlocking {
        val job = ScheduledBackupJob(
            tenantBaseDir = tenantDir.absolutePath,
            backupDir = backupDir.absolutePath,
            intervalHours = 24
        )
        job.runOnce()

        assertTrue(backupDir.listFiles().orEmpty().isEmpty())
    }

    private fun seedTenantDb(studentId: String): File {
        val dbFile = File(tenantDir, "$studentId.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        driver.execute(null, "CREATE TABLE IF NOT EXISTS test (id TEXT PRIMARY KEY)", 0, null)
        driver.close()
        return dbFile
    }
}
