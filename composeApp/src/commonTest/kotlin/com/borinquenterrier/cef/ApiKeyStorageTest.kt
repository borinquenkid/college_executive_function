package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ApiKeyStorageTest : FunSpec({

    test("API key is saved and retrieved correctly") {
        val settings = MapSettings()
        val apiKey = "test-api-key"

        settings.putString("GEMINI_API_KEY", apiKey)

        val retrievedKey = settings.getString("GEMINI_API_KEY", "")
        retrievedKey shouldBe apiKey
    }
})
