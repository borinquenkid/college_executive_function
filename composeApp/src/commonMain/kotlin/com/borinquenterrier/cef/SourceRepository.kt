package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.FragmentEntity
import com.borinquenterrier.cef.db.SourceEntity
import kotlinx.coroutines.flow.StateFlow

interface SourceRepository {
    suspend fun saveSource(sourceItem: SourceItem, originUri: String?, rawBytes: ByteArray? = null)
    suspend fun updateSourceStatus(sourceId: String, status: SourceStatus)
    /**
     * Live status updates for [sourceId] (ADR 0012's web SSE stream — see
     * `GET /api/sources/{id}/stream`). Null if the source has never had a status transition
     * recorded in this process's lifetime (in-memory, not persisted — a late subscriber after a
     * server restart sees nothing here even though the DB row's status is still accurate).
     * Collecting the returned [StateFlow] immediately yields the current phase, not just future
     * transitions, since that's the whole point: a subscriber connecting mid-digestion must not
     * have to wait for the next transition to know where things stand.
     */
    suspend fun statusFlow(sourceId: String): StateFlow<SourceStatus>?
    suspend fun updateSourceMetadata(sourceId: String, metadata: String)
    suspend fun getSourceMetadata(sourceId: String): String?
    suspend fun getAllSources(): List<SourceEntity>
    suspend fun getSourceById(sourceId: String): SourceEntity?
    suspend fun getFragmentsForSource(sourceId: String): List<FragmentEntity>
    suspend fun deleteSource(sourceId: String)
}
