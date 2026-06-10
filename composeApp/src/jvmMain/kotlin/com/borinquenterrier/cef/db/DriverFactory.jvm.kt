package com.borinquenterrier.cef.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        val databaseFile = File(System.getProperty("user.home"), ".cef/cef.db")
        val exists = databaseFile.exists()

        if (!databaseFile.parentFile.exists()) {
            databaseFile.parentFile.mkdirs()
        }

        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")

        try {
            if (!exists) {
                AppDatabase.Schema.create(driver)
            } else {
                // Simplest way for dev: if a table is missing, try to create it
                // In a production app, we would use proper versioned migrations.
                AppDatabase.Schema.create(driver)
            }
        } catch (e: Exception) {
            // If create fails because tables already exist, it's fine (using IF NOT EXISTS in .sq)
        }

        return driver
    }
}
