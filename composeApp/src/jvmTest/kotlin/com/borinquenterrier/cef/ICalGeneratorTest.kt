package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Uid
import java.io.StringReader

class ICalGeneratorTest : FunSpec({

    test("Should build calendar from events list and serialize to valid ICS") {
        val events = listOf(
            TimeEvent(
                id = "event-1",
                title = "CS 101 Lecture",
                source = EventSource.CLASS,
                category = AcademicCategory.CLASS,
                date = LocalDate(2026, 6, 8),
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 30)
            ),
            DayEvent(
                id = "event-2",
                title = "Math Assignment Due",
                source = EventSource.SCHOOL,
                category = AcademicCategory.DEADLINE,
                date = LocalDate(2026, 6, 9)
            )
        )

        val calendar = ICalGenerator.buildAcademicCalendar(events)
        calendar shouldNotBe null

        val icsString = ICalGenerator.calendarToString(calendar)
        icsString shouldNotBe null
        println("Generated ICS:\n$icsString")

        // Parse back using CalendarBuilder to verify validity
        val builder = CalendarBuilder()
        val parsedCalendar = builder.build(StringReader(icsString))
        parsedCalendar shouldNotBe null

        val parsedEvents = parsedCalendar.getComponents<VEvent>(Component.VEVENT)
        parsedEvents.size shouldBe 2

        val event1 = parsedEvents.find { it.summary.value == "CS 101 Lecture" }
        event1 shouldNotBe null

        val uidOpt1 = event1?.uid as? java.util.Optional<*>
        val uid1 = if (uidOpt1 != null && uidOpt1.isPresent) uidOpt1.get() as? Uid else null
        uid1?.value shouldBe "event-1"

        val desc1 = event1?.description
        desc1?.value shouldContain "Source: CLASS"
        desc1?.value shouldContain "Category: CLASS"

        val event2 = parsedEvents.find { it.summary.value == "Math Assignment Due" }
        event2 shouldNotBe null

        val uidOpt2 = event2?.uid as? java.util.Optional<*>
        val uid2 = if (uidOpt2 != null && uidOpt2.isPresent) uidOpt2.get() as? Uid else null
        uid2?.value shouldBe "event-2"

        val desc2 = event2?.description
        desc2?.value shouldContain "Source: SCHOOL"
        desc2?.value shouldContain "Category: DEADLINE"
    }
})
