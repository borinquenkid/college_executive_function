package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.LocalDate

/**
 * Pure logic behind the date-picker dialog: the evidence text shown next to the picker, the
 * re-dating of a confirmed event, and the confirm/discard reducers over the two channels.
 */
class DateResolutionPresenterTest : FunSpec({

    fun deadline(title: String, date: String) =
        DayEvent(title = title, source = EventSource.AI_GENERATED, category = AcademicCategory.DEADLINE, date = LocalDate.parse(date))

    test("evidence text shows the source snippet when present") {
        val item = DateResolutionItem(deadline("Essay", "2026-09-01"), "Essay on witness protection due soon.")
        DateResolutionPresenter.evidenceText(item).shouldContain("witness protection")
    }

    test("evidence text warns when there is no source snippet (likely fabricated)") {
        val item = DateResolutionItem(deadline("Essay", "2026-09-01"), null)
        DateResolutionPresenter.evidenceText(item).shouldContain("invented")
    }

    test("withDate replaces the date on a DayEvent, preserving everything else") {
        val e = deadline("Essay", "2026-09-01")
        val moved = DateResolutionPresenter.withDate(e, LocalDate.parse("2026-07-15"))
        moved.date shouldBe LocalDate.parse("2026-07-15")
        moved.title shouldBe "Essay"
        moved.category shouldBe AcademicCategory.DEADLINE
    }

    test("confirm drops the item from pending and adds the re-dated event to the push list") {
        val item = DateResolutionItem(deadline("Essay", "2026-09-01"), "snippet")
        val (pending, push) = DateResolutionPresenter.confirm(
            pending = listOf(item),
            pushList = emptyList(),
            item = item,
            date = LocalDate.parse("2026-07-15"),
        )
        pending shouldBe emptyList()
        push.size shouldBe 1
        push[0].title shouldBe "Essay"
        push[0].date shouldBe LocalDate.parse("2026-07-15")
    }

    test("discard drops the item from pending without touching the push list") {
        val item = DateResolutionItem(deadline("Phantom", "2026-09-01"), null)
        DateResolutionPresenter.discard(listOf(item), item) shouldBe emptyList()
    }
})
