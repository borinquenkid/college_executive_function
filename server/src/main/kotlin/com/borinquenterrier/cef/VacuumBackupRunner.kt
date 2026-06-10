package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VacuumResult(
    val sourcePath: String,
    val backupPath: String,
    val success: Boolean,
    val error: Exception? = null
)

class VacuumBackupRunner(private val backupDir: String) {

    suspend fun runAll(tenantBaseDir: String): List<VacuumResult> = withContext(Dispatchers.IO) {
        val dir = File(backupDir).apply { mkdirs() }
        File(tenantBaseDir)
            .walk()
            .filter { it.isFile && it.extension == "db" }
            .map { dbFile -> vacuum(dbFile, File(dir, dbFile.name)) }
            .toList()
    }

    private fun vacuum(source: File, destination: File): VacuumResult {
        return try {
            JdbcSqliteDriver("jdbc:sqlite:${source.absolutePath}").use { driver ->
                driver.execute(null, "VACUUM INTO '${destination.absolutePath}'", 0, null)
            }
            VacuumResult(source.absolutePath, destination.absolutePath, success = true)
        } catch (e: Exception) {
            VacuumResult(source.absolutePath, destination.absolutePath, success = false, error = e)
        }
    }
}
