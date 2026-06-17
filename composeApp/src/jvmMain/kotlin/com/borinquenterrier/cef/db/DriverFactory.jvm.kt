package com.borinquenterrier.cef.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.getAppDirectory
import java.io.File

actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        val databaseFile = File(getAppDirectory(), "cef.db")
        val exists = databaseFile.exists()

        if (!databaseFile.parentFile.exists()) {
            databaseFile.parentFile.mkdirs()
        }

        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")

        if (!exists) {
            AppDatabase.Schema.create(driver)
        } else {
            try {
                AppDatabase.Schema.migrate(driver, 1, AppDatabase.Schema.version)
            } catch (e: Exception) {
                // Migration already applied or not needed — safe to ignore
            }
        }

        return driver
    }
}
