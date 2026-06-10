package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.DriverFactory
import com.borinquenterrier.cef.db.createDatabase
import app.cash.sqldelight.db.SqlDriver
import java.io.File

/**
 * Automatically discovers all student SQLite database files in the tenant directory
 * and runs schema upgrades/migrations on each.
 */
class TenantMigrationRunner(private val tenantBaseDir: String) {

    fun runMigrations(): Int {
        val base = File(tenantBaseDir)
        if (!base.exists() || !base.isDirectory) return 0

        val dbFiles = base.walkTopDown()
            .filter { it.isFile && it.extension == "db" }
            .toList()

        var count = 0
        for (dbFile in dbFiles) {
            val migratingFactory = MigratingDriverFactory(dbFile)
            // createDatabase triggers schema creation and manual alter table migrations
            createDatabase(migratingFactory)
            migratingFactory.createdDriver?.close()
            count++
        }
        return count
    }

    private class MigratingDriverFactory(dbFile: File) : DriverFactory(dbFile) {
        var createdDriver: SqlDriver? = null

        override fun createDriver(): SqlDriver {
            val driver = super.createDriver()
            createdDriver = driver
            return driver
        }
    }
}
