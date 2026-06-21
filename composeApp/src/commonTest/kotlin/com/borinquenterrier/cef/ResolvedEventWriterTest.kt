package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate

class ResolvedEventWriterTest : FunSpec({

    fun day(title: String, date: LocalDate = LocalDate(2025, 10, 1), id: String? = title) =
        DayEvent(title = title, source = EventSource.AI_GENERATED, category = AcademicCategory.DEADLINE, date = date, id = id)

    fun localOnly(title: String, date: LocalDate = LocalDate(2025, 10, 1), id: String? = "$title-lo") =
        day(title, date, id).withSyncStatus(SyncStatus.LOCAL_ONLY)

    fun makeWriter(saveBlock: suspend (Event, String) -> Unit = { _, _ -> }): Pair<CalendarAgent, ResolvedEventWriter> {
        val agent = mockk<CalendarAgent>(relaxed = true)
        coEvery { agent.saveEvent(any(), any()) } coAnswers { saveBlock(firstArg(), secondArg()) }
        return Pair(agent, ResolvedEventWriter(agent, null))
    }

    // ── success path ──────────────────────────────────────────────────────────

    test("persist saves all resolved events and returns correct success count") {
        val (agent, writer) = makeWriter()
        val conflicts = mutableListOf<Event>()

        val (success, localOnly) = writer.persist(listOf(day("HW1"), day("HW2")), "default", conflicts)

        success shouldBe 2
        localOnly shouldBe 0
        conflicts.shouldBeEmpty()
        coVerify(exactly = 2) { agent.saveEvent(any(), "default") }
    }

    test("persist returns (0, 0) when resolved list is empty") {
        val (agent, writer) = makeWriter()
        val conflicts = mutableListOf<Event>()

        val (success, localOnly) = writer.persist(emptyList(), "default", conflicts)

        success shouldBe 0
        localOnly shouldBe 0
        coVerify(exactly = 0) { agent.saveEvent(any(), any()) }
    }

    // ── OverlapException ──────────────────────────────────────────────────────

    test("persist adds event to conflicts list on OverlapException") {
        val ev = day("HW1")
        val (_, writer) = makeWriter { _, _ -> throw OverlapException(ev, ev) }
        val conflicts = mutableListOf<Event>()

        val (success, _) = writer.persist(listOf(ev), "default", conflicts)

        success shouldBe 0
        conflicts shouldHaveSize 1
    }

    test("persist mixes successes and OverlapException conflicts correctly") {
        val agent = mockk<CalendarAgent>(relaxed = true)
        var callCount = 0
        val placeholder = day("p")
        coEvery { agent.saveEvent(any(), any()) } answers {
            if (++callCount == 2) throw OverlapException(placeholder, placeholder)
        }
        val writer = ResolvedEventWriter(agent, null)
        val conflicts = mutableListOf<Event>()

        val (success, _) = writer.persist(
            listOf(day("A", id = "a"), day("B", id = "b"), day("C", id = "c")),
            "default", conflicts
        )

        success shouldBe 2
        conflicts shouldHaveSize 1
    }

    // ── RemoteSyncFailedException ─────────────────────────────────────────────

    test("persist counts RemoteSyncFailedException as localOnly without adding to conflicts") {
        val ev = day("HW2")
        val (_, writer) = makeWriter { _, _ -> throw RemoteSyncFailedException("offline") }
        val conflicts = mutableListOf<Event>()

        val (success, localOnly) = writer.persist(listOf(ev), "default", conflicts)

        success shouldBe 0
        localOnly shouldBe 1
        conflicts.shouldBeEmpty()
    }

    // ── stale LOCAL_ONLY purge ────────────────────────────────────────────────

    test("persist hard-deletes stale LOCAL_ONLY events with same title and date before saving") {
        val stale = localOnly("HW3", id = "hw3-stale")
        val resolved = day("HW3")
        val (agent, writer) = makeWriter()
        val conflicts = mutableListOf<Event>()

        writer.persist(listOf(resolved), "default", conflicts, localOnlyExisting = listOf(stale))

        coVerify(exactly = 1) { agent.hardDeleteLocalOnly("hw3-stale", "default") }
        coVerify(exactly = 1) { agent.saveEvent(resolved, "default") }
    }

    test("persist does not hard-delete LOCAL_ONLY events that differ by date") {
        val stale = localOnly("HW4", date = LocalDate(2025, 11, 1), id = "hw4-stale")
        val resolved = day("HW4", date = LocalDate(2025, 10, 1))
        val (agent, writer) = makeWriter()
        val conflicts = mutableListOf<Event>()

        writer.persist(listOf(resolved), "default", conflicts, localOnlyExisting = listOf(stale))

        coVerify(exactly = 0) { agent.hardDeleteLocalOnly(any(), any()) }
    }

    test("persist does not hard-delete LOCAL_ONLY events that differ by title") {
        val stale = localOnly("Other", date = LocalDate(2025, 10, 1), id = "other-stale")
        val resolved = day("HW5", date = LocalDate(2025, 10, 1))
        val (agent, writer) = makeWriter()
        val conflicts = mutableListOf<Event>()

        writer.persist(listOf(resolved), "default", conflicts, localOnlyExisting = listOf(stale))

        coVerify(exactly = 0) { agent.hardDeleteLocalOnly(any(), any()) }
    }

    test("persist skips hard-delete when LOCAL_ONLY event has null id") {
        val stale = localOnly("HW6", id = null)
        val resolved = day("HW6")
        val (agent, writer) = makeWriter()
        val conflicts = mutableListOf<Event>()

        writer.persist(listOf(resolved), "default", conflicts, localOnlyExisting = listOf(stale))

        coVerify(exactly = 0) { agent.hardDeleteLocalOnly(any(), any()) }
        coVerify(exactly = 1) { agent.saveEvent(resolved, "default") }
    }
})
