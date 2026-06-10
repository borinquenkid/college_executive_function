package com.borinquenterrier.cef

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

enum class EventSource {
    ROUTINE, AI_GENERATED, MANUAL, STUDENT, SCHOOL, CLASS
}

enum class AcademicCategory {
    REGULAR, HOLIDAY, DEADLINE, FINALS, SEMESTER_BOUND, STUDY_BLOCK, CLASS
}

val AcademicCategory.priority: Int
    get() = when (this) {
        AcademicCategory.FINALS -> 100
        AcademicCategory.DEADLINE -> 90
        AcademicCategory.CLASS -> 80
        AcademicCategory.HOLIDAY -> 70
        AcademicCategory.SEMESTER_BOUND -> 60
        AcademicCategory.REGULAR -> 30
        AcademicCategory.STUDY_BLOCK -> 10
    }

enum class SyncStatus {
    SYNCED, LOCAL_ONLY, DELETED_LOCALLY
}

enum class CompletionStatus {
    INCOMPLETE, COMPLETED, SKIPPED
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
    val date: LocalDate
    val updatedAt: Long
    val warning: String? // Added for "Strict but Warn" capability
    val studyPlanStart: String?
    val gradeWeight: Float?
    val completionStatus: CompletionStatus

    val priority: Int
        get() = category.priority

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
    override val warning: String? = null,
    override val studyPlanStart: String? = null,
    override val gradeWeight: Float? = null,
    override val completionStatus: CompletionStatus = CompletionStatus.INCOMPLETE,
    @Serializable(with = LocalTimeSerializer::class)
    val startTime: LocalTime,
    @Serializable(with = LocalTimeSerializer::class)
    val endTime: LocalTime,
    @Serializable(with = LocalDateSerializer::class)
    override val date: LocalDate,
    val recurrence: Recurrence? = null
) : Event {
    override fun overlaps(other: Event): Boolean {
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
    override val warning: String? = null,
    override val studyPlanStart: String? = null,
    override val gradeWeight: Float? = null,
    override val completionStatus: CompletionStatus = CompletionStatus.INCOMPLETE,
    @Serializable(with = LocalDateSerializer::class)
    override val date: LocalDate,
    val recurrence: Recurrence? = null
) : Event {
    override fun overlaps(other: Event): Boolean {
        return false
    }
}

fun Event.withSyncStatus(status: SyncStatus): Event = when (this) {
    is TimeEvent -> copy(syncStatus = status)
    is DayEvent -> copy(syncStatus = status)
}

fun Event.withCompletionStatus(status: CompletionStatus): Event = when (this) {
    is TimeEvent -> copy(completionStatus = status)
    is DayEvent -> copy(completionStatus = status)
}

fun Event.timeUntilDue(
    currentDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
): Duration {
    return currentDate.daysUntil(this.date).days
}

fun Event.studyProgress(
    currentDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
): Float {
    if (category != AcademicCategory.DEADLINE && category != AcademicCategory.FINALS) {
        return 0f
    }
    val start = studyPlanStart?.let {
        try {
            LocalDate.parse(it)
        } catch (e: Exception) {
            null
        }
    } ?: date.minus(7, DateTimeUnit.DAY)

    val totalDays = start.daysUntil(date)
    if (totalDays <= 0) return 1f

    val elapsedDays = start.daysUntil(currentDate)
    if (elapsedDays <= 0) return 0f
    if (elapsedDays >= totalDays) return 1f

    return elapsedDays.toFloat() / totalDays.toFloat()
}
