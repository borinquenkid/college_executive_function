package com.borinquenterrier.cef

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Handles serialization and deserialization of preference data structures.
 * Provides centralized error handling for JSON operations.
 */
class PreferenceSerializer(private val logger: Logger) {
    private val tag = "PreferenceSerializer"

    fun deserializeDirectories(jsonString: String): List<String>? {
        if (jsonString.isBlank()) return null
        return try {
            Json.decodeFromString<List<String>>(jsonString)
        } catch (e: SerializationException) {
            logger.e(tag, "Deserialization failed for directories", e)
            null
        }
    }

    fun serializeDirectories(dirs: List<String>): String {
        return try {
            Json.encodeToString(dirs)
        } catch (e: SerializationException) {
            logger.e(tag, "Serialization failed for directories", e)
            ""
        }
    }
}
