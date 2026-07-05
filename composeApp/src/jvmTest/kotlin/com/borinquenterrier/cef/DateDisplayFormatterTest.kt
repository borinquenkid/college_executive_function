package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DateDisplayFormatterTest {

    @Test
    fun testFormatsSingleDigitDay() {
        assertEquals("Jul 5, 2026", DateDisplayFormatter.format(LocalDate(2026, 7, 5)))
    }

    @Test
    fun testFormatsDoubleDigitDay() {
        assertEquals("Aug 26, 2026", DateDisplayFormatter.format(LocalDate(2026, 8, 26)))
    }

    @Test
    fun testFormatsDecember() {
        assertEquals("Dec 31, 2026", DateDisplayFormatter.format(LocalDate(2026, 12, 31)))
    }

    @Test
    fun testFormatsJanuary() {
        assertEquals("Jan 1, 2027", DateDisplayFormatter.format(LocalDate(2027, 1, 1)))
    }
}
