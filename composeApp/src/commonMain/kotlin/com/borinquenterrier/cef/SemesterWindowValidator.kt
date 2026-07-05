package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/** Cross-field validation for a semester start/end window picked via calendar picker. */
object SemesterWindowValidator {
    fun validate(start: LocalDate?, end: LocalDate?): String? = when {
        start != null && end != null && end < start -> "End date must be after start date"
        else -> null
    }
}
