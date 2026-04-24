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
        
        if (!exists) {
            AppDatabase.Schema.create(driver)
        }
        
        return driver
    }
}
