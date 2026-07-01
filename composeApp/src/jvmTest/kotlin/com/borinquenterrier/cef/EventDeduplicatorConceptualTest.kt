package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

/**
 * Regression for the "conceptual duplicate" bug seen after ingesting the real
 * ENG 101 601 (Summer 2026) syllabus + companion docs: multiple prompt families
 * each emit an event for the same deliverable ("Issue Brief #2 due" AND
 * "DEADLINE: Issue Brief #2"), and the study-plan prompt leaks category labels
 * into the title ("STUDY_BLOCK: …").
 *
 * The dataset is the actual 31 events that landed in cef.db. Dedup must collapse
 * the same-deliverable pairs while preserving genuinely distinct same-date events
 * (a class session is not its own deadline).
 */
class EventDeduplicatorConceptualTest : FunSpec({

    fun day(title: String, date: String, category: AcademicCategory = AcademicCategory.REGULAR) =
        DayEvent(
            title = title,
            source = EventSource.STUDENT,
            category = category,
            date = LocalDate.parse(date)
        )

    // The real events, verbatim from the DB dump (category shown in [] in the dump).
    val realEvents: List<Event> = listOf(
        day("Class Session: Assign Issue Brief #2 & watch \"How FBI Undercover Agents Actually Work\"", "2026-07-01"),
        day("DEADLINE: Issue Brief #1", "2026-07-01"),
        day("Issue Brief #1 due", "2026-07-01"),
        day("Post to the Undercover Work Discussion", "2026-07-01"),
        day("Independence Day Holiday (College Closed)", "2026-07-03"),
        day("Reply to two posts in the Undercover Work Discussion", "2026-07-03"),
        day("Class Session: Connecting ideas from multiple sources", "2026-07-06"),
        day("Class Session: Discuss Issue Brief #2 Idea Worksheet", "2026-07-08"),
        day("Watch How FBI Undercover Agents Actually Work (Italian Mafia)", "2026-07-08"),
        day("Complete Connecting Sources Practice", "2026-07-10"),
        day("STUDY_BLOCK: Issue Brief #2 Drafting", "2026-07-10", AcademicCategory.REGULAR),
        day("STUDY_BLOCK: Issue Brief #2 Drafting", "2026-07-10", AcademicCategory.STUDY_BLOCK),
        day("REGULAR: Issue Brief #2 Draft Submission", "2026-07-12"),
        day("Submit Issue Brief #2 draft to Revision Interviews #2", "2026-07-12"),
        day("Class Session: Revision Interviews for Issue Brief #2", "2026-07-13"),
        day("Class Session: Assign Issue Brief #3 & watch “How Supermax Prisons Actually Work”", "2026-07-15"),
        day("DEADLINE: Issue Brief #2", "2026-07-15"),
        day("Issue Brief #2 due", "2026-07-15"),
        day("Post to the Connecting Hidden Systems Discussion", "2026-07-15"),
        day("Reply to the Connecting Hidden Systems Discussion", "2026-07-17"),
        day("Class Session: Connecting Hidden Systems Workshop & thesis statements", "2026-07-20"),
        day("Class Session: Review “writing essentials” & assign Final Paper: Hidden Systems in American Justice", "2026-07-22"),
        day("DEADLINE: Issue Brief #3", "2026-07-22"),
        day("Submit Issue Brief #3 (final draft)", "2026-07-22"),
        day("Complete the Final Paper Planning Guide", "2026-07-24"),
        day("REGULAR: Final Paper Planning Guide", "2026-07-24"),
        day("Class Session: Final Paper Work Session / Writing Center Option & submit progress check", "2026-07-27"),
        day("Class Session: Optional Final Paper Help Session", "2026-07-29"),
        day("Complete course reflection", "2026-07-29"),
        day("DEADLINE: Final Paper", "2026-07-31"),
        day("Submit Final Paper: Hidden Systems in American Justice", "2026-07-31")
    )

    test("no surviving title carries a leaked CATEGORY: prefix") {
        // stripLeakedCategoryPrefix is what EventGenerationService applies to display titles.
        val cleaned = realEvents.map { EventDeduplicator.stripLeakedCategoryPrefix(it.title) }
        cleaned.forEach { title ->
            listOf("DEADLINE:", "STUDY_BLOCK:", "REGULAR:", "HOLIDAY:", "FINALS:", "SEMESTER_BOUND:")
                .forEach { leak -> title.startsWith(leak) shouldBe false }
        }
        // "Class Session:" is NOT a leaked label — it must be preserved.
        EventDeduplicator.stripLeakedCategoryPrefix("Class Session: Discuss X") shouldBe "Class Session: Discuss X"
    }

    test("collapses same-deliverable pairs but keeps distinct same-date events") {
        // Titles are cleaned the same way the pipeline cleans them, then deduped.
        val cleaned = realEvents.map { e ->
            (e as DayEvent).copy(title = EventDeduplicator.stripLeakedCategoryPrefix(e.title))
        }
        val deduped = EventDeduplicator.dedup(cleaned)
        val titlesByDate = deduped.groupBy { EventDeduplicator.dateOf(it) }
            .mapValues { (_, v) -> v.map { it.title } }

        // Jul 1: the two IB#1 deadline phrasings collapse to one; class session + discussion survive.
        titlesByDate[LocalDate.parse("2026-07-01")]!!.count { it.contains("Issue Brief #1") } shouldBe 1
        titlesByDate[LocalDate.parse("2026-07-01")]!!.any { it.startsWith("Class Session") } shouldBe true
        titlesByDate[LocalDate.parse("2026-07-01")]!!.any { it.contains("Undercover Work Discussion") } shouldBe true

        // Jul 10: the duplicated study block collapses to one AND survives (not folded into the
        // Jul 15 deadline).
        titlesByDate[LocalDate.parse("2026-07-10")]!!.count { it.contains("Issue Brief #2 Drafting") } shouldBe 1

        // Jul 15: DEADLINE + "due" phrasings collapse; the IB#3 class session stays.
        titlesByDate[LocalDate.parse("2026-07-15")]!!.count { it.contains("Issue Brief #2") } shouldBe 1
        titlesByDate[LocalDate.parse("2026-07-15")]!!.any { it.contains("Issue Brief #3") } shouldBe true

        // Jul 22: IB#3 deadline / "final draft" collapse; the class session about Final Paper stays.
        titlesByDate[LocalDate.parse("2026-07-22")]!!.count { it.contains("Issue Brief #3") } shouldBe 1
        titlesByDate[LocalDate.parse("2026-07-22")]!!.any { it.startsWith("Class Session") } shouldBe true

        // Jul 24: "Final Paper Planning Guide" appears once.
        titlesByDate[LocalDate.parse("2026-07-24")]!!.count { it.contains("Final Paper Planning Guide") } shouldBe 1

        // Jul 31: the Final Paper deadline collapses to one (colon-subtitle case).
        titlesByDate[LocalDate.parse("2026-07-31")]!!.size shouldBe 1

        // Independence Day is a real distinct event and must survive.
        deduped.any { it.title.contains("Independence Day") } shouldBe true
    }

    test("does not over-merge: two different class sessions on nearby dates both survive") {
        val a = day("Class Session: Connecting ideas from multiple sources", "2026-07-06")
        val b = day("Class Session: Discuss Issue Brief #2 Idea Worksheet", "2026-07-08")
        val deduped = EventDeduplicator.dedup(listOf(a, b))
        deduped shouldContainExactlyInAnyOrder listOf(a, b)
    }
})
