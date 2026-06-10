package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

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

    fun areTitlesSimilar(title1: String, title2: String): Boolean {
        fun normalize(s: String): String {
            return s.lowercase()
                .replace(Regex("['\"#’“”]"), "")
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        val n1 = normalize(title1)
        val n2 = normalize(title2)

        return n1.contains(n2) || n2.contains(n1)
    }

    test("Run evaluation suite on test syllabi").config(
        timeout = AI_INTEGRATION_TIMEOUT_MS.milliseconds
    ) {
        val apiKey = resolveApiKey("EVALUATION SUITE") ?: return@config


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
        println(
            String.format(
                "%-30s | %-10s | %-10s | %-15s",
                "Syllabus File",
                "Recall",
                "Matched",
                "Date Accuracy"
            )
        )
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
            ).find { it.exists() }
                ?: throw Exception("Expected JSON file not found: $expectedJsonName")

            val rawParts = PdfReader().readSource(pdfFile.absolutePath)
            val fullText = rawParts.joinToString("\n\n") { it.text }
            val fragments = SourceProcessor.process(fullText)
            val expectedEvents = loadExpectedEvents(expectedFile)

            // Run AI extraction — skip cleanly if daily quota is exhausted
            val extractedEvents = skipIfQuotaExhausted("generateCalendarEvents[$pdfName]") {
                aiService.generateCalendarEvents(fragments)
            }

            println("  Extracted Events:")
            extractedEvents.forEach { println("    - ${it.date} | ${it.category} | ${it.title}") }

            // Evaluate
            var matchedCount = 0
            var dateCorrectCount = 0

            expectedEvents.forEach { expected ->
                // Check if any extracted event matches the expected event
                val matchingExtracted = extractedEvents.find { actual ->
                    areTitlesSimilar(actual.title, expected.title)
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

            val recall =
                if (expectedEvents.isNotEmpty()) (matchedCount.toDouble() / expectedEvents.size.toDouble()) * 100.0 else 100.0
            val dateAccuracy =
                if (matchedCount > 0) (dateCorrectCount.toDouble() / matchedCount.toDouble()) * 100.0 else 100.0

            println(
                String.format(
                    "%-30s | %8.1f%% | %4d/%-5d | %13.1f%%",
                    pdfName,
                    recall,
                    matchedCount,
                    expectedEvents.size,
                    dateAccuracy
                )
            )
        }
        println("=======================================================\n")
    }
})
