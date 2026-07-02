package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

class CalendarAgentReconcileTest : FunSpec({

    fun day(title: String, date: String, id: String, sync: SyncStatus = SyncStatus.SYNCED, updatedAt: Long = 1L) =
        DayEvent(
            id = id, title = title, source = EventSource.STUDENT,
            category = AcademicCategory.DEADLINE, syncStatus = sync, updatedAt = updatedAt,
            date = LocalDate.parse(date)
        )

    val summer = StudyPreferences(semesterStart = "2026-06-01", semesterEnd = "2026-08-01")

    fun agentWith(events: List<Event>): CalendarAgent {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        coEvery { localRepo.getAllEvents(any()) } returns events
        val prefsPort = mockk<PreferencesPort> { coEvery { getPreferences() } returns summer }
        return CalendarAgent(localRepo, mockk(relaxed = true), preferencesRepository = prefsPort)
    }

    test("reconcile detects duplicates, out-of-term, and stale-timestamp events") {
        val kept = day("Issue Brief #1 due", "2026-07-01", id = "keep", sync = SyncStatus.SYNCED)
        val dup = day("Issue Brief #1 due", "2026-07-01", id = "dup", sync = SyncStatus.LOCAL_ONLY)
        val fall = day("Labor Day Holiday", "2026-09-07", id = "fall")
        val stale = day("Issue Brief #2 due", "2026-07-15", id = "stale", updatedAt = 0L)

        val report = runBlocking { agentWith(listOf(kept, dup, fall, stale)).reconcile() }

        report.duplicatesToDelete.map { it.id } shouldContainExactlyInAnyOrder listOf("dup")
        report.outOfSemesterToDelete.map { it.id } shouldContainExactlyInAnyOrder listOf("fall")
        report.timestampsToStamp.map { it.id } shouldContainExactlyInAnyOrder listOf("stale")
    }

    test("reset suppresses sync mid-clear so it can't re-pull the remote, and fully clears") {
        val remote = FakeRemoteCalendar()
        remote.seed(
            listOf(
                day("A due", "2026-07-01", id = "a"),
                day("B due", "2026-07-02", id = "b"),
                day("C due", "2026-07-03", id = "c")
            )
        )
        // isLive() needs a run profile + a Google token; local reports itself already cleared.
        val settings = mockk<com.russhwolf.settings.Settings>(relaxed = true)
        every { settings.getString("run_profile", "local") } returns "local"
        every { settings.getString("GOOGLE_ACCESS_TOKEN", "") } returns "token"
        val local = mockk<StudentCalendarRepository>(relaxed = true)
        every { local.getSettings() } returns settings
        coEvery { local.getAllEvents(any()) } returns emptyList()

        val agent = CalendarAgent(local, remote, remoteClearDelayFn = {})

        // Control: outside a reset, a sync WOULD pull all three remote events down into local.
        runBlocking { agent.checkSyncProposals().remoteEventsToSync.map { it.id } }
            .shouldContainExactlyInAnyOrder(listOf("a", "b", "c"))

        // Capture what a sync sees at the exact moment the reset is deleting (isResetting == true).
        var midReset: SyncNegotiation? = null
        remote.beforeDelete = { if (midReset == null) midReset = runBlocking { agent.checkSyncProposals() } }

        runBlocking { agent.resetCalendar() }

        // The guard made the mid-reset sync a no-op → nothing to re-pull back into local.
        midReset!!.remoteEventsToSync.shouldBeEmpty()
        // And the reset actually cleared the remote.
        runBlocking { remote.getAllEvents("default") }.shouldBeEmpty()
    }

    test("applyReconciliation completes and bumps resetVersion so the UI refreshes") {
        val agent = agentWith(emptyList())
        val report = ReconciliationReport(
            duplicatesToDelete = listOf(day("dup", "2026-07-01", id = "d1")),
            timestampsToStamp = listOf(day("stale", "2026-07-02", id = "s1", updatedAt = 0L))
        )
        val before = agent.resetVersion.value
        runBlocking { agent.applyReconciliation(report) }
        agent.resetVersion.value shouldBeGreaterThan before
    }
})
