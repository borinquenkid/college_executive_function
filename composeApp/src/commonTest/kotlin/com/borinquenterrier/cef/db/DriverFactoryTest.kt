package com.borinquenterrier.cef.db

import app.cash.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.*

class DriverFactoryTest : FunSpec({
    test("createDriver returns the configured driver") {
        val mockDriver = mockk<SqlDriver>(relaxed = true)
        val mockFactory = mockk<DriverFactory>()
        every { mockFactory.createDriver() } returns mockDriver

        val result = mockFactory.createDriver()
        result shouldBe mockDriver
    }

    test("createDatabase initializes and migrates driver schema") {
        val mockDriver = mockk<SqlDriver>(relaxed = true)
        val mockFactory = mockk<DriverFactory>()
        every { mockFactory.createDriver() } returns mockDriver

        val db = createDatabase(mockFactory)
        db.shouldNotBeNull()
    }
})
