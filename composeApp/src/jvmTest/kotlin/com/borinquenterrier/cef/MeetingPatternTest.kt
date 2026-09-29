package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.THURSDAY
import kotlinx.datetime.DayOfWeek.TUESDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.io.File

class MeetingPatternTest : FunSpec({

    val root = listOf(File("../contributions"), File("contributions")).first { it.isDirectory }
    suspend fun fixture(entry: ContributionIndex): String =
        PdfReader().readSource(File(root, entry.relativePath).absolutePath).joinToString("\n") { it.text }

    val mwf = setOf(MONDAY, WEDNESDAY, FRIDAY)
    val tr = setOf(TUESDAY, THURSDAY)

    context("MeetingPatternParser on the Missouri S&T fixtures") {
        test("MATH 4209: labeled MoWeFr 9:00 wins over MWF 8:00 office hours") {
            MeetingPatternParser.parse(fixture(ContributionIndex.MST_MATH4209_SYLLABUS)) shouldBe
                MeetingPattern(mwf, LocalTime(9, 0), LocalTime(9, 50))
        }
        test("STAT 5643: MWF 12:00 lecture, not the Monday/Friday office hours or single-day exam lines") {
            MeetingPatternParser.parse(fixture(ContributionIndex.MST_STAT5643_SYLLABUS)) shouldBe
                MeetingPattern(mwf, LocalTime(12, 0), LocalTime(12, 50))
        }
        test("COMP SCI 2300: Tue & Thu 3:30-4:45, not Mon & Wed office hours") {
            MeetingPatternParser.parse(fixture(ContributionIndex.MST_CS2300_SYLLABUS)) shouldBe
                MeetingPattern(tr, LocalTime(15, 30), LocalTime(16, 45))
        }
        test("STAT 5353: time-before-days '1:00-1:50pm MWF'") {
            MeetingPatternParser.parse(fixture(ContributionIndex.MST_STAT5353_SYLLABUS)) shouldBe
                MeetingPattern(mwf, LocalTime(13, 0), LocalTime(13, 50))
        }
        test("SPMS 1185: two TR sections at different times -> days only") {
            MeetingPatternParser.parse(fixture(ContributionIndex.MST_SPMS1185_SYLLABUS)) shouldBe MeetingPattern(tr)
        }
        test("project description has no meeting pattern") {
            MeetingPatternParser.parse(fixture(ContributionIndex.MST_CS2300_PROJECT_DESCRIPTION)).shouldBeNull()
        }
    }

    context("MeetingPatternParser edge cases") {
        test("glued DOCX text '111TR 2:00:-3:15'") {
            MeetingPatternParser.parse("Principles of Speech (SPMS 1185Sect. 111TR 2:00:-3:15Innovation Lab 214)") shouldBe
                MeetingPattern(tr, LocalTime(14, 0), LocalTime(15, 15))
        }
        test("course label from glued DOCX text; room numbers are not course codes") {
            MeetingScheduleApplier.courseLabel("Principles of Speech (SPMS 1185Sect. 111TR 2:00") shouldBe "SPMS 1185"
            MeetingScheduleApplier.courseLabel("Location CS 00120").shouldBeNull()
        }
        test("plural day names; the office-hours line right after stays excluded (UT BIO 325L)") {
            val text = "Class Lecture: Mondays 1-2 PM in GEA 105\nOffice: PAI 1.48F Mondays: 2-2:30 after lecture"
            MeetingPatternParser.parse(text) shouldBe MeetingPattern(setOf(MONDAY), LocalTime(13, 0), LocalTime(14, 0))
        }
        test("'after lecture' inside office hours is not a meeting label (real UT BIO 325L spring PDF)") {
            val text = PdfReader().readSource(File(root, ContributionIndex.UT_BIO325L_GENETICS_LAB_SPRING.relativePath).absolutePath)
                .joinToString("\n") { it.text }
            MeetingPatternParser.parse(text) shouldBe MeetingPattern(setOf(MONDAY), LocalTime(13, 0), LocalTime(14, 0))
        }
        test("classes outside the source's own term bounds are dropped") {
            fun d(t: String, date: LocalDate, c: AcademicCategory) = DayEvent(title = t, source = EventSource.AI_GENERATED, category = c, date = date)
            val events = listOf(
                d("Class Lecture", LocalDate(2026, 1, 5), AcademicCategory.CLASS),        // model placeholder, shifted
                d("UT classes begin", LocalDate(2026, 1, 12), AcademicCategory.SEMESTER_BOUND),
                d("Lecture: Microscopy basics", LocalDate(2026, 1, 12), AcademicCategory.CLASS),
                d("Reading due", LocalDate(2026, 1, 8), AcademicCategory.DEADLINE),        // non-class kept
                d("Last class day", LocalDate(2026, 4, 27), AcademicCategory.SEMESTER_BOUND),
                d("Review session", LocalDate(2026, 4, 29), AcademicCategory.CLASS),
            )
            MeetingScheduleApplier.dropClassesOutsideOwnTerm(events).map { it.title } shouldBe
                listOf("UT classes begin", "Lecture: Microscopy basics", "Reading due", "Last class day")
            MeetingScheduleApplier.dropClassesOutsideOwnTerm(events.filter { it.category == AcademicCategory.CLASS }).size shouldBe 3
        }
        test("chapter and page ranges are not times") {
            MeetingPatternParser.parse("MWF Ch. 1.1–1.3 and pages 14–18").shouldBeNull()
        }
        test("11:00-12:15pm keeps the morning start") {
            MeetingPatternParser.parse("Lecture: TR 11:00-12:15pm") shouldBe MeetingPattern(tr, LocalTime(11, 0), LocalTime(12, 15))
        }
        test("out-of-order capitals are not day codes") {
            MeetingPatternParser.parseDays("FM").shouldBeNull()
            MeetingPatternParser.parseDays("MWF") shouldBe mwf
            MeetingPatternParser.parseDays("T/R") shouldBe tr
            MeetingPatternParser.parseDays("TuTh") shouldBe tr
            MeetingPatternParser.parseDays("Monday, Wednesday, and Friday") shouldBe mwf
        }
        test("office hours alone are not a meeting pattern") {
            MeetingPatternParser.parse("Office Hours: Wed/Fri 11:00am-12:00pm").shouldBeNull()
        }
    }

    context("MeetingScheduleApplier") {
        fun day(title: String, date: LocalDate, cat: AcademicCategory = AcademicCategory.CLASS) =
            DayEvent(title = title, source = EventSource.AI_GENERATED, category = cat, date = date)
        val window = LocalDate(2026, 8, 24) to LocalDate(2026, 12, 18)

        test("date-only class sessions on meeting days get the stated time; other days stay date-only") {
            val text = "Meeting Days & Time Tue & Thu 3:30 pm to 4:45 pm"
            val events = (listOf(8 to 25, 8 to 27, 9 to 1, 9 to 3, 9 to 8, 9 to 10)).map { (m, d) -> day("Lecture", LocalDate(2026, m, d)) } +
                day("Proposal due", LocalDate(2026, 9, 15), AcademicCategory.DEADLINE)
            val out = MeetingScheduleApplier.apply(events, text, window)
            out.size shouldBe events.size // not sparse -> no expansion
            out.filter { it.category == AcademicCategory.CLASS }.forEach {
                it.shouldBeInstanceOf<TimeEvent>().startTime shouldBe LocalTime(15, 30)
            }
            out.single { it.category == AcademicCategory.DEADLINE }.shouldBeInstanceOf<DayEvent>()
        }

        test("MATH 4209 fixture expands MWF 9:00 across the window, honoring the school calendar") {
            val text = fixture(ContributionIndex.MST_MATH4209_SYLLABUS)
            val own = listOf(day("Problem Set #1", LocalDate(2026, 9, 2), AcademicCategory.DEADLINE))
            val calendar = listOf(
                day("Classwork begins", LocalDate(2026, 8, 24), AcademicCategory.SEMESTER_BOUND),
                day("Labor Day Holiday", LocalDate(2026, 9, 7), AcademicCategory.HOLIDAY),
                day("Fall Break begins", LocalDate(2026, 10, 8), AcademicCategory.HOLIDAY),
                day("Veteran's Day Holiday", LocalDate(2026, 11, 11), AcademicCategory.HOLIDAY),
                day("Thanksgiving vacation begins", LocalDate(2026, 11, 22), AcademicCategory.HOLIDAY),
                day("Last Class Day - First Eight-week Session", LocalDate(2026, 10, 16), AcademicCategory.SEMESTER_BOUND),
                day("Last Class Day", LocalDate(2026, 12, 11), AcademicCategory.SEMESTER_BOUND),
                // A different course's cancellation must not cancel this one.
                day("No Class (Conference)", LocalDate(2026, 11, 2), AcademicCategory.HOLIDAY),
            )
            val out = MeetingScheduleApplier.apply(own, text, window, calendar)
            val sessions = out.filter { it.category == AcademicCategory.CLASS }.map { it as TimeEvent }
            val dates = sessions.map { it.date }

            sessions.first().title shouldBe "MATH 4209 class"
            sessions.all { it.startTime == LocalTime(9, 0) && it.endTime == LocalTime(9, 50) } shouldBe true
            sessions.all { it.warning!!.contains("stated meeting pattern") } shouldBe true
            dates.first() shouldBe LocalDate(2026, 8, 24)
            dates.last() shouldBe LocalDate(2026, 12, 11)
            dates shouldNotContain LocalDate(2026, 9, 7)   // Labor Day
            dates shouldNotContain LocalDate(2026, 10, 9)  // Fall Break (Thu 10/8 through the weekend)
            dates shouldContain LocalDate(2026, 10, 12)    // classes resume Monday
            dates shouldNotContain LocalDate(2026, 11, 11) // Veterans Day
            dates shouldNotContain LocalDate(2026, 11, 23) // Thanksgiving week
            dates shouldNotContain LocalDate(2026, 11, 27)
            dates shouldContain LocalDate(2026, 11, 2)     // other course's conference
            dates.all { it.dayOfWeek in mwf } shouldBe true
        }

        test("STAT 5643 fixture: own holidays, exam days, text break range and finals week are skipped") {
            val text = fixture(ContributionIndex.MST_STAT5643_SYLLABUS)
            val own = listOf(
                day("Labor Day (No Class)", LocalDate(2026, 9, 7), AcademicCategory.HOLIDAY),
                day("Exam 1", LocalDate(2026, 9, 28), AcademicCategory.FINALS),
                day("Fall Break (No Class)", LocalDate(2026, 10, 9), AcademicCategory.HOLIDAY),
                day("Exam 2", LocalDate(2026, 10, 30), AcademicCategory.FINALS),
                day("No Class (Conference)", LocalDate(2026, 11, 2), AcademicCategory.HOLIDAY),
                day("No Class (Conference)", LocalDate(2026, 11, 4), AcademicCategory.HOLIDAY),
                day("Veterans Day (No Class)", LocalDate(2026, 11, 11), AcademicCategory.HOLIDAY),
                // dedup keeps only one Thanksgiving date; the text's "Nov. 23–27" row covers the rest
                day("Thanksgiving Break - No Class", LocalDate(2026, 11, 27), AcademicCategory.HOLIDAY),
                day("Final Exam", LocalDate(2026, 12, 18), AcademicCategory.FINALS),
            )
            val dates = MeetingScheduleApplier.apply(own, text, window)
                .filter { it.category == AcademicCategory.CLASS }.map { it.date }
            dates shouldNotContain LocalDate(2026, 9, 28)
            dates shouldNotContain LocalDate(2026, 11, 2)
            dates shouldNotContain LocalDate(2026, 11, 4)
            dates shouldNotContain LocalDate(2026, 11, 23)
            dates shouldNotContain LocalDate(2026, 11, 25)
            dates shouldContainAll listOf(LocalDate(2026, 10, 5), LocalDate(2026, 10, 7), LocalDate(2026, 11, 6))
            dates.max() shouldBe LocalDate(2026, 12, 11) // stops before finals week
        }

        test("no semester window: term bounds come from the loaded school calendar") {
            val text = fixture(ContributionIndex.MST_MATH4209_SYLLABUS)
            val own = listOf(
                day("Problem Set #1", LocalDate(2026, 9, 2), AcademicCategory.DEADLINE),
                day("Problem Set #2", LocalDate(2026, 9, 26), AcademicCategory.DEADLINE),
            )
            val calendar = listOf(
                day("Classwork begins", LocalDate(2026, 8, 24), AcademicCategory.SEMESTER_BOUND),
                // FS2026 also lists 8-week sub-sessions; they must not narrow a 16-week course.
                day("Classwork begins - Second Eight-week Session", LocalDate(2026, 10, 19), AcademicCategory.SEMESTER_BOUND),
                day("12-Week Classes Begins", LocalDate(2026, 9, 21), AcademicCategory.SEMESTER_BOUND),
                day("Last Class Day. 11-Week courses", LocalDate(2026, 11, 6), AcademicCategory.SEMESTER_BOUND),
                day("Last Class Day - First Eight-week Session", LocalDate(2026, 10, 16), AcademicCategory.SEMESTER_BOUND),
                day("Last Class Day", LocalDate(2026, 12, 11), AcademicCategory.SEMESTER_BOUND),
                // A different term in the same calendar must not be picked.
                day("Classwork begins", LocalDate(2027, 1, 19), AcademicCategory.SEMESTER_BOUND),
                day("Last Class Day", LocalDate(2027, 5, 7), AcademicCategory.SEMESTER_BOUND),
            )
            val dates = MeetingScheduleApplier.apply(own, text, window = null, otherSourcesEvents = calendar)
                .filter { it.category == AcademicCategory.CLASS }.map { it.date }
            dates.min() shouldBe LocalDate(2026, 8, 24)
            dates.max() shouldBe LocalDate(2026, 12, 11)
        }

        test("corpus regression 2026-09-29: summer begin rows and another course's finals don't break the term") {
            // The exact bound rows MATH 4209 saw mid-corpus, where term came back null.
            val text = fixture(ContributionIndex.MST_MATH4209_SYLLABUS)
            val own = listOf(
                day("Problem Set #1", LocalDate(2026, 9, 2), AcademicCategory.DEADLINE),
                day("Problem Set #2", LocalDate(2026, 9, 26), AcademicCategory.DEADLINE),
                day("Quiz Exam Format Poll", LocalDate(2026, 9, 29), AcademicCategory.FINALS),
            )
            val others = listOf(
                "Final Exams" to LocalDate(2026, 5, 11),
                "Summer 3-Week and 11-Week Classes Begin" to LocalDate(2026, 5, 18),
                "Summer 6-Week and 8-Week Classes Begin" to LocalDate(2026, 6, 8),
                "12-Week Classes Begins" to LocalDate(2026, 9, 21),
                "Second 8-Week Classes Begin" to LocalDate(2026, 10, 19),
                "Final Exams" to LocalDate(2026, 12, 14),
                "First Day of Class: MTH 140S 605: Int Algebra with Support" to LocalDate(2026, 8, 24),
                "Last Day of Class" to LocalDate(2026, 12, 13),
                "Final Exam" to LocalDate(2026, 12, 16),
                "UT classes begin" to LocalDate(2025, 8, 25),
                "Project Demonstrations and Final Exam Review" to LocalDate(2026, 12, 8),
                "Final Exam" to LocalDate(2026, 12, 14),
            ).map { (t, d) -> day(t, d, AcademicCategory.SEMESTER_BOUND) }
            MeetingScheduleApplier.termFromCalendar(own, others) shouldBe (LocalDate(2026, 8, 24) to LocalDate(2026, 12, 13))
            val dates = MeetingScheduleApplier.apply(own, text, window = null, otherSourcesEvents = others)
                .filter { it.category == AcademicCategory.CLASS }.map { it.date }
            dates.min() shouldBe LocalDate(2026, 8, 24)
            dates.max() shouldBe LocalDate(2026, 12, 11) // last MWF before the institutional finals week of 12/14
        }

        test("expanding: an off-pattern class session from the model is dropped; with a real schedule it is kept") {
            val text = fixture(ContributionIndex.MST_MATH4209_SYLLABUS)
            val tuesday = day("MATH 4209 Class Session", LocalDate(2026, 9, 29)) // MWF course
            MeetingScheduleApplier.apply(listOf(tuesday), text, window)
                .none { it.date == LocalDate(2026, 9, 29) } shouldBe true

            val schedule = (0..5).map { day("Lecture $it", LocalDate(2026, 8, 24).plusDays(7 * it)) } + tuesday
            MeetingScheduleApplier.apply(schedule, text, window)
                .any { it.date == LocalDate(2026, 9, 29) } shouldBe true
        }

        test("no semester window and no school calendar -> no expansion") {
            val text = fixture(ContributionIndex.MST_MATH4209_SYLLABUS)
            val own = listOf(day("Problem Set #1", LocalDate(2026, 9, 2), AcademicCategory.DEADLINE))
            MeetingScheduleApplier.apply(own, text, window = null) shouldBe own
        }

        test("a syllabus with a real session schedule is not expanded") {
            val text = "Meeting Days & Time Tue & Thu 3:30 pm to 4:45 pm"
            val events = (1..6).map { day("Lecture $it", LocalDate(2026, 9, 1).plusDays(7 * it)) }
            MeetingScheduleApplier.apply(events, text, window).size shouldBe 6
        }

        test("breakSpan covers through the following weekend; a weekend start covers the next week") {
            MeetingScheduleApplier.breakSpan(LocalDate(2026, 10, 8)).last() shouldBe LocalDate(2026, 10, 11)
            MeetingScheduleApplier.breakSpan(LocalDate(2026, 11, 22)).last() shouldBe LocalDate(2026, 11, 29)
            MeetingScheduleApplier.breakSpan(LocalDate(2026, 11, 23)).last() shouldBe LocalDate(2026, 11, 29)
        }

        test("textBreakRanges skips week rows that only mention a one-day break") {
            val start = LocalDate(2026, 8, 24)
            MeetingScheduleApplier.textBreakRanges("3 Sept. 7–11 Labor Day (Sept. 7 – No Class); Ch. 1.5", start).shouldBeEmpty()
            MeetingScheduleApplier.textBreakRanges("14 Nov. 23–27 Thanksgiving Break – No Class", start).size shouldBe 5
        }
    }
})

private fun LocalDate.plusDays(n: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + n)
