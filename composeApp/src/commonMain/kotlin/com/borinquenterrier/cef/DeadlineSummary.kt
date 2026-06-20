package com.borinquenterrier.cef

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class DeadlineSummary(val dueIn7Days: Int, val dueIn30Days: Int) {
    companion object {
        fun from(events: List<Event>, today: LocalDate): DeadlineSummary {
            val end7  = today.plus(7,  DateTimeUnit.DAY)
            val end30 = today.plus(30, DateTimeUnit.DAY)
            var count7 = 0; var count30 = 0
            for (event in events) {
                if (event.category != AcademicCategory.DEADLINE && event.category != AcademicCategory.FINALS) continue
                if (event.date in today..end7)  count7++
                if (event.date in today..end30) count30++
            }
            return DeadlineSummary(count7, count30)
        }
    }
}
