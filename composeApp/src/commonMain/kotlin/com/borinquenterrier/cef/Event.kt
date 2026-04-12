package com.borinquenterrier.cef

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

enum class EventSource {
    ROUTINE, AI_GENERATED, MANUAL, STUDENT, SCHOOL, CLASS
}

enum class AcademicCategory {
    REGULAR, HOLIDAY, DEADLINE, FINALS, SEMESTER_BOUND
}

enum class SyncStatus {
    SYNCED, LOCAL_ONLY, DELETED_LOCALLY
}

@Serializable
data class Recurrence(
    val daysOfWeek: List<DayOfWeek>,
    @Serializable(with = LocalDateSerializer::class)
    val startDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class)
    val endDate: LocalDate
)

sealed interface Event {
    val id: String?
    val title: String
    val source: EventSource
    val category: AcademicCategory
    val syncStatus: SyncStatus
    val updatedAt: Long

    /**
     * Checks if this event overlaps with another event in time.
     */
    fun overlaps(other: Event): Boolean
}

@Serializable
data class TimeEvent(
    override val id: String? = null,
    override val title: String,
    override val source: EventSource,
    override val category: AcademicCategory = AcademicCategory.REGULAR,
    override val syncStatus: SyncStatus = SyncStatus.SYNCED,
    override val updatedAt: Long = 0,
    @Serializable(with = LocalTimeSerializer::class)
    val startTime: LocalTime,
    @Serializable(with = LocalTimeSerializer::class)
    val endTime: LocalTime,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val recurrence: Recurrence? = null
) : Event {
    override fun overlaps(other: Event): Boolean {
        if (other is DayEvent) return other.date == this.date
        if (other is TimeEvent) {
            if (other.date != this.date) return false
            // Standard overlap check: (StartA < EndB) and (EndA > StartB)
            return this.startTime < other.endTime && this.endTime > other.startTime
        }
        return false
    }
}

@Serializable
data class DayEvent(
    override val id: String? = null,
    override val title: String,
    override val source: EventSource,
    override val category: AcademicCategory = AcademicCategory.REGULAR,
    override val syncStatus: SyncStatus = SyncStatus.SYNCED,
    override val updatedAt: Long = 0,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val recurrence: Recurrence? = null
) : Event {
    override fun overlaps(other: Event): Boolean {
        val otherDate = when(other) {
            is TimeEvent -> other.date
            is DayEvent -> other.date
        }
        return this.date == otherDate
    }
}
