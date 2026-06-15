package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlin.time.Clock

class SqlDelightUserPreferenceMemoryRepository(
    private val database: AppDatabase,
    private val logger: Logger? = null
) : UserPreferenceMemoryRepository {

    private val tag = "SqlDelightUserPreferenceMemoryRepository"

    override suspend fun logOverride(action: OverrideAction, event: Event) =
        withContext(Dispatchers.Default) {
            val now = Clock.System.now().toEpochMilliseconds()
            val id = "${now}_${event.id ?: "unknown"}"
            val dayOfWeek = when (event) {
                is TimeEvent -> event.date.dayOfWeek
                is DayEvent -> event.date.dayOfWeek
            }.name

            val startHour = when (event) {
                is TimeEvent -> event.startTime.hour
                is DayEvent -> 0
            }

            val endHour = when (event) {
                is TimeEvent -> {
                    val end = event.endTime.hour
                    if (event.endTime.minute > 0) end + 1 else end
                }

                is DayEvent -> 24
            }

            database.appDatabaseQueries.insertOverrideLog(
                id = id,
                actionType = action.name,
                dayOfWeek = dayOfWeek,
                startHour = startHour.toLong(),
                endHour = endHour.toLong(),
                timestamp = now
            )
        }

    override suspend fun pruneOldLogs(olderThanMs: Long) = withContext(Dispatchers.Default) {
        database.appDatabaseQueries.deleteOverrideLogsOlderThan(olderThanMs)
    }

    override suspend fun getDerivedConstraints(overrideThreshold: Int): List<UserPreferenceConstraint> =
        withContext(Dispatchers.Default) {
            // Prune logs older than 30 days
            val thirtyDaysAgo =
                Clock.System.now().toEpochMilliseconds() - (30L * 24 * 60 * 60 * 1000)
            database.appDatabaseQueries.deleteOverrideLogsOlderThan(thirtyDaysAgo)

            val logs = database.appDatabaseQueries.selectAllOverrideLogs().executeAsList()
            val counts = mutableMapOf<DayOfWeek, IntArray>()

            for (log in logs) {
                val day = try {
                    DayOfWeek.valueOf(log.dayOfWeek)
                } catch (e: Exception) {
                    logger?.e(tag, "Failed to parse DayOfWeek: ${log.dayOfWeek}", e)
                    continue
                }
                val start = log.startHour.toInt().coerceIn(0, 23)
                val end = log.endHour.toInt().coerceIn(0, 24)

                val hrArray = counts.getOrPut(day) { IntArray(24) }
                for (h in start until end) {
                    if (h in 0..23) {
                        hrArray[h]++
                    }
                }
            }

            val constraints = mutableListOf<UserPreferenceConstraint>()
            for (day in DayOfWeek.entries) {
                val hrArray = counts[day] ?: continue
                var startBlock: Int? = null
                for (h in 0..23) {
                    val isBlocked = hrArray[h] >= overrideThreshold
                    if (isBlocked) {
                        if (startBlock == null) {
                            startBlock = h
                        }
                    } else {
                        if (startBlock != null) {
                            constraints.add(UserPreferenceConstraint(day, startBlock, h))
                            startBlock = null
                        }
                    }
                }
                if (startBlock != null) {
                    constraints.add(UserPreferenceConstraint(day, startBlock, 24))
                }
            }

            constraints
        }

    override suspend fun clearAllLogs() = withContext(Dispatchers.Default) {
        database.appDatabaseQueries.deleteAllOverrideLogs()
    }
}
