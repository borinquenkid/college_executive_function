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

/**
 * Ingestion lifecycle for the web async-upload path (ADR 0012). Android/iOS/Desktop ingest
 * synchronously in-process and always end up at DONE/FAILED by the time [SourceItem] reaches
 * their UI, so this only carries meaning for the web client's SSE stream.
 *
 * No separate WRITING_CALENDAR phase: [SourceProcessingPipeline.processSource] can't distinguish
 * it from RESOLVING_CONFLICTS — both happen inside one `eventAgent.pushToCalendar()` call with no
 * observable boundary in between, so RESOLVING_CONFLICTS covers the whole step.
 */
@Serializable
enum class SourceStatus {
    PENDING,
    ANALYZING_CONTEXT,
    EXTRACTING_DELIVERABLES,
    RESOLVING_CONFLICTS,
    DONE,
    FAILED
}

@Serializable
data class SourceItem(
    val title: String,
    val fragments: List<SourceFragment>,
    val category: SourceCategory = SourceCategory.OTHER,
    // Repo-assigned identity; today equal to `title` (see SqlDelightSourceRepository.saveSource),
    // exposed explicitly here so callers don't have to know/assume that.
    val id: String = title,
    val status: SourceStatus = SourceStatus.DONE
)
