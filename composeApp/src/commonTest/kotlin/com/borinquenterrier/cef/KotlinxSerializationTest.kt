package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json

class KotlinxSerializationTest : FunSpec({

    test("LocalDateSerializer correctly serializes and deserializes") {
        // 1. Arrange
        val date = LocalDate(2024, 8, 26)
        val serializer = LocalDateSerializer

        // 2. Act
        val jsonString = Json.encodeToString(serializer, date)
        val decodedDate = Json.decodeFromString(serializer, jsonString)

        // 3. Assert
        jsonString shouldBe "\"2024-08-26\""
        decodedDate shouldBe date
    }

    test("LocalTimeSerializer correctly serializes and deserializes") {
        // 1. Arrange
        val time = LocalTime(10, 30)
        val serializer = LocalTimeSerializer

        // 2. Act
        val jsonString = Json.encodeToString(serializer, time)
        val decodedTime = Json.decodeFromString(serializer, jsonString)

        // 3. Assert
        jsonString shouldBe "\"10:30\""
        decodedTime shouldBe time
    }
})
