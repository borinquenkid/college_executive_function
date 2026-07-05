package com.borinquenterrier.cef

interface UserPreferenceMemoryRepository {
    suspend fun logOverride(action: OverrideAction, event: Event)
    suspend fun pruneOldLogs(olderThanMs: Long)
    suspend fun getDerivedConstraints(overrideThreshold: Int = 2): List<UserPreferenceConstraint>
    suspend fun clearAllLogs()

    companion object {
        // 30 days: long enough for getDerivedConstraints() to see a recurring weekly
        // pattern (4+ occurrences) rather than a one-off override, short enough that a
        // semester-old habit doesn't keep suppressing a schedule the student has since changed.
        const val OVERRIDE_LOG_RETENTION_MS: Long = 30L * 24 * 60 * 60 * 1000

        val NoOp: UserPreferenceMemoryRepository = object : UserPreferenceMemoryRepository {
            override suspend fun logOverride(action: OverrideAction, event: Event) {}
            override suspend fun pruneOldLogs(olderThanMs: Long) {}
            override suspend fun getDerivedConstraints(overrideThreshold: Int) = emptyList<UserPreferenceConstraint>()
            override suspend fun clearAllLogs() {}
        }
    }
}
