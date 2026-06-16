package com.borinquenterrier.cef

import kotlinx.serialization.Serializable

@Serializable
data class CachedAnalysis(
    val sourceHash: String,
    val cachedEventsJson: String,
    val cachedMetadataJson: String?,
    val createdAt: Long
)
