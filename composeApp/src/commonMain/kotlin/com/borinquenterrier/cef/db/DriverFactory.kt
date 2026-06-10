package com.borinquenterrier.cef.db

import app.cash.sqldelight.db.SqlDriver

expect open class DriverFactory {
    open fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DriverFactory): AppDatabase =
    buildDatabase(driverFactory.createDriver())

/**
 * Applies the incremental schema migrations (idempotently) on an already-created [driver] and
 * returns the [AppDatabase]. Shared by production (via [createDatabase]) and by tests that build
 * an in-memory driver, so both get an identical, fully-migrated schema.
 */
fun buildDatabase(driver: SqlDriver): AppDatabase {
    try {
        driver.execute(
            null,
            "ALTER TABLE SourceEntity ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'",
            0
        )
    } catch (_: Exception) {
        // Column may already exist, or table might not have been created yet, ignore.
    }
    try {
        driver.execute(null, "ALTER TABLE EventEntity ADD COLUMN studyPlanStart TEXT", 0)
    } catch (_: Exception) {
        // Column may already exist, or table might not have been created yet, ignore.
    }
    try {
        driver.execute(null, "ALTER TABLE EventEntity ADD COLUMN gradeWeight REAL", 0)
    } catch (_: Exception) {
        // Column may already exist, or table might not have been created yet, ignore.
    }
    try {
        driver.execute(
            null,
            "ALTER TABLE EventEntity ADD COLUMN completionStatus TEXT NOT NULL DEFAULT 'INCOMPLETE'",
            0
        )
    } catch (_: Exception) {
        // Column may already exist, or table might not have been created yet, ignore.
    }
    try {
        driver.execute(null, "ALTER TABLE SourceEntity ADD COLUMN contentHash TEXT", 0)
    } catch (_: Exception) {
        // Column may already exist, or table might not have been created yet, ignore.
    }
    try {
        driver.execute(null, "ALTER TABLE EventEntity ADD COLUMN sourceId TEXT", 0)
    } catch (_: Exception) {
        // Column may already exist, or table might not have been created yet, ignore.
    }
    try {
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS AnalysisCacheEntity (
                sourceHash TEXT PRIMARY KEY NOT NULL,
                cachedEventsJson TEXT NOT NULL,
                cachedMetadataJson TEXT,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
            0
        )
    } catch (_: Exception) {
        // Table may already exist, ignore.
    }
    return AppDatabase(driver)
}
