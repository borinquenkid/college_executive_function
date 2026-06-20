package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ConflictResolutionPresenterTest : FunSpec({

    test("bodyText includes the conflict count") {
        ConflictResolutionPresenter.bodyText(1) shouldContain "1 event(s)"
        ConflictResolutionPresenter.bodyText(5) shouldContain "5 event(s)"
    }

    test("bodyText mentions professor permission") {
        ConflictResolutionPresenter.bodyText(2) shouldContain "professor's permission"
    }

    test("instructionsText has three numbered steps") {
        val text = ConflictResolutionPresenter.instructionsText()
        text shouldContain "1."
        text shouldContain "2."
        text shouldContain "3."
    }

    test("hasReason returns false for blank and empty strings") {
        ConflictResolutionPresenter.hasReason("") shouldBe false
        ConflictResolutionPresenter.hasReason("   ") shouldBe false
        ConflictResolutionPresenter.hasReason("\t") shouldBe false
    }

    test("hasReason returns true for non-blank reason") {
        ConflictResolutionPresenter.hasReason("Quiz is fixed") shouldBe true
        ConflictResolutionPresenter.hasReason("  reason  ") shouldBe true
    }
})
