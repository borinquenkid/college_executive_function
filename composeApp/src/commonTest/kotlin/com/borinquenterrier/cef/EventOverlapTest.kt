package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class EventOverlapTest : FunSpec({

    val date = LocalDate(2024, 9, 2)
    val otherDate = LocalDate(2024, 9, 3)

    test("TimeEvents on different days should not overlap") {
        val event1 = TimeEvent(title = "Class A", source = EventSource.CLASS, date = date, startTime = LocalTime(10, 0), endTime = LocalTime(11, 0))
        val event2 = TimeEvent(title = "Class B", source = EventSource.CLASS, date = otherDate, startTime = LocalTime(10, 0), endTime = LocalTime(11, 0))
        
        event1.overlaps(event2) shouldBe false
    }

    test("TimeEvents with overlapping time ranges on same day should overlap") {
        val event1 = TimeEvent(title = "Class A", source = EventSource.CLASS, date = date, startTime = LocalTime(10, 0), endTime = LocalTime(11, 0))
        val event2 = TimeEvent(title = "Class B", source = EventSource.CLASS, date = date, startTime = LocalTime(10, 30), endTime = LocalTime(11, 30))
        
        event1.overlaps(event2) shouldBe true
        event2.overlaps(event1) shouldBe true
    }

    test("TimeEvents that are back-to-back should not overlap") {
        val event1 = TimeEvent(title = "Class A", source = EventSource.CLASS, date = date, startTime = LocalTime(10, 0), endTime = LocalTime(11, 0))
        val event2 = TimeEvent(title = "Class B", source = EventSource.CLASS, date = date, startTime = LocalTime(11, 0), endTime = LocalTime(12, 0))
        
        event1.overlaps(event2) shouldBe false
    }

    test("DayEvent and TimeEvent on same day should overlap") {
        val holiday = DayEvent(title = "Labor Day", source = EventSource.SCHOOL, category = AcademicCategory.HOLIDAY, date = date)
        val lecture = TimeEvent(title = "Math 101", source = EventSource.CLASS, date = date, startTime = LocalTime(10, 0), endTime = LocalTime(11, 0))
        
        holiday.overlaps(lecture) shouldBe true
        lecture.overlaps(holiday) shouldBe true
    }

    test("DayEvents on same day should overlap") {
        val holiday1 = DayEvent(title = "Holiday A", source = EventSource.SCHOOL, date = date)
        val holiday2 = DayEvent(title = "Holiday B", source = EventSource.SCHOOL, date = date)
        
        holiday1.overlaps(holiday2) shouldBe true
    }

    test("Events on different days should never overlap") {
        val dayEvent = DayEvent(title = "Holiday", source = EventSource.SCHOOL, date = date)
        val timeEvent = TimeEvent(title = "Class", source = EventSource.CLASS, date = otherDate, startTime = LocalTime(10, 0), endTime = LocalTime(11, 0))
        
        dayEvent.overlaps(timeEvent) shouldBe false
    }
})
