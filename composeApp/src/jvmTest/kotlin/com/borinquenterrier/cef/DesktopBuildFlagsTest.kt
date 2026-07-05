package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DesktopBuildFlagsTest : FunSpec({
    test("a packaged release build is never debug, regardless of flags") {
        computeIsDebug(isPackagedRelease = true, debugSystemProperty = null, debugEnvVar = null) shouldBe false
        computeIsDebug(isPackagedRelease = true, debugSystemProperty = "true", debugEnvVar = "true") shouldBe false
    }

    test("a non-packaged build defaults to debug when nothing is set") {
        computeIsDebug(isPackagedRelease = false, debugSystemProperty = null, debugEnvVar = null) shouldBe true
    }

    test("a non-packaged build honors an explicit debug=false system property") {
        computeIsDebug(isPackagedRelease = false, debugSystemProperty = "false", debugEnvVar = null) shouldBe false
    }

    test("a non-packaged build honors an explicit DEBUG=false env var") {
        computeIsDebug(isPackagedRelease = false, debugSystemProperty = null, debugEnvVar = "false") shouldBe false
    }
})
