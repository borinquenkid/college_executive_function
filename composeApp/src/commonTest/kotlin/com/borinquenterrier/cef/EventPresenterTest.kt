package com.borinquenterrier.cef

import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EventPresenterTest : FunSpec({

    test("getEventBorderColor returns correct colors for non-REGULAR categories") {
        EventPresenter.getEventBorderColor(
            AcademicCategory.HOLIDAY,
            EventSource.MANUAL
        ) shouldBe Color(0xFFFF5252)
        EventPresenter.getEventBorderColor(
            AcademicCategory.DEADLINE,
            EventSource.MANUAL
        ) shouldBe Color(0xFFFF9800)
        EventPresenter.getEventBorderColor(
            AcademicCategory.FINALS,
            EventSource.MANUAL
        ) shouldBe Color(0xFF9C27B0)
        EventPresenter.getEventBorderColor(
            AcademicCategory.SEMESTER_BOUND,
            EventSource.MANUAL
        ) shouldBe Color(0xFF607D8B)
        EventPresenter.getEventBorderColor(
            AcademicCategory.STUDY_BLOCK,
            EventSource.MANUAL
        ) shouldBe Color(0xFF8BC34A)
        EventPresenter.getEventBorderColor(
            AcademicCategory.CLASS,
            EventSource.MANUAL
        ) shouldBe Color(0xFF3F51B5)
    }

    test("getEventBorderColor covers all REGULAR source branches") {
        EventPresenter.getEventBorderColor(
            AcademicCategory.REGULAR,
            EventSource.ROUTINE
        ) shouldBe Color(0xFF4CAF50)
        EventPresenter.getEventBorderColor(
            AcademicCategory.REGULAR,
            EventSource.AI_GENERATED
        ) shouldBe Color(0xFF2196F3)
        EventPresenter.getEventBorderColor(
            AcademicCategory.REGULAR,
            EventSource.MANUAL
        ) shouldBe Color(0xFFBDBDBD)
        EventPresenter.getEventBorderColor(
            AcademicCategory.REGULAR,
            EventSource.STUDENT
        ) shouldBe Color(0xFFE91E63)
        EventPresenter.getEventBorderColor(
            AcademicCategory.REGULAR,
            EventSource.SCHOOL
        ) shouldBe Color(0xFF9E9E9E)
        EventPresenter.getEventBorderColor(
            AcademicCategory.REGULAR,
            EventSource.CLASS
        ) shouldBe Color(0xFF3F51B5)
    }

    test("getCategoryLabel returns correct labels for non-REGULAR categories") {
        EventPresenter.getCategoryLabel(
            AcademicCategory.HOLIDAY,
            EventSource.MANUAL
        ) shouldBe "Holiday/Break"
        EventPresenter.getCategoryLabel(
            AcademicCategory.DEADLINE,
            EventSource.MANUAL
        ) shouldBe "Important Deadline"
        EventPresenter.getCategoryLabel(
            AcademicCategory.FINALS,
            EventSource.MANUAL
        ) shouldBe "Finals Week"
        EventPresenter.getCategoryLabel(
            AcademicCategory.SEMESTER_BOUND,
            EventSource.MANUAL
        ) shouldBe "Semester Boundary"
        EventPresenter.getCategoryLabel(
            AcademicCategory.STUDY_BLOCK,
            EventSource.MANUAL
        ) shouldBe "Suggested Study Period"
        EventPresenter.getCategoryLabel(
            AcademicCategory.CLASS,
            EventSource.MANUAL
        ) shouldBe "Scheduled Class"
    }

    test("getCategoryLabel covers all REGULAR source branches") {
        EventPresenter.getCategoryLabel(
            AcademicCategory.REGULAR,
            EventSource.ROUTINE
        ) shouldBe "Class/Routine"
        EventPresenter.getCategoryLabel(
            AcademicCategory.REGULAR,
            EventSource.AI_GENERATED
        ) shouldBe "Homework/Assignment"
        EventPresenter.getCategoryLabel(
            AcademicCategory.REGULAR,
            EventSource.MANUAL
        ) shouldBe "School Calendar"
        EventPresenter.getCategoryLabel(
            AcademicCategory.REGULAR,
            EventSource.STUDENT
        ) shouldBe "Personal"
        EventPresenter.getCategoryLabel(
            AcademicCategory.REGULAR,
            EventSource.SCHOOL
        ) shouldBe "Institutional"
        EventPresenter.getCategoryLabel(
            AcademicCategory.REGULAR,
            EventSource.CLASS
        ) shouldBe "Course Item"
    }

    test("getDeadlineStatus maps daysUntil to correct DeadlineStatus") {
        EventPresenter.getDeadlineStatus(-1) shouldBe EventPresenter.DeadlineStatus.OVERDUE
        EventPresenter.getDeadlineStatus(-5) shouldBe EventPresenter.DeadlineStatus.OVERDUE
        EventPresenter.getDeadlineStatus(0) shouldBe EventPresenter.DeadlineStatus.DUE_TODAY
        EventPresenter.getDeadlineStatus(1) shouldBe EventPresenter.DeadlineStatus.FUTURE
        EventPresenter.getDeadlineStatus(10) shouldBe EventPresenter.DeadlineStatus.FUTURE
    }

    test("getDeadlineChipText returns correct text including pluralization") {
        EventPresenter.getDeadlineChipText(-1) shouldBe "Overdue by 1 day"
        EventPresenter.getDeadlineChipText(-3) shouldBe "Overdue by 3 days"
        EventPresenter.getDeadlineChipText(0) shouldBe "Due Today"
        EventPresenter.getDeadlineChipText(1) shouldBe "Due in 1 day"
        EventPresenter.getDeadlineChipText(5) shouldBe "Due in 5 days"
    }
})
