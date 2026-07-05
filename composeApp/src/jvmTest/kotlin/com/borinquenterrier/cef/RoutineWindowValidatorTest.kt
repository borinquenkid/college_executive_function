package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutineWindowValidatorTest {

    @Test
    fun testEndDateBeforeTodayIsFullyInPast() {
        val today = LocalDate(2026, 7, 4)
        val endDate = LocalDate(2024, 12, 13)

        assertTrue(RoutineWindowValidator.isFullyInPast(endDate, today))
    }

    @Test
    fun testEndDateEqualToTodayIsNotFullyInPast() {
        val today = LocalDate(2026, 7, 4)

        assertFalse(RoutineWindowValidator.isFullyInPast(today, today))
    }

    @Test
    fun testEndDateAfterTodayIsNotFullyInPast() {
        val today = LocalDate(2026, 7, 4)
        val endDate = LocalDate(2026, 12, 31)

        assertFalse(RoutineWindowValidator.isFullyInPast(endDate, today))
    }
}
