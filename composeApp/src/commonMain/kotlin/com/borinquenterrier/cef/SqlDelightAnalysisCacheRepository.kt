package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlDelightAnalysisCacheRepository(
    private val database: AppDatabase
) : AnalysisCacheRepository {

    override suspend fun getCached(hash: String): CachedAnalysis? =
        withContext(Dispatchers.Default) {
            val entity = database.appDatabaseQueries.getCachedAnalysis(hash).executeAsOneOrNull()
            if (entity != null) {
                CachedAnalysis(
                    sourceHash = entity.sourceHash,
                    cachedEventsJson = entity.cachedEventsJson,
                    cachedMetadataJson = entity.cachedMetadataJson,
                    createdAt = entity.createdAt
                )
            } else {
                null
            }
        }

    override suspend fun putCache(analysis: CachedAnalysis) =
        withContext(Dispatchers.Default) {
            database.appDatabaseQueries.insertCachedAnalysis(
                sourceHash = analysis.sourceHash,
                cachedEventsJson = analysis.cachedEventsJson,
                cachedMetadataJson = analysis.cachedMetadataJson,
                createdAt = analysis.createdAt
            )
        }

    override suspend fun evict(hash: String) =
        withContext(Dispatchers.Default) {
            database.appDatabaseQueries.deleteCachedAnalysis(hash)
        }
}
