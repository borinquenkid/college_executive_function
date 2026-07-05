package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SemesterWindowValidatorTest {

    @Test
    fun testEndBeforeStartIsInvalid() {
        val start = LocalDate(2026, 8, 26)
        val end = LocalDate(2026, 5, 1)

        assertEquals("End date must be after start date", SemesterWindowValidator.validate(start, end))
    }

    @Test
    fun testEndAfterStartIsValid() {
        val start = LocalDate(2026, 8, 26)
        val end = LocalDate(2026, 12, 13)

        assertNull(SemesterWindowValidator.validate(start, end))
    }

    @Test
    fun testEqualDatesAreValid() {
        val date = LocalDate(2026, 8, 26)

        assertNull(SemesterWindowValidator.validate(date, date))
    }

    @Test
    fun testNullStartIsValid() {
        assertNull(SemesterWindowValidator.validate(null, LocalDate(2026, 12, 13)))
    }

    @Test
    fun testNullEndIsValid() {
        assertNull(SemesterWindowValidator.validate(LocalDate(2026, 8, 26), null))
    }

    @Test
    fun testBothNullIsValid() {
        assertNull(SemesterWindowValidator.validate(null, null))
    }
}
