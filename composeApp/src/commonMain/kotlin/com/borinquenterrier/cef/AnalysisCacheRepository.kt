package com.borinquenterrier.cef

interface AnalysisCacheRepository {
    suspend fun getCached(hash: String): CachedAnalysis?
    suspend fun putCache(analysis: CachedAnalysis)
    suspend fun evict(hash: String)
}
