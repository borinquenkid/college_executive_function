package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Persists/reads [StudentTermProfile] rows (ADR 0004 / ROADMAP Phase 13, XM-1/XM-3/XM-4).
 * `termStart` is the primary key, so re-saving the same term ([TermProfileAggregator.aggregate]'s
 * output for that term) is an idempotent upsert — safe to call more than once for the same
 * boundary crossing.
 */
class TermProfileRepository(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun save(profile: StudentTermProfile) = withContext(dispatcher) {
        database.appDatabaseQueries.insertTermProfile(
            termStart = profile.termStart.toString(),
            termEnd = profile.termEnd.toString(),
            courseLoad = profile.courseLoad.toLong(),
            categoryDistributionJson = json.encodeToString(profile.categoryDistribution.mapKeys { it.key.name }),
            deadlineCadenceByWeekdayJson = json.encodeToString(profile.deadlineCadenceByWeekday.mapKeys { it.key.name }),
            studyPlanConstraintsJson = null,
            computedAt = Clock.System.now().toEpochMilliseconds()
        )
        Unit
    }

    suspend fun getAll(): List<StudentTermProfile> = withContext(dispatcher) {
        database.appDatabaseQueries.selectAllTermProfiles().executeAsList().map { row ->
            StudentTermProfile(
                termStart = LocalDate.parse(row.termStart),
                termEnd = LocalDate.parse(row.termEnd),
                courseLoad = row.courseLoad.toInt(),
                categoryDistribution = json.decodeFromString<Map<String, Int>>(row.categoryDistributionJson)
                    .mapKeys { AcademicCategory.valueOf(it.key) },
                deadlineCadenceByWeekday = json.decodeFromString<Map<String, Int>>(row.deadlineCadenceByWeekdayJson)
                    .mapKeys { DayOfWeek.valueOf(it.key) }
            )
        }
    }

    /** Number of completed terms recorded so far — the min-2-terms confabulation floor (ADR 0004) reads this. */
    suspend fun count(): Long = withContext(dispatcher) {
        database.appDatabaseQueries.selectTermProfileCount().executeAsOne()
    }
}
