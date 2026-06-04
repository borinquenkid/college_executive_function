package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate

class SyllabusEvaluationSuite : FunSpec({

    data class ExpectedEvent(
        val title: String,
        val date: LocalDate,
        val category: AcademicCategory
    )

    fun loadExpectedEvents(expectedFile: File): List<ExpectedEvent> {
        val text = expectedFile.readText()
        val jsonArray = Json.parseToJsonElement(text).jsonArray
        return jsonArray.map { element ->
            val obj = element.jsonObject
            val title = obj["title"]!!.jsonPrimitive.content
            val dateStr = obj["date"]!!.jsonPrimitive.content
            val categoryStr = obj["category"]!!.jsonPrimitive.content
            ExpectedEvent(
                title = title,
                date = LocalDate.parse(dateStr),
                category = AcademicCategory.valueOf(categoryStr)
            )
        }
    }

    test("Run evaluation suite on test syllabi") {
        // Resolve Credentials
        val envFile = listOf(File("../.env"), File(".env")).find { it.exists() }
        val envMap = envFile?.readLines()?.associate {
            val key = it.substringBefore("=").trim()
            val value = it.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
            key to value
        } ?: emptyMap()

        val apiKey = (envMap["CEF_GEMINI_API_KEY"] ?: envMap["GEMINI_API_KEY"])?.takeIf { it.isNotBlank() }
        if (apiKey == null) {
            println("SKIPPING EVALUATION SUITE: No Gemini API Key found in .env")
            return@test
        }

        val settings = MapSettings()
        settings.putString("CEF_GEMINI_API_KEY", apiKey)
        val aiService = RealAIService(settings, Logger(settings), null)

        val testCases = listOf(
            Pair("syllabus_bdan250.pdf", "syllabus_bdan250_expected.json"),
            Pair("syllabus_hist152.pdf", "syllabus_hist152_expected.json")
        )

        println("\n=======================================================")
        println("             SYLLABUS EVALUATION RESULTS")
        println("=======================================================")
        println(String.format("%-30s | %-10s | %-10s | %-15s", "Syllabus File", "Recall", "Matched", "Date Accuracy"))
        println("-------------------------------------------------------------------------------------")

        testCases.forEach { (pdfName, expectedJsonName) ->
            val pdfFile = listOf(
                File("src/commonTest/resources/$pdfName"),
                File("composeApp/src/commonTest/resources/$pdfName"),
                File("../composeApp/src/commonTest/resources/$pdfName")
            ).find { it.exists() } ?: throw Exception("PDF file not found: $pdfName")

            val expectedFile = listOf(
                File("src/commonTest/resources/$expectedJsonName"),
                File("composeApp/src/commonTest/resources/$expectedJsonName"),
                File("../composeApp/src/commonTest/resources/$expectedJsonName")
            ).find { it.exists() } ?: throw Exception("Expected JSON file not found: $expectedJsonName")

            val rawParts = PdfReader().readSource(pdfFile.absolutePath)
            val fullText = rawParts.joinToString("\n\n") { it.text }
            val fragments = SourceProcessor.process(fullText)
            val expectedEvents = loadExpectedEvents(expectedFile)

            // Run AI extraction
            val extractedEvents = runBlocking { aiService.generateCalendarEvents(fragments) }

            // Evaluate
            var matchedCount = 0
            var dateCorrectCount = 0

            expectedEvents.forEach { expected ->
                // Check if any extracted event matches the expected event
                val matchingExtracted = extractedEvents.find { actual ->
                    val titleMatch = actual.title.lowercase().contains(expected.title.lowercase()) ||
                            expected.title.lowercase().contains(actual.title.lowercase())
                    titleMatch
                }

                if (matchingExtracted != null) {
                    matchedCount++
                    if (matchingExtracted.date == expected.date) {
                        dateCorrectCount++
                    } else {
                        println("  [DATE MISMATCH] for '${expected.title}': Expected ${expected.date}, got ${matchingExtracted.date}")
                    }
                } else {
                    println("  [MISSING EVENT] '${expected.title}' due on ${expected.date}")
                }
            }

            val recall = if (expectedEvents.isNotEmpty()) (matchedCount.toDouble() / expectedEvents.size.toDouble()) * 100.0 else 100.0
            val dateAccuracy = if (matchedCount > 0) (dateCorrectCount.toDouble() / matchedCount.toDouble()) * 100.0 else 100.0

            println(String.format("%-30s | %8.1f%% | %4d/%-5d | %13.1f%%", pdfName, recall, matchedCount, expectedEvents.size, dateAccuracy))
        }
        println("=======================================================\n")
    }
})
