package com.borinquenterrier.cef

interface UserPreferenceMemoryRepository {
    suspend fun logOverride(action: OverrideAction, event: Event)
    suspend fun pruneOldLogs(olderThanMs: Long)
    suspend fun getDerivedConstraints(overrideThreshold: Int = 2): List<UserPreferenceConstraint>
    suspend fun clearAllLogs()

    companion object {
        val NoOp: UserPreferenceMemoryRepository = object : UserPreferenceMemoryRepository {
            override suspend fun logOverride(action: OverrideAction, event: Event) {}
            override suspend fun pruneOldLogs(olderThanMs: Long) {}
            override suspend fun getDerivedConstraints(overrideThreshold: Int) = emptyList<UserPreferenceConstraint>()
            override suspend fun clearAllLogs() {}
        }
    }
}
