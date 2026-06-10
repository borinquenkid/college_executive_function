package com.borinquenterrier.cef.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual open class DriverFactory(private val context: Context) {
    actual open fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(AppDatabase.Schema, context, "cef.db")
    }
}
