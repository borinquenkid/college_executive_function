package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

class SemesterResolverTest : FunSpec({

    test("getSemesterRange returns Fall semester (Aug 1 - Dec 31)") {
        val today1 = LocalDate(2026, 8, 15)
        val today2 = LocalDate(2026, 12, 1)

        val range1 = SemesterResolver.getSemesterRange(today1)
        val range2 = SemesterResolver.getSemesterRange(today2)

        range1 shouldBe (LocalDate(2026, 8, 1) to LocalDate(2026, 12, 31))
        range2 shouldBe (LocalDate(2026, 8, 1) to LocalDate(2026, 12, 31))
    }

    test("getSemesterRange returns Spring semester (Jan 1 - May 31)") {
        val today1 = LocalDate(2026, 1, 15)
        val today2 = LocalDate(2026, 5, 20)

        val range1 = SemesterResolver.getSemesterRange(today1)
        val range2 = SemesterResolver.getSemesterRange(today2)

        range1 shouldBe (LocalDate(2026, 1, 1) to LocalDate(2026, 5, 31))
        range2 shouldBe (LocalDate(2026, 1, 1) to LocalDate(2026, 5, 31))
    }

    test("getSemesterRange returns Interim/Summer semester (today - today + 30 days)") {
        val today = LocalDate(2026, 6, 15)
        val range = SemesterResolver.getSemesterRange(today)

        range shouldBe (LocalDate(2026, 6, 15) to LocalDate(2026, 7, 15))
    }
})
