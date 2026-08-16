package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

class WeekAnchorDateResolverTest : FunSpec({

    fun fragment(text: String, metadata: Map<String, String> = emptyMap()) =
        SourceFragment(text = text, pageNumber = 1, type = SourceType.TEXT, metadata = metadata)

    // ── buildTable ─────────────────────────────────────────────────────────────

    test("buildTable maps each anchored week to its Monday") {
        val table = WeekAnchorDateResolver.buildTable(
            listOf(
                fragment("Week 1: June 8–14, 2026: Public Perception vs. Reality"),
                fragment("Week 6: July 13–19, 2026: Isolation, Punishment, and Public Fear")
            )
        )
        table shouldBe mapOf(
            1 to LocalDate(2026, 6, 8),
            6 to LocalDate(2026, 7, 13)
        )
    }

    test("buildTable handles a cross-month range") {
        val table = WeekAnchorDateResolver.buildTable(
            listOf(fragment("Week 4: June 29–July 5, 2026"))
        )
        table shouldBe mapOf(4 to LocalDate(2026, 6, 29))
    }

    test("buildTable snaps a Sunday-anchored range to its Monday") {
        // June 7, 2026 is a Sunday — some schedules anchor weeks Sun–Sat.
        val table = WeekAnchorDateResolver.buildTable(
            listOf(fragment("Week 1: June 7–13, 2026"))
        )
        table shouldBe mapOf(1 to LocalDate(2026, 6, 8))
    }

    test("buildTable resolves a yearless anchor using the single year stated by other anchors") {
        val table = WeekAnchorDateResolver.buildTable(
            listOf(
                fragment("Week 1: June 8–14"),
                fragment("Week 4: June 29–July 5, 2026")
            )
        )
        table shouldBe mapOf(
            1 to LocalDate(2026, 6, 8),
            4 to LocalDate(2026, 6, 29)
        )
    }

    test("buildTable skips yearless anchors when no anchor states a year") {
        WeekAnchorDateResolver.buildTable(
            listOf(fragment("Week 1: June 8–14"))
        ).shouldBeEmpty()
    }

    test("buildTable reads anchors injected into fragment metadata, not just fragment text") {
        // A pre-batched summary-table fragment carries the anchor table only via
        // WeekAnchorExtractor's metadata injection — its own text has no definitions.
        val table = WeekAnchorDateResolver.buildTable(
            listOf(
                fragment(
                    "Issue Brief #2 — Due Week 6",
                    metadata = mapOf("weekAnchors" to "Week 6: July 13–19, 2026")
                )
            )
        )
        table shouldBe mapOf(6 to LocalDate(2026, 7, 13))
    }

    test("buildTable accepts a plain hyphen range separator") {
        val table = WeekAnchorDateResolver.buildTable(
            listOf(fragment("Week 3: June 22 - June 28, 2026"))
        )
        table shouldBe mapOf(3 to LocalDate(2026, 6, 22))
    }

    // ── resolve ────────────────────────────────────────────────────────────────

    val table = mapOf(6 to LocalDate(2026, 7, 13))

    test("resolve overrides a model date shifted +1 day when a weekday label is present") {
        // 2026-08-16 eval regression: model treated Week 6 (July 13–19) as Sunday-anchored
        // and computed Wednesday as Jul 16. Truth: Wed Jul 15.
        WeekAnchorDateResolver.resolve(
            table, weekNumber = 6, dayName = "WEDNESDAY", modelDate = LocalDate(2026, 7, 16)
        ) shouldBe LocalDate(2026, 7, 15)
    }

    test("resolve maps each weekday label to its offset from Monday") {
        WeekAnchorDateResolver.resolve(table, 6, "MONDAY", LocalDate(2026, 7, 14)) shouldBe LocalDate(2026, 7, 13)
        WeekAnchorDateResolver.resolve(table, 6, "FRIDAY", LocalDate(2026, 7, 18)) shouldBe LocalDate(2026, 7, 17)
        WeekAnchorDateResolver.resolve(table, 6, "SUNDAY", LocalDate(2026, 7, 20)) shouldBe LocalDate(2026, 7, 19)
    }

    test("resolve accepts lowercase and abbreviated weekday labels") {
        WeekAnchorDateResolver.resolve(table, 6, "wednesday", LocalDate(2026, 7, 16)) shouldBe LocalDate(2026, 7, 15)
        WeekAnchorDateResolver.resolve(table, 6, "Wed", LocalDate(2026, 7, 16)) shouldBe LocalDate(2026, 7, 15)
    }

    test("resolve without a weekday label keeps a model date already inside the anchor week") {
        // Summary-table rows ("Due Week 6") carry no weekday; the model may know the day
        // from context we can't see, so an in-week date is trusted.
        WeekAnchorDateResolver.resolve(
            table, weekNumber = 6, dayName = null, modelDate = LocalDate(2026, 7, 15)
        ) shouldBe LocalDate(2026, 7, 15)
    }

    test("resolve without a weekday label snaps an out-of-week model date to Wednesday") {
        WeekAnchorDateResolver.resolve(
            table, weekNumber = 6, dayName = null, modelDate = LocalDate(2026, 7, 22)
        ) shouldBe LocalDate(2026, 7, 15)
    }

    test("resolve keeps the model date for a week missing from the table") {
        WeekAnchorDateResolver.resolve(
            table, weekNumber = 9, dayName = "MONDAY", modelDate = LocalDate(2026, 8, 3)
        ) shouldBe LocalDate(2026, 8, 3)
    }

    test("resolve keeps the model date when the weekday label is unparseable") {
        WeekAnchorDateResolver.resolve(
            table, weekNumber = 6, dayName = "midweekish", modelDate = LocalDate(2026, 7, 16)
        ) shouldBe LocalDate(2026, 7, 16)
    }
})
