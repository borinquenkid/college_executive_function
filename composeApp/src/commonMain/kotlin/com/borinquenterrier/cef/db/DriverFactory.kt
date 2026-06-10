package com.borinquenterrier.cef.db

import app.cash.sqldelight.db.SqlDriver

expect class DriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DriverFactory): AppDatabase {
    val driver = driverFactory.createDriver()
    try {
        driver.execute(
            null,
            "ALTER TABLE SourceEntity ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'",
            0
        )
    } catch (e: Exception) {
        // Column may already exist, or table might not have been created yet, ignore.
    }
    try {
        driver.execute(null, "ALTER TABLE EventEntity ADD COLUMN studyPlanStart TEXT", 0)
    } catch (e: Exception) {
        // Column may already exist, or table might not have been created yet, ignore.
    }
    try {
        driver.execute(null, "ALTER TABLE EventEntity ADD COLUMN gradeWeight REAL", 0)
    } catch (e: Exception) {
        // Column may already exist, or table might not have been created yet, ignore.
    }
    try {
        driver.execute(
            null,
            "ALTER TABLE EventEntity ADD COLUMN completionStatus TEXT NOT NULL DEFAULT 'INCOMPLETE'",
            0
        )
    } catch (e: Exception) {
        // Column may already exist, or table might not have been created yet, ignore.
    }
    return AppDatabase(driver)
}
