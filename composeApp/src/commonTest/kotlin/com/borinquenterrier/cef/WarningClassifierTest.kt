package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarningClassifierTest {

    private val fallSemester = LocalDate(2026, 8, 1) to LocalDate(2026, 12, 31)
    private val springSemester = LocalDate(2027, 1, 1) to LocalDate(2027, 5, 31)

    // ── Pattern-based informational detection ─────────────────────────────

    @Test
    fun `specifies a range is informational`() {
        val w = "Source document specifies a range of 2026-10-12 to 2026-10-16."
        assertTrue(WarningClassifier.classify(w).endsWith("— You can ignore this."))
    }

    @Test
    fun `specifies range without article is informational`() {
        val w = "Source document specifies range 2026-12-14 to 2026-12-20."
        assertTrue(WarningClassifier.classify(w).endsWith("— You can ignore this."))
    }

    @Test
    fun `not explicitly stated is informational`() {
        val w = "Event not explicitly stated in source document; categorized as user-generated study block."
        assertTrue(WarningClassifier.classify(w).endsWith("— You can ignore this."))
    }

    @Test
    fun `interpreted as is informational`() {
        val w = "Date interpreted as the first Monday of March."
        assertTrue(WarningClassifier.classify(w).endsWith("— You can ignore this."))
    }

    // ── Actionable warnings are not modified ─────────────────────────────

    @Test
    fun `actionable warning is returned unchanged`() {
        val w = "Source document lists this as homework due for the next class session, not a formal deadline."
        assertEquals(w, WarningClassifier.classify(w))
    }

    @Test
    fun `missing exam date warning is returned unchanged`() {
        val w = "Missing exam date — professor has not posted it yet."
        assertEquals(w, WarningClassifier.classify(w))
    }

    // ── Semester-range filtering ──────────────────────────────────────────

    @Test
    fun `warning with dates inside active semester is returned unchanged`() {
        val w = "Conflicting dates: 2026-10-14 and 2026-10-16 both listed as due."
        assertEquals(w, WarningClassifier.classify(w, fallSemester))
    }

    @Test
    fun `warning with all dates outside active semester gets out-of-period label`() {
        val w = "Event on 2025-03-15 may conflict with spring break."
        val result = WarningClassifier.classify(w, fallSemester)
        assertTrue(result.contains("Out of current period"))
    }

    @Test
    fun `warning with mixed in-and-out dates is returned unchanged`() {
        val w = "Range spans 2025-12-01 to 2026-09-01 — ambiguous semester."
        // One date is inside fall 2026, so not flagged as out-of-period
        val result = WarningClassifier.classify(w, fallSemester)
        assertTrue(!result.contains("Out of current period"))
    }

    @Test
    fun `warning with no embedded dates ignores semester range`() {
        val w = "Document contains contradictory grading policy."
        assertEquals(w, WarningClassifier.classify(w, fallSemester))
    }

    // ── activeSemesterFrom ────────────────────────────────────────────────

    @Test
    fun `activeSemesterFrom returns fall range when today is in fall`() {
        val today = LocalDate(2026, 10, 1)
        val events = listOf(makeDayEvent(LocalDate(2026, 10, 15)))
        val range = WarningClassifier.activeSemesterFrom(events, today)
        assertEquals(LocalDate(2026, 8, 1), range?.first)
        assertEquals(LocalDate(2026, 12, 31), range?.second)
    }

    @Test
    fun `activeSemesterFrom returns summer range when today is in summer even with fall events`() {
        val today = LocalDate(2026, 6, 17)
        val events = listOf(
            makeDayEvent(LocalDate(2026, 10, 15)),
            makeDayEvent(LocalDate(2026, 11, 20))
        )
        val range = WarningClassifier.activeSemesterFrom(events, today)
        // Summer/interim: today to today+30, NOT fall
        assertEquals(today, range?.first)
    }

    @Test
    fun `activeSemesterFrom returns spring range when today is in spring`() {
        val today = LocalDate(2027, 2, 10)
        val events = listOf(makeDayEvent(LocalDate(2027, 2, 10)))
        val range = WarningClassifier.activeSemesterFrom(events, today)
        assertEquals(LocalDate(2027, 1, 1), range?.first)
        assertEquals(LocalDate(2027, 5, 31), range?.second)
    }

    @Test
    fun `activeSemesterFrom returns null for empty list`() {
        assertEquals(null, WarningClassifier.activeSemesterFrom(emptyList(), LocalDate(2026, 10, 1)))
    }

    private fun makeDayEvent(date: LocalDate) = DayEvent(
        title = "Test",
        date = date,
        category = AcademicCategory.DEADLINE,
        source = EventSource.AI_GENERATED
    )
}
