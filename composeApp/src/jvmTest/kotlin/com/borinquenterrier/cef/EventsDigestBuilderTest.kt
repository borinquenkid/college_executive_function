package com.borinquenterrier.cef

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class EventsDigestBuilderTest : FunSpec({

    fun deadline(title: String, date: LocalDate, warning: String? = null) = DayEvent(
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE,
        date = date,
        warning = warning
    )

    val today = LocalDate(2026, 7, 1)

    test("near-term events are always present regardless of question wording") {
        val events = listOf(
            deadline("Hypothetical Twitter Feud Project", LocalDate(2026, 7, 10)),
            deadline("Quiz #5", LocalDate(2026, 7, 3))
        )

        // Question shares zero vocabulary with either title.
        val digest = EventsDigestBuilder.build(events, "completely unrelated wording", today)

        digest shouldNotBe null
        digest!! shouldContain "Hypothetical Twitter Feud Project"
        digest shouldContain "Quiz #5"
    }

    test("far-future events are included when the question matches their title") {
        val events = listOf(
            deadline("Quiz #5", LocalDate(2026, 7, 3)),
            deadline("Final Paper: Hidden Systems", LocalDate(2026, 9, 20))
        )

        val digest = EventsDigestBuilder.build(events, "when is the final paper due?", today)!!

        digest shouldContain "Final Paper: Hidden Systems"
    }

    test("far-future events match through synonym expansion, not just literal title words") {
        // The residual gap from the retrieval eval: student says "assignment", the calendar
        // says "Project", and the deadline is beyond the 14-day always-include window.
        val events = listOf(
            deadline("Quiz #5", LocalDate(2026, 7, 3)),
            deadline("Hypothetical Twitter Feud Project", LocalDate(2026, 9, 20))
        )

        val digest = EventsDigestBuilder.build(
            events,
            "When do I have to turn in the big history assignment where I make up a fake social media argument?",
            today
        )!!

        digest shouldContain "Hypothetical Twitter Feud Project"
    }

    test("far-future events with no title match are omitted") {
        val events = listOf(
            deadline("Quiz #5", LocalDate(2026, 7, 3)),
            deadline("Final Paper: Hidden Systems", LocalDate(2026, 9, 20))
        )

        val digest = EventsDigestBuilder.build(events, "zzz unrelated zzz", today)!!

        digest shouldContain "Quiz #5"
        digest shouldNotContain "Final Paper"
    }

    test("warning-bearing events render with the ⚠ marker; others do not") {
        val events = listOf(
            deadline("Inferred class meeting", LocalDate(2026, 7, 2), warning = "Added automatically"),
            deadline("Quiz #5", LocalDate(2026, 7, 3))
        )

        val digest = EventsDigestBuilder.build(events, "anything", today)!!
        val lines = digest.lines()

        lines.single { "Inferred class meeting" in it } shouldContain "⚠"
        lines.single { "Quiz #5" in it }.contains("⚠") shouldBe false
    }

    test("maxLines cap keeps the nearest events, dropped lines are the farthest") {
        val events = (1..20).map { deadline("Event $it", today.plus(DatePeriod(days = it % 14))) }

        val digest = EventsDigestBuilder.build(events, "anything", today, maxLines = 5)!!

        digest.lines().size shouldBe 5
        // Nearest-first priority: the earliest dates survive.
        digest shouldContain today.plus(DatePeriod(days = 1)).toString()
    }

    test("returns null for no events and for nothing selectable") {
        EventsDigestBuilder.build(emptyList(), "q", today) shouldBe null
        // One far event, no title overlap: nothing selectable.
        val far = listOf(deadline("Final Exam", LocalDate(2026, 12, 14)))
        EventsDigestBuilder.build(far, "zzz", today) shouldBe null
    }

    test("output is deterministic and chronologically ordered") {
        val events = listOf(
            deadline("B event", LocalDate(2026, 7, 5)),
            deadline("A event", LocalDate(2026, 7, 5)),
            deadline("C event", LocalDate(2026, 7, 2))
        )

        val digest1 = EventsDigestBuilder.build(events, "anything", today)!!
        val digest2 = EventsDigestBuilder.build(events.shuffled(), "anything", today)!!

        digest1 shouldBe digest2
        digest1.lines() shouldBe listOf(
            "2026-07-02 | DEADLINE | C event",
            "2026-07-05 | DEADLINE | A event",
            "2026-07-05 | DEADLINE | B event"
        )
    }

    test("TimeEvent lines include the start time") {
        val events = listOf(
            TimeEvent(
                title = "Midterm Exam",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.FINALS,
                date = LocalDate(2026, 7, 8),
                startTime = LocalTime(9, 0),
                endTime = LocalTime(11, 0)
            )
        )

        val digest = EventsDigestBuilder.build(events, "midterm", today)!!

        digest shouldContain "2026-07-08 09:00 | FINALS | Midterm Exam"
    }

    // The plan's hard gate (tasks/plan.md T3): for every deadline-stake question in the
    // retrieval eval fixture, when the corresponding event is inside the near-term window,
    // the digest MUST contain it — no matter how the student words the question. This is
    // the deterministic guarantee that chat deadline answers never depend on lexical luck.
    test("digest covers 100% of deadline-stake eval questions for in-window events") {
        val questionsFile = listOf(
            File("src/commonTest/resources/retrieval_eval_questions.json"),
            File("composeApp/src/commonTest/resources/retrieval_eval_questions.json")
        ).find { it.exists() } ?: error("retrieval eval fixture not found")

        // The calendar event each deadline-stake question is about (title + date as the
        // extraction pipeline would store them from the fixture syllabi).
        val eventByQuestionPrefix = mapOf(
            "When is the Primary Source Image Project due?" to
                deadline("Primary Source Image Project", LocalDate(2025, 5, 4)),
            "What is the last day to drop BDAN 250 without a grade?" to
                deadline("Drop without a grade", LocalDate(2025, 1, 28)),
            "When is Issue Brief #2 due?" to
                deadline("Issue Brief #2", LocalDate(2026, 7, 15)),
            "When is the PSYCH 101 Final Exam?" to
                deadline("PSYCH 101 Final Exam", LocalDate(2026, 12, 14)),
            "Which week is the Case Study Paper due?" to
                deadline("Case Study Paper", LocalDate(2026, 10, 28)),
            "When do I have to turn in the big history assignment where I make up a fake social media argument between two historical figures?" to
                deadline("Hypothetical Twitter Feud Project", LocalDate(2025, 5, 11)),
            "What's the deadline to get out of my analytics class without it showing up on my record?" to
                deadline("Drop without a grade", LocalDate(2025, 1, 28)),
            "When's the final essay for my English class due?" to
                deadline("Final Paper: Hidden Systems in American Justice", LocalDate(2026, 7, 29)),
            "When do I need to hand in my first write-up on the psych readings?" to
                deadline("Reading Response 1", LocalDate(2026, 9, 9))
        )

        val questions = Json.parseToJsonElement(questionsFile.readText()).jsonArray
            .map { it.jsonObject }
            .filter { it["stake"]!!.jsonPrimitive.content == "deadline" }

        questions.size shouldBe eventByQuestionPrefix.size

        questions.forEach { q ->
            val questionText = q["question"]!!.jsonPrimitive.content
            val event = eventByQuestionPrefix[questionText]
                ?: error("No event mapping for deadline question: $questionText")

            // The student asks 3 days before the deadline — the in-window scenario that
            // must never miss.
            val askDay = event.date.minus(DatePeriod(days = 3))
            val digest = EventsDigestBuilder.build(listOf(event), questionText, askDay)

            withClue("Digest missed in-window deadline for: $questionText") {
                (digest ?: "") shouldContain event.title
            }
        }
    }
})
