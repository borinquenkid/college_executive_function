package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EvalServiceNameTest : FunSpec({
    test("CI reports as cef-eval-ci, anything else as cef-eval-local") {
        evalServiceName("true") shouldBe "cef-eval-ci"
        evalServiceName(null) shouldBe "cef-eval-local"
        evalServiceName("") shouldBe "cef-eval-local"
    }
})
