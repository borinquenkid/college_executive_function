package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import com.borinquenterrier.cef.db.FragmentEntity
import com.borinquenterrier.cef.db.SourceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class SqlDelightSourceRepository(
    private val database: AppDatabase
) : SourceRepository {

    override suspend fun saveSource(sourceItem: SourceItem, originUri: String?) =
        withContext(Dispatchers.Default) {
            val sourceId = sourceItem.title
            database.appDatabaseQueries.insertSource(
                id = sourceId,
                title = sourceItem.title,
                originUri = originUri,
                type = if (sourceItem.fragments.any { it.type == SourceType.CALENDAR }) "CALENDAR" else "TEXT",
                category = sourceItem.category.name,
                metadata = null,
                updatedAt = Clock.System.now().toEpochMilliseconds()
            )

            sourceItem.fragments.forEachIndexed { index, fragment ->
                database.appDatabaseQueries.insertFragment(
                    id = "${sourceId}_$index",
                    sourceId = sourceId,
                    text = fragment.text,
                    pageNumber = fragment.pageNumber?.toLong(),
                    sectionTitle = fragment.sectionTitle,
                    type = fragment.type.name,
                    metadata = null
                )
            }
        }

    override suspend fun updateSourceMetadata(sourceId: String, metadata: String) =
        withContext(Dispatchers.Default) {
            val source = database.appDatabaseQueries.selectSourceById(sourceId).executeAsOneOrNull()
            if (source != null) {
                database.appDatabaseQueries.insertSource(
                    id = source.id,
                    title = source.title,
                    originUri = source.originUri,
                    type = source.type,
                    category = source.category,
                    metadata = metadata,
                    updatedAt = Clock.System.now().toEpochMilliseconds()
                )
            } else {
                database.appDatabaseQueries.insertSource(
                    id = sourceId,
                    title = sourceId,
                    originUri = null,
                    type = "TEXT",
                    category = "OTHER",
                    metadata = metadata,
                    updatedAt = Clock.System.now().toEpochMilliseconds()
                )
            }
        }

    override suspend fun getSourceMetadata(sourceId: String): String? =
        withContext(Dispatchers.Default) {
            database.appDatabaseQueries.selectSourceById(sourceId).executeAsOneOrNull()?.metadata
        }

    override suspend fun getAllSources(): List<SourceEntity> = withContext(Dispatchers.Default) {
        database.appDatabaseQueries.selectAllSources().executeAsList()
    }

    override suspend fun getSourceById(sourceId: String): SourceEntity? =
        withContext(Dispatchers.Default) {
            database.appDatabaseQueries.selectSourceById(sourceId).executeAsOneOrNull()
        }

    override suspend fun getFragmentsForSource(sourceId: String): List<FragmentEntity> =
        withContext(Dispatchers.Default) {
            database.appDatabaseQueries.selectFragmentsBySource(sourceId).executeAsList()
        }

    override suspend fun deleteSource(sourceId: String) = withContext(Dispatchers.Default) {
        database.appDatabaseQueries.deleteFragmentsBySource(sourceId)
        database.appDatabaseQueries.deleteSource(sourceId)
    }
}
