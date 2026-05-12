package com.borinquenterrier.cef

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * A record representing a part of source material (PDF page, DOCX paragraph, or ICS event).
 * This allows for passing metadata along with the raw text to the AI.
 */
@Serializable
data class SourceFragment(
    val text: String,
    val pageNumber: Int? = null,
    val sectionTitle: String? = null,
    val type: SourceType = SourceType.TEXT,
    val metadata: Map<String, String> = emptyMap()
) {
    fun toJson(): String = Json.encodeToString(this)
}

@Serializable
enum class SourceType {
    TEXT,       // Default for PDF/Docx/Txt
    CALENDAR    // Used for ICS events
}
