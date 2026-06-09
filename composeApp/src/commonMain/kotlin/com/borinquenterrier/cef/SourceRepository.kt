package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.SourceEntity
import com.borinquenterrier.cef.db.FragmentEntity

interface SourceRepository {
    suspend fun saveSource(sourceItem: SourceItem, originUri: String?)
    suspend fun updateSourceMetadata(sourceId: String, metadata: String)
    suspend fun getSourceMetadata(sourceId: String): String?
    suspend fun getAllSources(): List<SourceEntity>
    suspend fun getSourceById(sourceId: String): SourceEntity?
    suspend fun getFragmentsForSource(sourceId: String): List<FragmentEntity>
    suspend fun deleteSource(sourceId: String)
}
