package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

/**
 * Headless pipeline scenarios driven by [PipelineScenarioHarness] + [FakeRemoteCalendar]. Each is a
 * fast unit test that seeds a state, runs pipeline steps, and asserts local + remote — including
 * starting from an intermediate (drifted) state, which manual JVM runs can't reproduce reliably.
 *
 * NOTE: the harness scripts the LLM directly (ScriptedAIService), so these cover pipeline MECHANICS
 * (extract → normalize → semester filter → push → idempotency → sync → reconcile), not the grounding
 * decorators (GroundingGuard/CriticActor), which have their own tests.
 */
class PipelineScenariosTest : FunSpec({

    fun day(title: String, date: String, id: String? = null, updatedAt: Long = 1L) = DayEvent(
        id = id, title = title, source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE, updatedAt = updatedAt, date = LocalDate.parse(date)
    )
    fun titles(events: List<Event>) = events.map { it.title }

    // ── Core mechanics ────────────────────────────────────────────────────────

    test("happy path: ingest pushes to both local and remote") {
        val h = PipelineScenarioHarness()
        h.ingest("syllabus", listOf(day("Issue Brief #1 due", "2026-07-01"), day("Final Paper", "2026-07-31")))

        titles(h.localEvents()) shouldContainExactlyInAnyOrder listOf("Issue Brief #1 due", "Final Paper")
        titles(h.remoteEvents()) shouldContainExactlyInAnyOrder listOf("Issue Brief #1 due", "Final Paper")
    }

    test("idempotency: re-ingesting the same content does NOT duplicate on remote") {
        val h = PipelineScenarioHarness()
        val events = listOf(day("Issue Brief #1 due", "2026-07-01"))
        h.ingest("syllabus", events)
        h.ingest("syllabus", events) // same content again

        h.remoteEvents().size shouldBe 1
        h.localEvents().size shouldBe 1
    }

    test("semester filter: out-of-term events never reach the calendar") {
        val h = PipelineScenarioHarness()
        h.ingest("syllabus", listOf(day("In term", "2026-07-01"), day("Fall event", "2026-09-07")))

        titles(h.localEvents()) shouldContainExactlyInAnyOrder listOf("In term")
        titles(h.remoteEvents()) shouldContainExactlyInAnyOrder listOf("In term")
    }

    test("in-batch dedup: two identical extracted events collapse to one") {
        val h = PipelineScenarioHarness()
        h.ingest("syllabus", listOf(day("Issue Brief #1 due", "2026-07-01"), day("Issue Brief #1 due", "2026-07-01")))

        h.remoteEvents().size shouldBe 1
    }

    test("LLM failure: a throwing extraction leaves the calendar untouched, no crash") {
        val h = PipelineScenarioHarness()
        h.ingestFailing("syllabus", RuntimeException("LLM unavailable"))

        h.localEvents() shouldBe emptyList()
        h.remoteEvents() shouldBe emptyList()
    }

    // ── Start from an intermediate (drifted) state → detect & recover ──────────

    test("recovery: seed a polluted local calendar, reconcile, repair → clean") {
        val h = PipelineScenarioHarness()
        h.seedLocal(
            listOf(
                day("Issue Brief #1 due", "2026-07-01", id = "keep", updatedAt = 5),
                day("Issue Brief #1 due", "2026-07-01", id = "dup", updatedAt = 5),   // exact duplicate
                day("Labor Day Holiday", "2026-09-07", id = "fall", updatedAt = 5),   // out-of-term
                day("Issue Brief #2 due", "2026-07-15", id = "stale", updatedAt = 0)  // sync-churn source
            )
        )

        val report = h.reconcile()
        report.duplicatesToDelete.size shouldBe 1
        report.outOfSemesterToDelete.size shouldBe 1
        report.timestampsToStamp.size shouldBe 1

        h.repair(report)

        val local = h.localEvents()
        local.count { it.title == "Issue Brief #1 due" } shouldBe 1     // duplicate gone
        local.none { it.title == "Labor Day Holiday" } shouldBe true    // out-of-term gone
        local.first { it.title == "Issue Brief #2 due" }.updatedAt shouldBeGreaterThan 0L // stamped
    }

    test("sync: seeded remote-only events flow down into local") {
        val h = PipelineScenarioHarness()
        h.seedRemote(listOf(day("Remote deadline", "2026-07-10", id = "r1", updatedAt = 9)))
        h.sync()

        titles(h.localEvents()) shouldContainExactlyInAnyOrder listOf("Remote deadline")
    }

    // ── Permutations: initial state × extraction outcome ──────────────────────

    data class Case(
        val name: String,
        val seedLocal: List<Event>,
        val extract: List<Event>,
        val expectedTitles: List<String>
    )

    val inTerm = { t: String, d: String -> day(t, d) }
    val cases = listOf(
        Case("empty + clean extract",
            seedLocal = emptyList(),
            extract = listOf(inTerm("A", "2026-07-01"), inTerm("B", "2026-07-02")),
            expectedTitles = listOf("A", "B")),
        Case("empty + extract with an out-of-term event",
            seedLocal = emptyList(),
            extract = listOf(inTerm("A", "2026-07-01"), day("Fall", "2026-10-01")),
            expectedTitles = listOf("A")),
        Case("pre-seeded event + extract of a NEW event → both present",
            seedLocal = listOf(day("Pre", "2026-06-20", id = "pre", updatedAt = 3)),
            extract = listOf(inTerm("New", "2026-07-05")),
            expectedTitles = listOf("Pre", "New")),
        Case("pre-seeded event + extract of the SAME event → no duplicate",
            seedLocal = listOf(day("Same", "2026-07-05", id = "s", updatedAt = 3)),
            extract = listOf(inTerm("Same", "2026-07-05")),
            expectedTitles = listOf("Same")),
        Case("empty + duplicate extractions collapse",
            seedLocal = emptyList(),
            extract = listOf(inTerm("Dup", "2026-07-08"), inTerm("Dup", "2026-07-08")),
            expectedTitles = listOf("Dup"))
    )

    cases.forEach { case ->
        test("permutation: ${case.name}") {
            val h = PipelineScenarioHarness()
            if (case.seedLocal.isNotEmpty()) h.seedLocal(case.seedLocal)
            h.ingest("syllabus", case.extract)
            titles(h.localEvents()) shouldContainExactlyInAnyOrder case.expectedTitles
        }
    }

    test("generated events carry a real updatedAt (kills the sync-override churn)") {
        val h = PipelineScenarioHarness()
        h.ingest("syllabus", listOf(day("Issue Brief #1 due", "2026-07-01")))
        h.localEvents().forEach { it.updatedAt shouldBeGreaterThan 0L }
    }

    // ── Fault tolerance ───────────────────────────────────────────────────────

    test("remote save failure keeps the event locally as LOCAL_ONLY (not lost)") {
        val h = PipelineScenarioHarness()
        h.remote.beforeSave = { throw GoogleApiException(500, "remote down") }
        h.ingest("syllabus", listOf(day("Issue Brief #1 due", "2026-07-01")))

        val local = h.localEvents()
        titles(local) shouldContainExactlyInAnyOrder listOf("Issue Brief #1 due")
        local.single().syncStatus shouldBe SyncStatus.LOCAL_ONLY
        h.remoteEvents() shouldBe emptyList()
    }

    test("LOCAL_ONLY events are retried to remote and become synced when it recovers") {
        val h = PipelineScenarioHarness()
        h.remote.beforeSave = { throw GoogleApiException(500, "remote down") }
        h.ingest("syllabus", listOf(day("Issue Brief #1 due", "2026-07-01")))
        h.localEvents().single().syncStatus shouldBe SyncStatus.LOCAL_ONLY

        h.remote.beforeSave = {}   // remote recovers
        h.retryLocalOnly()

        h.localEvents().single().syncStatus shouldBe SyncStatus.SYNCED
        titles(h.remoteEvents()) shouldContainExactlyInAnyOrder listOf("Issue Brief #1 due")
    }

    test("multi-source: three sources merge; a shared deliverable is not duplicated") {
        val h = PipelineScenarioHarness()
        h.ingest("cal.ics", listOf(day("Independence Day Holiday", "2026-07-03")))
        h.ingest("syllabus-A", listOf(day("Issue Brief #1 due", "2026-07-01"), day("Independence Day Holiday", "2026-07-03")))
        h.ingest("syllabus-B", listOf(day("Final Paper", "2026-07-31")))

        titles(h.localEvents()) shouldContainExactlyInAnyOrder
            listOf("Independence Day Holiday", "Issue Brief #1 due", "Final Paper") // holiday once
    }

    test("reset clears local and remote") {
        val h = PipelineScenarioHarness()
        h.ingest("syllabus", listOf(day("A", "2026-07-01"), day("B", "2026-07-02")))
        h.reset()

        h.localEvents() shouldBe emptyList()
        h.remoteEvents() shouldBe emptyList()
    }

    test("reset survives a rate-limited remote and still clears everything (resilient clear)") {
        val h = PipelineScenarioHarness()
        h.seedRemote((1..5).map { day("Event $it", "2026-07-0$it", id = "e$it") })
        val failedOnce = mutableSetOf<String>()
        h.remote.beforeDelete = { id ->
            if (id == "e3" && failedOnce.add(id))
                throw GoogleApiException(403, """{"error":{"message":"Rate Limit Exceeded"}}""")
        }

        h.reset()

        h.remoteEvents() shouldBe emptyList() // rate-limited delete retried; nothing left behind
    }

    // ── Reconcile permutations: which drift each state produces ────────────────

    data class ReconcileCase(
        val name: String,
        val seed: List<Event>,
        val dups: Int, val outOfTerm: Int, val stale: Int
    )

    val reconcileCases = listOf(
        ReconcileCase("clean calendar",
            seed = listOf(day("A", "2026-07-01", id = "a"), day("B", "2026-07-02", id = "b")),
            dups = 0, outOfTerm = 0, stale = 0),
        ReconcileCase("one exact duplicate",
            seed = listOf(day("A", "2026-07-01", id = "a1"), day("A", "2026-07-01", id = "a2")),
            dups = 1, outOfTerm = 0, stale = 0),
        ReconcileCase("one out-of-term",
            seed = listOf(day("A", "2026-07-01", id = "a"), day("Fall", "2026-10-01", id = "f")),
            dups = 0, outOfTerm = 1, stale = 0),
        ReconcileCase("one stale timestamp",
            seed = listOf(day("A", "2026-07-01", id = "a", updatedAt = 0L)),
            dups = 0, outOfTerm = 0, stale = 1),
        ReconcileCase("all three at once",
            seed = listOf(
                day("A", "2026-07-01", id = "a1"), day("A", "2026-07-01", id = "a2"), // dup
                day("Fall", "2026-10-01", id = "f"),                                   // out-of-term
                day("B", "2026-07-02", id = "b", updatedAt = 0L)                       // stale
            ),
            dups = 1, outOfTerm = 1, stale = 1)
    )

    reconcileCases.forEach { case ->
        test("reconcile permutation: ${case.name}") {
            val h = PipelineScenarioHarness()
            h.seedLocal(case.seed)
            val r = h.reconcile()
            r.duplicatesToDelete.size shouldBe case.dups
            r.outOfSemesterToDelete.size shouldBe case.outOfTerm
            r.timestampsToStamp.size shouldBe case.stale
        }
    }
})
