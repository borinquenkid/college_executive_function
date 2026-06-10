package com.borinquenterrier.cef

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import java.io.File

/**
 * Opens (or creates) a per-student SQLite database under baseDir.
 *
 * On first open: creates parent directories, runs AppDatabase.Schema.create, enables WAL mode.
 * On subsequent opens: schema creation is skipped (IF NOT EXISTS guards in .sq files).
 */
class TenantDatabaseFactory(private val baseDir: String) {

    fun dbFileFor(studentId: String): File {
        val hash = md5(studentId)
        val dir = File(baseDir, "${hash.substring(0, 2)}/${hash.substring(2, 4)}")
        return File(dir, "$studentId.db")
    }

    fun openDriver(studentId: String): SqlDriver {
        val file = dbFileFor(studentId)
        val isNew = !file.exists()

        file.parentFile.mkdirs()

        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")

        if (isNew) {
            AppDatabase.Schema.create(driver)
        } else {
            try { AppDatabase.Schema.create(driver) } catch (_: Exception) { }
        }

        driver.execute(null, "PRAGMA journal_mode=WAL", 0, null)

        return driver
    }

    private fun md5(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
