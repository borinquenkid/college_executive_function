package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.LocalDate

class WeekdayDateConflictDetectorTest : FunSpec({

    fun ev(title: String, date: String): Event =
        DayEvent(title = title, source = EventSource.AI_GENERATED, date = LocalDate.parse(date))

    // Lines copied from the SPMS1185 fall 2026 syllabus (no year in the text). Nine mentions agree with 2026 and one,
    // "Friday, Nov. 21", does not: Nov 21, 2026 is a Saturday.
    val base = """
        Wk 1 - Aug 24 - 28
        Tue 8/25 - Paired introductions; introduction to the class; speaking basics
        Thur 8/27 - Re-introductions; example speech; syllabus; brainstorming exercise
        Tue 9/1 - Speech 1: Introduction Speech
        Thur 9/3 - Finish Speech 1; discuss speaking experience
        Tue 9/8 - Official speech review; audience analysis
        Thur 9/10 - TOPIC DUE BEFORE CLASS; Speech 1 feedback
        Tue 9/15 - PREP FORM DUE BEFORE CLASS; organizing and outlining
        Thur 9/17 - Library session in classroom; finding research sources
        Thur 11/19 - Optional Argumentative Speech day; rehearsal day; Online Speech due by 5 pm on Friday, Nov. 21
    """.trimIndent()
    val spms = base

    test("holds back the event on a contradictory weekday/date, whichever date the model chose") {
        for (guess in listOf("2026-11-20", "2026-11-21")) {
            val events = listOf(
                ev("Introduction Speech", "2026-09-01"),
                ev("Rehearsal day", "2026-11-19"),
                ev("Online Speech due", guess)
            )
            val (clean, held) = WeekdayDateConflictDetector.partition(events, spms)
            clean.map { it.title } shouldContainExactly listOf("Introduction Speech", "Rehearsal day")
            held.map { it.event.title } shouldContainExactly listOf("Online Speech due")
            held.single().event.date shouldBe LocalDate.parse(guess) // the model's date is not altered
            held.single().pushOnConfirm shouldBe true
            held.single().statedDate shouldBe LocalDate.parse("2026-11-21") // the date as written, not the model's
            DateResolutionPresenter.initialDate(held.single()) shouldBe LocalDate.parse("2026-11-21")
            held.single().reason!! shouldContain "Friday, Nov. 21"
            held.single().reason!! shouldContain "Saturday"
        }
    }

    test("the two readings of a bad date are the stated date and the date with that weekday") {
        val c = WeekdayDateConflictDetector.conflicts(spms, listOf(ev("x", "2026-11-20"))).single()
        c.year shouldBe 2026
        c.candidates shouldBe setOf(LocalDate.parse("2026-11-21"), LocalDate.parse("2026-11-20"))
    }

    test("every contradiction in one document is caught (the real file also has Tue 12/2 and Thur 12/4)") {
        val text = base + "\nTue 12/2 - Argumentative Speech 1\nThur 12/4 - Argumentative Speech 2\nTue 12/8 - Argumentative Speech 3"
        val events = listOf(
            ev("Argumentative Speech 1", "2026-12-02"), ev("Argumentative Speech 2", "2026-12-03"),
            ev("Argumentative Speech 3", "2026-12-08"), ev("Online Speech due", "2026-11-21")
        )
        val (clean, held) = WeekdayDateConflictDetector.partition(events, text)
        held.map { it.event.title }.toSet() shouldBe setOf("Argumentative Speech 1", "Argumentative Speech 2", "Online Speech due")
        clean.map { it.title } shouldContainExactly listOf("Argumentative Speech 3")
    }

    test("an unrelated event on the same date is not held") {
        val (clean, held) = WeekdayDateConflictDetector.partition(
            listOf(ev("Library closes early", "2026-11-21"), ev("Online Speech due", "2026-11-20")), spms
        )
        held.map { it.event.title } shouldContainExactly listOf("Online Speech due")
        clean.map { it.title } shouldContainExactly listOf("Library closes early")
    }

    test("a consistent document produces no conflicts") {
        val text = "Tue 9/1 - A\nThur 9/3 - B\nTue 9/8 - C\nFriday, December 18, 2026 - Final Exam"
        val events = listOf(ev("Final Exam", "2026-12-18"))
        WeekdayDateConflictDetector.partition(events, text) shouldBe (events to emptyList())
    }

    test("stays silent when the year is not clear: too few agreeing mentions") {
        val text = "Tue 9/1 - A\nThur 9/3 - B\nFriday, Nov. 21 - Online Speech due"
        val events = listOf(ev("Online Speech due", "2026-11-21"))
        WeekdayDateConflictDetector.partition(events, text) shouldBe (events to emptyList())
    }

    test("stays silent when most mentions disagree with the best year") {
        val text = "Mon 9/1 - A\nTue 9/3 - B\nWed 9/8 - C\nThu 9/9 - D\nTue 9/1 - E\nFri 9/3 - F"
        WeekdayDateConflictDetector.conflicts(text, listOf(ev("x", "2026-09-01"))) shouldBe emptyList()
    }

    test("reads abbreviations, full names and month names; ignores impossible dates") {
        val ms = WeekdayDateConflictDetector.mentions(
            "Thur 11/5 a\nFri, Nov. 20 b\nMonday, September 28, 2026 c\nTues Sept 8 d\nMon 2/30 e\nSat 13/1 f"
        )
        ms.map { it.month to it.day } shouldContainExactly listOf(11 to 5, 11 to 20, 9 to 28, 9 to 8, 2 to 30)
    }

    test("an impossible date like Feb 30 never crashes conflict detection") {
        val text = "Tue 9/1 - A\nThur 9/3 - B\nTue 9/8 - C\nMon 2/30 - impossible"
        WeekdayDateConflictDetector.conflicts(text, listOf(ev("x", "2026-09-01"))) shouldBe emptyList()
    }
})
