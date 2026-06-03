package com.borinquenterrier.cef

import kotlinx.serialization.Serializable

@Serializable
enum class SourceCategory {
    SYLLABUS,
    READING_MATERIAL,
    LAB_MANUAL,
    LECTURE_NOTES,
    OTHER
}

data class SourceItem(
    val title: String,
    val fragments: List<SourceFragment>,
    val category: SourceCategory = SourceCategory.OTHER
)
