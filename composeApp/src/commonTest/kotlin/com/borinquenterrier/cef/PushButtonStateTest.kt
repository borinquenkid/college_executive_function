package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PushButtonStateTest : FunSpec({

    context("variant") {
        test("conflicts take priority over connected") {
            PushButtonState.variant(hasConflicts = true, isConnected = true) shouldBe PushButtonVariant.CONFLICT
        }

        test("conflict with disconnected is still CONFLICT") {
            PushButtonState.variant(hasConflicts = true, isConnected = false) shouldBe PushButtonVariant.CONFLICT
        }

        test("no conflict and connected is GOOGLE") {
            PushButtonState.variant(hasConflicts = false, isConnected = true) shouldBe PushButtonVariant.GOOGLE
        }

        test("no conflict and not connected is LOCAL") {
            PushButtonState.variant(hasConflicts = false, isConnected = false) shouldBe PushButtonVariant.LOCAL
        }
    }

    context("label") {
        test("CONFLICT variant produces force-sync label") {
            PushButtonState.label(PushButtonVariant.CONFLICT) shouldBe "Force Sync Remaining"
        }

        test("GOOGLE variant produces google calendar label") {
            PushButtonState.label(PushButtonVariant.GOOGLE) shouldBe "Push to Google Calendar"
        }

        test("LOCAL variant produces local calendar label") {
            PushButtonState.label(PushButtonVariant.LOCAL) shouldBe "Save to Local Calendar"
        }
    }
})
