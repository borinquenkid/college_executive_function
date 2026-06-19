package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate

class CalendarSyncManagerTest : FunSpec({

    val logger = mockk<Logger>(relaxed = true)

    fun emptyNegotiation() = SyncNegotiation(
        proposals = emptyList(),
        remoteEventsToSync = emptyList(),
        deletedLocalIds = emptyList()
    )

    fun negotiationWith(vararg titles: String): SyncNegotiation {
        val proposals = titles.map { title ->
            val e = DayEvent(title = title, source = EventSource.AI_GENERATED,
                date = LocalDate(2026, 8, 1))
            SyncProposal.DirectConflict(localEvent = e, remoteEvent = e)
        }
        return SyncNegotiation(proposals = proposals,
            remoteEventsToSync = emptyList(), deletedLocalIds = emptyList())
    }

    // ── initiateSyncIfNeeded ──────────────────────────────────────────────────

    test("initiateSyncIfNeeded returns null immediately when not linked") {
        val agent = mockk<CalendarAgent>(relaxed = true)
        val manager = CalendarSyncManager(agent, logger)

        val result = manager.initiateSyncIfNeeded(isGoogleLinked = false)

        result shouldBe null
        coVerify(exactly = 0) { agent.checkSyncProposals(any()) }
    }

    test("initiateSyncIfNeeded returns null and auto-applies when no proposals") {
        val agent = mockk<CalendarAgent>(relaxed = true)
        coEvery { agent.checkSyncProposals(any()) } returns emptyNegotiation()
        val manager = CalendarSyncManager(agent, logger)

        val result = manager.initiateSyncIfNeeded(isGoogleLinked = true)

        result shouldBe null
        coVerify(exactly = 1) { agent.applySyncNegotiation(any(), any()) }
    }

    test("initiateSyncIfNeeded returns negotiation when proposals exist") {
        val agent = mockk<CalendarAgent>(relaxed = true)
        coEvery { agent.checkSyncProposals(any()) } returns negotiationWith("Event A", "Event B")
        val manager = CalendarSyncManager(agent, logger)

        val result = manager.initiateSyncIfNeeded(isGoogleLinked = true)

        result shouldNotBe null
        result!!.proposals.size shouldBe 2
        coVerify(exactly = 0) { agent.applySyncNegotiation(any(), any()) }
    }

    test("initiateSyncIfNeeded returns null on exception") {
        val agent = mockk<CalendarAgent>(relaxed = true)
        coEvery { agent.checkSyncProposals(any()) } throws RuntimeException("Network error")
        val manager = CalendarSyncManager(agent, logger)

        val result = manager.initiateSyncIfNeeded(isGoogleLinked = true)

        result shouldBe null
    }

    // ── applySyncProposal ─────────────────────────────────────────────────────

    test("applySyncProposal returns true on success") {
        val agent = mockk<CalendarAgent>(relaxed = true)
        val manager = CalendarSyncManager(agent, logger)

        val result = manager.applySyncProposal(emptyNegotiation())

        result shouldBe true
        coVerify(exactly = 1) { agent.applySyncNegotiation(any(), any()) }
    }

    test("applySyncProposal returns false on exception") {
        val agent = mockk<CalendarAgent>(relaxed = true)
        coEvery { agent.applySyncNegotiation(any(), any()) } throws RuntimeException("Write failed")
        val manager = CalendarSyncManager(agent, logger)

        val result = manager.applySyncProposal(emptyNegotiation())

        result shouldBe false
    }

    // ── refreshEvents ─────────────────────────────────────────────────────────

    test("refreshEvents returns events from agent") {
        val agent = mockk<CalendarAgent>(relaxed = true)
        val events = listOf(DayEvent(title = "Exam", source = EventSource.AI_GENERATED,
            date = LocalDate(2026, 8, 10)))
        coEvery { agent.getEvents(any()) } returns events
        val manager = CalendarSyncManager(agent, logger)

        val result = manager.refreshEvents()

        result shouldBe events
    }

    test("refreshEvents returns empty list on exception") {
        val agent = mockk<CalendarAgent>(relaxed = true)
        coEvery { agent.getEvents(any()) } throws RuntimeException("DB error")
        val manager = CalendarSyncManager(agent, logger)

        val result = manager.refreshEvents()

        result.shouldBeEmpty()
    }
})
