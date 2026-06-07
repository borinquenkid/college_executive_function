package com.borinquenterrier.cef

import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EventPresenterTest : FunSpec({

    test("getEventBorderColor returns correct colors for categories and sources") {
        EventPresenter.getEventBorderColor(AcademicCategory.HOLIDAY, EventSource.MANUAL) shouldBe Color(0xFFFF5252)
        EventPresenter.getEventBorderColor(AcademicCategory.DEADLINE, EventSource.MANUAL) shouldBe Color(0xFFFF9800)
        EventPresenter.getEventBorderColor(AcademicCategory.REGULAR, EventSource.ROUTINE) shouldBe Color(0xFF4CAF50)
        EventPresenter.getEventBorderColor(AcademicCategory.REGULAR, EventSource.AI_GENERATED) shouldBe Color(0xFF2196F3)
    }

    test("getCategoryLabel returns correct labels for categories and sources") {
        EventPresenter.getCategoryLabel(AcademicCategory.HOLIDAY, EventSource.MANUAL) shouldBe "Holiday/Break"
        EventPresenter.getCategoryLabel(AcademicCategory.DEADLINE, EventSource.MANUAL) shouldBe "Important Deadline"
        EventPresenter.getCategoryLabel(AcademicCategory.REGULAR, EventSource.ROUTINE) shouldBe "Class/Routine"
        EventPresenter.getCategoryLabel(AcademicCategory.REGULAR, EventSource.AI_GENERATED) shouldBe "Homework/Assignment"
    }

    test("getDeadlineStatus maps daysUntil to correct DeadlineStatus") {
        EventPresenter.getDeadlineStatus(-1) shouldBe EventPresenter.DeadlineStatus.OVERDUE
        EventPresenter.getDeadlineStatus(-5) shouldBe EventPresenter.DeadlineStatus.OVERDUE
        EventPresenter.getDeadlineStatus(0) shouldBe EventPresenter.DeadlineStatus.DUE_TODAY
        EventPresenter.getDeadlineStatus(1) shouldBe EventPresenter.DeadlineStatus.FUTURE
        EventPresenter.getDeadlineStatus(10) shouldBe EventPresenter.DeadlineStatus.FUTURE
    }

    test("getDeadlineChipText returns correct text") {
        EventPresenter.getDeadlineChipText(-1) shouldBe "Overdue by 1 day"
        EventPresenter.getDeadlineChipText(-3) shouldBe "Overdue by 3 days"
        EventPresenter.getDeadlineChipText(0) shouldBe "Due Today"
        EventPresenter.getDeadlineChipText(1) shouldBe "Due in 1 day"
        EventPresenter.getDeadlineChipText(5) shouldBe "Due in 5 days"
    }
})
