package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AppDirectoryTest : FunSpec({
    test("should resolve Mac OS X path to Library/Application Support") {
        val dir = getAppDirectory(
            osName = "Mac OS X",
            userHome = "/Users/testuser"
        )
        // Normalize slashes for comparison
        dir.path.replace('\\', '/') shouldBe "/Users/testuser/Library/Application Support/CollegeExecutiveFunction"
    }

    test("should resolve Windows path to APPDATA if present") {
        val dir = getAppDirectory(
            osName = "Windows 11",
            userHome = "/Users/testuser",
            envMap = mapOf("APPDATA" to "/Users/testuser/AppData/Roaming")
        )
        dir.path.replace('\\', '/') shouldBe "/Users/testuser/AppData/Roaming/CollegeExecutiveFunction"
    }

    test("should resolve Windows path to userHome if APPDATA is absent") {
        val dir = getAppDirectory(
            osName = "Windows 11",
            userHome = "/Users/testuser",
            envMap = emptyMap()
        )
        dir.path.replace('\\', '/') shouldBe "/Users/testuser/CollegeExecutiveFunction"
    }

    test("should resolve Linux path to XDG_DATA_HOME if present") {
        val dir = getAppDirectory(
            osName = "Linux",
            userHome = "/home/testuser",
            envMap = mapOf("XDG_DATA_HOME" to "/custom/data")
        )
        dir.path.replace('\\', '/') shouldBe "/custom/data/CollegeExecutiveFunction"
    }

    test("should resolve Linux path to default local share if XDG_DATA_HOME is absent") {
        val dir = getAppDirectory(
            osName = "Linux",
            userHome = "/home/testuser",
            envMap = emptyMap()
        )
        dir.path.replace('\\', '/') shouldBe "/home/testuser/.local/share/CollegeExecutiveFunction"
    }
})
