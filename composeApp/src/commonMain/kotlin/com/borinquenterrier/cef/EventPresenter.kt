package com.borinquenterrier.cef

import androidx.compose.ui.graphics.Color

object EventPresenter {
    enum class DeadlineStatus {
        OVERDUE,
        DUE_TODAY,
        FUTURE
    }

    fun getEventBorderColor(category: AcademicCategory, source: EventSource): Color {
        return when (category) {
            AcademicCategory.HOLIDAY -> Color(0xFFFF5252) // Red-ish for Holidays
            AcademicCategory.DEADLINE -> Color(0xFFFF9800) // Orange for Deadlines
            AcademicCategory.FINALS -> Color(0xFF9C27B0) // Purple for Finals
            AcademicCategory.SEMESTER_BOUND -> Color(0xFF607D8B) // Grey for Start/End
            AcademicCategory.STUDY_BLOCK -> Color(0xFF8BC34A) // Light Green for Study
            AcademicCategory.CLASS -> Color(0xFF3F51B5) // Indigo for Class
            AcademicCategory.REGULAR -> when (source) {
                EventSource.ROUTINE -> Color(0xFF4CAF50) // Green
                EventSource.AI_GENERATED -> Color(0xFF2196F3) // Blue
                EventSource.MANUAL -> Color(0xFFBDBDBD) // Light Grey
                EventSource.STUDENT -> Color(0xFFE91E63) // Pink
                EventSource.SCHOOL -> Color(0xFF9E9E9E) // Grey
                EventSource.CLASS -> Color(0xFF3F51B5) // Indigo
            }
        }
    }

    fun getCategoryLabel(category: AcademicCategory, source: EventSource): String {
        return when (category) {
            AcademicCategory.HOLIDAY -> "Holiday/Break"
            AcademicCategory.DEADLINE -> "Important Deadline"
            AcademicCategory.FINALS -> "Finals Week"
            AcademicCategory.SEMESTER_BOUND -> "Semester Boundary"
            AcademicCategory.STUDY_BLOCK -> "Suggested Study Period"
            AcademicCategory.CLASS -> "Scheduled Class"
            AcademicCategory.REGULAR -> when (source) {
                EventSource.ROUTINE -> "Class/Routine"
                EventSource.AI_GENERATED -> "Homework/Assignment"
                EventSource.MANUAL -> "School Calendar"
                EventSource.STUDENT -> "Personal"
                EventSource.SCHOOL -> "Institutional"
                EventSource.CLASS -> "Course Item"
            }
        }
    }

    fun getDeadlineStatus(daysUntil: Int): DeadlineStatus {
        return when {
            daysUntil < 0 -> DeadlineStatus.OVERDUE
            daysUntil == 0 -> DeadlineStatus.DUE_TODAY
            else -> DeadlineStatus.FUTURE
        }
    }

    fun getDeadlineChipText(daysUntil: Int): String {
        return when {
            daysUntil < 0 -> "Overdue by ${-daysUntil} day${if (-daysUntil != 1) "s" else ""}"
            daysUntil == 0 -> "Due Today"
            else -> "Due in $daysUntil day${if (daysUntil != 1) "s" else ""}"
        }
    }
}
