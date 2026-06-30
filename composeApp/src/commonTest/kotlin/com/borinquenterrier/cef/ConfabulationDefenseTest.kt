package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

/**
 * Pipeline-level defense harness: a scripted "LLM" ([ScriptedAIService]) is driven through the
 * real [GroundingGuardAIService] for every permutation of confabulation and exception, asserting
 * that (a) phantom events never survive grounding and (b) an LLM exception never turns into
 * fabricated events — it propagates.
 *
 * Each row is registered as its own parameterized test. The source text below references exactly
 * two real deliverable dates (2026-07-15, 2026-08-10) and only the year 2026, so any other date
 * or year in a scripted plan is, by construction, a confabulation that must be dropped.
 */
private const val SOURCE =
    "Spring 2026 syllabus. Essay due July 15, 2026. Final exam on August 10, 2026."

private fun day(category: AcademicCategory, date: String, title: String) =
    DayEvent(title = title, source = EventSource.AI_GENERATED, category = category, date = LocalDate.parse(date))

private fun deadline(title: String, date: String) = day(AcademicCategory.DEADLINE, date, title)
private fun finals(title: String, date: String) = day(AcademicCategory.FINALS, date, title)
private fun block(title: String, date: String) = day(AcademicCategory.STUDY_BLOCK, date, title)

private class Scenario(
    val name: String,
    val plan: () -> List<Event>,
    val expectedTitles: Set<String> = emptySet(),
    val expectThrows: Boolean = false,
)

private val SCENARIOS = listOf(
    Scenario(
        "valid deliverable + anchored study block both survive",
        { listOf(deadline("Essay", "2026-07-15"), block("Study for essay", "2026-07-13")) },
        expectedTitles = setOf("Essay", "Study for essay"),
    ),
    Scenario(
        "finals grounded by source date survives with its block",
        { listOf(finals("Final exam", "2026-08-10"), block("Study final", "2026-08-08")) },
        expectedTitles = setOf("Final exam", "Study final"),
    ),
    Scenario(
        "orphan study blocks with no deliverable are all dropped",
        { listOf(block("Study A", "2026-07-10"), block("Study B", "2026-07-12")) },
        expectedTitles = emptySet(),
    ),
    Scenario(
        "wrong-year deliverable and its block dropped by year grounding",
        { listOf(deadline("Old essay", "2099-07-15"), block("Study old", "2099-07-13")) },
        expectedTitles = emptySet(),
    ),
    Scenario(
        "fabricated-date deliverable dropped, orphaning its study block",
        { listOf(deadline("Phantom", "2026-09-22"), block("Study phantom", "2026-09-20")) },
        expectedTitles = emptySet(),
    ),
    Scenario(
        "mixed real and fabricated keeps only the grounded chain",
        {
            listOf(
                deadline("Essay", "2026-07-15"), block("Study for essay", "2026-07-13"),
                deadline("Phantom", "2026-09-22"), block("Study phantom", "2026-09-20"),
            )
        },
        expectedTitles = setOf("Essay", "Study for essay"),
    ),
    Scenario(
        "totally confabulated plan is fully rejected",
        {
            listOf(
                deadline("Fake1", "2026-12-01"),   // year ok, date not in source -> date-dropped
                deadline("Fake2", "2030-01-01"),   // wrong year -> year-dropped
                block("Study fake", "2026-11-28"),  // orphaned once Fake1 drops
            )
        },
        expectedTitles = emptySet(),
    ),
    Scenario(
        "empty plan stays empty",
        { emptyList() },
        expectedTitles = emptySet(),
    ),
    Scenario(
        "LLM timeout exception propagates, never becomes events",
        { throw RuntimeException("Socket timeout has expired") },
        expectThrows = true,
    ),
    Scenario(
        "LLM generic failure propagates, never becomes events",
        { throw IllegalStateException("model returned garbage") },
        expectThrows = true,
    ),
)

class ConfabulationDefenseTest : FunSpec({

    // Mirrors EventGenerationService.generateStudyPlan: GroundingGuard year-grounds the scripted
    // LLM output, then StudyPlanResolver applies date + anchor grounding and the resolution split.
    suspend fun runPlan(plan: () -> List<Event>): StudyPlanResult {
        val yearGrounded = GroundingGuardAIService(ScriptedAIService(onStudyPlan = plan))
            .generateStudyPlan(SOURCE, "", StudyPreferences())
        return StudyPlanResolver.resolve(yearGrounded, SOURCE)
    }

    SCENARIOS.forEach { scenario ->
        test(scenario.name) {
            if (scenario.expectThrows) {
                shouldThrow<Throwable> { runPlan(scenario.plan) }
            } else {
                val result = runPlan(scenario.plan)
                withClue(
                    "grounded=${result.grounded.map { it.title }} " +
                        "needsResolution=${result.needsResolution.map { it.event.title }}"
                ) {
                    result.grounded.map { it.title }.toSet() shouldBe scenario.expectedTitles
                }
            }
        }
    }
})
