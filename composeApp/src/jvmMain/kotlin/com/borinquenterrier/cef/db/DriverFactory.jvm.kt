package com.borinquenterrier.cef.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.sql.SQLException

actual open class DriverFactory(val dbFile: File? = null) {
    actual open fun createDriver(): SqlDriver {
        val databaseFile = dbFile ?: File(System.getProperty("user.home"), ".cef/cef.db")
        val exists = databaseFile.exists() && databaseFile.length() > 0

        if (!databaseFile.parentFile.exists()) {
            databaseFile.parentFile.mkdirs()
        }

        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")

        if (!exists) {
            AppDatabase.Schema.create(driver)
        } else {
            try {
                AppDatabase.Schema.migrate(driver, 1, AppDatabase.Schema.version)
            } catch (_: SQLException) {
                // Migration already applied or not needed — safe to ignore
            }
        }

        return driver
    }
}
