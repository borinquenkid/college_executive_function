package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

class AppNavigationServiceTest : FunSpec({
    test("should initialize with Home screen") {
        val testDispatcher = StandardTestDispatcher()
        TestScope(testDispatcher)
        val service = AppNavigationService()

        service.currentScreen.value shouldBe AppScreen.Home
    }

    test("should navigate to Calendar") {
        val testDispatcher = StandardTestDispatcher()
        TestScope(testDispatcher)
        val service = AppNavigationService()

        service.navigateTo(AppScreen.Calendar)

        service.currentScreen.value shouldBe AppScreen.Calendar
    }

    test("should navigate to Settings") {
        val testDispatcher = StandardTestDispatcher()
        TestScope(testDispatcher)
        val service = AppNavigationService()

        service.navigateTo(AppScreen.Settings)

        service.currentScreen.value shouldBe AppScreen.Settings
    }

    test("should support multiple navigation transitions") {
        val testDispatcher = StandardTestDispatcher()
        TestScope(testDispatcher)
        val service = AppNavigationService()

        service.navigateTo(AppScreen.Calendar)
        service.currentScreen.value shouldBe AppScreen.Calendar

        service.navigateTo(AppScreen.Settings)
        service.currentScreen.value shouldBe AppScreen.Settings

        service.navigateTo(AppScreen.Routine)
        service.currentScreen.value shouldBe AppScreen.Routine
    }

    test("should navigate back to Home") {
        val testDispatcher = StandardTestDispatcher()
        TestScope(testDispatcher)
        val service = AppNavigationService()

        service.navigateTo(AppScreen.Calendar)
        service.currentScreen.value shouldBe AppScreen.Calendar

        service.navigateTo(AppScreen.Home)
        service.currentScreen.value shouldBe AppScreen.Home
    }
})
