package com.borinquenterrier.cef.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual open class DriverFactory {
    actual open fun createDriver(): SqlDriver {
        return NativeSqliteDriver(AppDatabase.Schema, "cef.db")
    }
}
