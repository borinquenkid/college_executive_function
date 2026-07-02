package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

class CalendarReconcilerTest : FunSpec({

    fun day(
        title: String,
        date: String,
        id: String? = title,
        sync: SyncStatus = SyncStatus.SYNCED,
        updatedAt: Long = 1L,
        sourceId: String? = null
    ) = DayEvent(
        id = id,
        title = title,
        source = EventSource.STUDENT,
        category = AcademicCategory.DEADLINE,
        syncStatus = sync,
        updatedAt = updatedAt,
        sourceId = sourceId,
        date = LocalDate.parse(date)
    )

    val summer = StudyPreferences(semesterStart = "2026-06-01", semesterEnd = "2026-08-01")

    test("a healthy in-term calendar reports clean") {
        val events = listOf(
            day("Issue Brief #1 due", "2026-07-01"),
            day("Final Paper", "2026-07-31")
        )
        val report = CalendarReconciler.analyze(events, summer)
        report.isClean.shouldBeTrue()
        report.totalIssues shouldBe 0
    }

    test("flags exact duplicates and keeps the best copy") {
        val kept = day("Issue Brief #1 due", "2026-07-01", id = "real", sync = SyncStatus.SYNCED)
        val dup = day("Issue Brief #1 due", "2026-07-01", id = null, sync = SyncStatus.LOCAL_ONLY)
        val report = CalendarReconciler.analyze(listOf(kept, dup), summer)
        report.duplicatesToDelete shouldContainExactlyInAnyOrder listOf(dup) // synced/id'd copy kept
    }

    test("does NOT treat same title on different dates as a duplicate") {
        val a = day("Study Block: Draft", "2026-07-10")
        val b = day("Study Block: Draft", "2026-07-12")
        CalendarReconciler.analyze(listOf(a, b), summer).duplicatesToDelete shouldBe emptyList()
    }

    test("flags out-of-term events") {
        val inTerm = day("Issue Brief #1 due", "2026-07-01")
        val fall = day("Labor Day Holiday", "2026-09-07")
        val report = CalendarReconciler.analyze(listOf(inTerm, fall), summer)
        report.outOfSemesterToDelete shouldContainExactlyInAnyOrder listOf(fall)
    }

    test("flags updatedAt=0 events (the sync-churn root)") {
        val stale = day("Issue Brief #1 due", "2026-07-01", updatedAt = 0L)
        CalendarReconciler.analyze(listOf(stale), summer).timestampsToStamp shouldContainExactlyInAnyOrder listOf(stale)
    }

    test("an out-of-term event is deleted, not stamped, even if updatedAt=0") {
        val fallStale = day("Labor Day", "2026-09-07", updatedAt = 0L)
        val report = CalendarReconciler.analyze(listOf(fallStale), summer)
        report.outOfSemesterToDelete shouldContainExactlyInAnyOrder listOf(fallStale)
        report.timestampsToStamp shouldBe emptyList() // not double-counted
    }

    test("no semester configured → nothing flagged as out-of-term") {
        val events = listOf(day("Whenever", "2030-01-01"))
        CalendarReconciler.analyze(events, StudyPreferences()).outOfSemesterToDelete shouldBe emptyList()
    }

    test("flags orphans (source gone) but not tagged-to-existing or untagged events") {
        val events = listOf(
            day("Kept", "2026-07-01", id = "k", sourceId = "real.pdf"),
            day("Orphan", "2026-07-02", id = "o", sourceId = "ghost.pdf"),
            day("Manual", "2026-07-03", id = "m", sourceId = null)
        )
        val report = CalendarReconciler.analyze(events, summer, knownSourceIds = setOf("real.pdf"))
        report.orphansToDelete.map { it.id } shouldContainExactlyInAnyOrder listOf("o")
    }

    test("null knownSourceIds skips orphan detection entirely") {
        val events = listOf(day("Tagged", "2026-07-01", id = "t", sourceId = "whatever.pdf"))
        CalendarReconciler.analyze(events, summer, knownSourceIds = null).orphansToDelete shouldBe emptyList()
    }
})
