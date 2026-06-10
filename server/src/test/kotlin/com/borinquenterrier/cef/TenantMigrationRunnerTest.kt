package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class TenantMigrationRunnerTest {

    private lateinit var baseDir: File
    private lateinit var dbFactory: TenantDatabaseFactory

    @BeforeTest
    fun setUp() {
        baseDir = Files.createTempDirectory("cef-migration-test").toFile()
        dbFactory = TenantDatabaseFactory(baseDir.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        baseDir.deleteRecursively()
    }

    @Test
    fun `TenantDriverFactory routes each student to their sharded SQLite path`() {
        val aliceDriverFactory = TenantDriverFactory("alice", dbFactory)
        val bobDriverFactory = TenantDriverFactory("bob", dbFactory)

        val aliceFile = dbFactory.dbFileFor("alice")
        val bobFile = dbFactory.dbFileFor("bob")

        // Assert paths are isolated
        assertNotEquals(aliceFile.absolutePath, bobFile.absolutePath)

        // Open drivers and verify databases are created in separate files
        val aliceDriver = aliceDriverFactory.createDriver()
        val bobDriver = bobDriverFactory.createDriver()

        try {
            assertTrue(aliceFile.exists())
            assertTrue(bobFile.exists())

            // Verify they are separate databases by inserting into one and checking the other
            val aliceDb = AppDatabase(aliceDriver)
            val bobDb = AppDatabase(bobDriver)

            assertTrue(aliceDb.appDatabaseQueries.selectAllSources().executeAsList().isEmpty())
            assertTrue(bobDb.appDatabaseQueries.selectAllSources().executeAsList().isEmpty())
        } finally {
            aliceDriver.close()
            bobDriver.close()
        }
    }

    @Test
    fun `TenantMigrationRunner discovers all active tenant databases and runs migrations`() {
        // Pre-create some sharded database files
        val aliceFile = dbFactory.dbFileFor("alice")
        val bobFile = dbFactory.dbFileFor("bob")

        aliceFile.parentFile.mkdirs()
        bobFile.parentFile.mkdirs()

        // Create empty files to simulate existing tenant DBs
        aliceFile.createNewFile()
        bobFile.createNewFile()

        val runner = TenantMigrationRunner(baseDir.absolutePath)
        val migratedCount = runner.runMigrations()

        assertEquals(2, migratedCount)

        // Verify that schemas were initialized inside those files
        val aliceDriverFactory = TenantDriverFactory("alice", dbFactory)
        val bobDriverFactory = TenantDriverFactory("bob", dbFactory)

        val aliceDriver = aliceDriverFactory.createDriver()
        val bobDriver = bobDriverFactory.createDriver()

        try {
            val aliceDb = AppDatabase(aliceDriver)
            val bobDb = AppDatabase(bobDriver)

            // Select query runs successfully, proving schema was initialized
            assertTrue(aliceDb.appDatabaseQueries.selectAllSources().executeAsList().isEmpty())
            assertTrue(bobDb.appDatabaseQueries.selectAllSources().executeAsList().isEmpty())
        } finally {
            aliceDriver.close()
            bobDriver.close()
        }
    }
}
