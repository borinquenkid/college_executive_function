package com.borinquenterrier.cef

import kotlinx.serialization.Serializable

@Serializable
enum class SourceCategory {
    SYLLABUS,
    CALENDAR,
    READING_MATERIAL,
    LAB_MANUAL,
    LECTURE_NOTES,
    OTHER
}

@Serializable
data class SourceItem(
    val title: String,
    val fragments: List<SourceFragment>,
    val category: SourceCategory = SourceCategory.OTHER
)
