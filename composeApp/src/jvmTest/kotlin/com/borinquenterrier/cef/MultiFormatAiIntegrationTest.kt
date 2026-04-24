package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldNotBeEmpty
import kotlinx.coroutines.runBlocking
import java.io.File

class MultiFormatAiIntegrationTest : FunSpec({

    test("AI should generate equivalent events from HTML, DOCX, and PDF") {
        println("Working Dir: ${File(".").absolutePath}")
        // 1. Resolve Credentials
        val envFile = listOf(File("../.env"), File(".env")).find { it.exists() }
        if (envFile == null) {
            println("SKIPPING MULTI-FORMAT TEST: No .env file found in . or ..")
            return@test
        }
        println("Using .env from: ${envFile.absolutePath}")
        
        val envMap = envFile.readLines()
            .filter { it.contains("=") && !it.startsWith("#") }
            .associate { 
                val key = it.substringBefore("=").trim()
                val value = it.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                key to value 
            }

        val apiKey = (envMap["CEF_GEMINI_API_KEY"] ?: envMap["GEMINI_API_KEY"])?.takeIf { it.isNotBlank() }
        val accessToken = envMap["GOOGLE_ACCESS_TOKEN"]?.takeIf { it.isNotBlank() }
        
        if (apiKey == null && accessToken == null) {
            println("SKIPPING MULTI-FORMAT TEST: No credentials found in .env")
            return@test
        }

        val geminiService = when {
            apiKey != null -> GeminiAIService(apiKey = apiKey)
            else -> GeminiAIService(accessToken = accessToken)
        }

        // 2. Prepare Source Contents
        
        // A. HTML - Very simple, same info as DOCX/PDF
        val htmlContent = "<html><body>MATH 101 on 2026-01-01 from 08:00 to 09:00 MWF</body></html>"
        val webReader = WebSourceReader()
        val cleanedHtmlText = webReader.cleanHtml(htmlContent)

        // B. DOCX
        val docxFile = File("src/commonTest/resources/calendar.docx")
        if (!docxFile.exists()) {
            val altPath = File("composeApp/src/commonTest/resources/calendar.docx")
            if (altPath.exists()) docxFile.parentFile.mkdirs() // Not really possible but for safety
        }
        val docxReader = DocxReader()
        val docxText = runBlocking { docxReader.extractText(docxFile.absolutePath) }

        // C. PDF
        val pdfFile = File("src/commonTest/resources/calendar.pdf")
        val pdfReader = PdfReader()
        val pdfText = runBlocking { pdfReader.extractText(pdfFile.absolutePath) }

        println("HTML Cleaned: $cleanedHtmlText")
        println("DOCX Extracted: $docxText")
        println("PDF Extracted: $pdfText")

        // 3. Run AI Extraction for each
        val htmlEvents = runBlocking { geminiService.generateCalendarEvents(cleanedHtmlText) }
        kotlinx.coroutines.delay(3000) // Stay under rate limit
        val docxEvents = runBlocking { geminiService.generateCalendarEvents(docxText) }
        kotlinx.coroutines.delay(3000)
        val pdfEvents = runBlocking { geminiService.generateCalendarEvents(pdfText) }

        // 4. Verify Equivalence
        htmlEvents.shouldNotBeEmpty()
        docxEvents.shouldNotBeEmpty()
        pdfEvents.shouldNotBeEmpty()
        
        fun List<Event>.findMath101() = this.find { it.title.contains("MATH 101", ignoreCase = true) }

        val htmlMath = htmlEvents.findMath101() ?: throw AssertionError("HTML extraction failed. Events: $htmlEvents")
        val docxMath = docxEvents.findMath101() ?: throw AssertionError("DOCX extraction failed. Events: $docxEvents")
        val pdfMath = pdfEvents.findMath101() ?: throw AssertionError("PDF extraction failed. Events: $pdfEvents")

        // Assert basic properties match across all sources
        docxMath.title.contains("MATH 101", ignoreCase = true) shouldBe true
        pdfMath.title.contains("MATH 101", ignoreCase = true) shouldBe true
        
        val htmlDate = (htmlMath as? TimeEvent)?.date ?: (htmlMath as DayEvent).date
        val docxDate = (docxMath as? TimeEvent)?.date ?: (docxMath as DayEvent).date
        val pdfDate = (pdfMath as? TimeEvent)?.date ?: (pdfMath as DayEvent).date
        
        docxDate shouldBe htmlDate
        pdfDate shouldBe htmlDate
        
        println("SUCCESS: All formats generated the same core event: ${htmlMath.title} on $htmlDate")
    }
})
