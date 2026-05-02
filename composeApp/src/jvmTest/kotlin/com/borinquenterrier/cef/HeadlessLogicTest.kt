package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import com.russhwolf.settings.MapSettings
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.borinquenterrier.cef.db.DriverFactory
import kotlinx.coroutines.runBlocking

/**
 * Demonstrates that the application logic can now run "Headless"
 * without any Compose UI dependencies.
 */
class HeadlessLogicTest : FunSpec({

    test("Should be able to instantiate and run logic via DependencyContainer") {
        // 1. Setup Headless Dependencies
        val settings = MapSettings()
        val logger = Logger(settings)
        val driverFactory = DriverFactory() // Use real factory in test
        
        // 2. Initialize Container
        val container = DependencyContainer(
            settings = settings,
            logger = logger,
            driverFactory = driverFactory,
            modelBasePath = "/tmp/models"
        )

        // 3. Verify member access
        container.googleAccountFlow.error.value shouldBe null
        container.studioFlow.isLoading.value shouldBe false
        
        // 4. Run a simple headless operation
        val text = "Test Event on 2026-01-01"
        val parts = SourceProcessor.process(text)
        
        parts.size shouldBe 1
        parts[0].text shouldBe text
        
        println("Headless logic successfully verified via DependencyContainer.")
    }
})
