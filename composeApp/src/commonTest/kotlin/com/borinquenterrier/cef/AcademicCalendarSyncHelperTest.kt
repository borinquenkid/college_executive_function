package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

class AcademicCalendarSyncHelperTest : FunSpec({

    val negotiation = SyncNegotiation(
        proposals = emptyList(),
        remoteEventsToSync = emptyList(),
        deletedLocalIds = emptyList()
    )

    test("calls onNegotiation when initiateSync returns a negotiation") {
        runTest {
            var capturedNegotiation: SyncNegotiation? = null
            var refreshCalled = false

            performCalendarSync(
                initiateSync = { negotiation },
                refreshEvents = { emptyList<Event>().also { refreshCalled = true } },
                forceSync = true,
                onNegotiation = { capturedNegotiation = it },
                onEventsRefreshed = {}
            )

            capturedNegotiation shouldBe negotiation
            refreshCalled shouldBe false
        }
    }

    test("calls onEventsRefreshed when initiateSync returns null") {
        runTest {
            val events = listOf(
                DayEvent(
                    title = "Test",
                    date = kotlinx.datetime.LocalDate(2026, 9, 1),
                    source = EventSource.AI_GENERATED,
                    category = AcademicCategory.REGULAR
                )
            )
            var capturedEvents: List<Event>? = null
            var negotiationCalled = false

            performCalendarSync(
                initiateSync = { null },
                refreshEvents = { events },
                forceSync = false,
                onNegotiation = { negotiationCalled = true },
                onEventsRefreshed = { capturedEvents = it }
            )

            capturedEvents shouldBe events
            negotiationCalled shouldBe false
        }
    }

    test("passes forceSync value through to initiateSync") {
        runTest {
            var capturedForceSync: Boolean? = null

            performCalendarSync(
                initiateSync = { capturedForceSync = it; null },
                refreshEvents = { emptyList() },
                forceSync = false,
                onNegotiation = {},
                onEventsRefreshed = {}
            )
            capturedForceSync shouldBe false

            performCalendarSync(
                initiateSync = { capturedForceSync = it; null },
                refreshEvents = { emptyList() },
                forceSync = true,
                onNegotiation = {},
                onEventsRefreshed = {}
            )
            capturedForceSync shouldBe true
        }
    }
})
