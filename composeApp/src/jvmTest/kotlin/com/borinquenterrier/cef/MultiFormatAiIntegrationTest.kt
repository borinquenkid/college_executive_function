package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldNotBeEmpty
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.runBlocking
import java.io.File
import com.russhwolf.settings.MapSettings

class MultiFormatAiIntegrationTest : FunSpec({

    test("AI should generate equivalent events from HTML, DOCX, and PDF using Gemini") {
        // 1. Resolve Credentials
        val envFile = listOf(File("../.env"), File(".env")).find { it.exists() }
        val envMap = envFile?.readLines()?.associate { 
            val key = it.substringBefore("=").trim()
            val value = it.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
            key to value 
        } ?: emptyMap()

        val apiKey = (envMap["CEF_GEMINI_API_KEY"] ?: envMap["GEMINI_API_KEY"])?.takeIf { it.isNotBlank() }
        
        if (apiKey == null) {
            println("SKIPPING MULTI-FORMAT AI TEST: No Gemini API Key found in .env")
            return@test
        }

        val settings = MapSettings()
        settings.putString("CEF_GEMINI_API_KEY", apiKey)
        val logger = Logger(settings)
        val aiService = AIService(settings, logger, null, null)

        // 2. Prepare Source Contents
        
        // A. HTML - Very simple, same info as DOCX/PDF
        val htmlContent = "<html><body>MATH 101 on 2026-01-01 from 08:00 to 09:00 MWF</body></html>"
        val webReader = WebSourceReader()
        val cleanedHtmlText = webReader.cleanHtml(htmlContent)
        val htmlParts = SourceProcessor.process(cleanedHtmlText)

        // B. DOCX
        var docxFile = File("src/commonTest/resources/calendar.docx")
        if (!docxFile.exists()) {
            val altPath = File("composeApp/src/commonTest/resources/calendar.docx")
            if (altPath.exists()) {
                 docxFile = altPath
            }
        }
        val docxReader = DocxReader()
        val docxParts = runBlocking { docxReader.readSource(docxFile.absolutePath) }

        // C. PDF
        var pdfFile = File("src/commonTest/resources/calendar.pdf")
        if (!pdfFile.exists()) {
            val altPath = File("composeApp/src/commonTest/resources/calendar.pdf")
            if (altPath.exists()) {
                pdfFile = altPath
            }
        }
        val pdfReader = PdfReader()
        val pdfParts = runBlocking { pdfReader.readSource(pdfFile.absolutePath) }

        // 3. Run AI Extraction for each
        val htmlEvents = runBlocking { if (aiService.isConfigured()) aiService.generateCalendarEvents(htmlParts) else emptyList() }
        kotlinx.coroutines.delay(5000)
        val docxEvents = runBlocking { if (aiService.isConfigured()) aiService.generateCalendarEvents(docxParts) else emptyList() }
        kotlinx.coroutines.delay(5000)
        val pdfEvents = runBlocking { if (aiService.isConfigured()) aiService.generateCalendarEvents(pdfParts) else emptyList() }


        // 4. Verify Equivalence
        htmlEvents.shouldNotBeEmpty()
        docxEvents.shouldNotBeEmpty()
        pdfEvents.shouldNotBeEmpty()
        
        fun List<Event>.findEventOnTargetDate() = this.find { 
            val date = (it as? TimeEvent)?.date ?: (it as DayEvent).date
            date == LocalDate(2026, 1, 1)
        }

        println("HTML Events: $htmlEvents")
        println("DOCX Events: $docxEvents")
        println("PDF Events: $pdfEvents")

        htmlEvents.findEventOnTargetDate() ?: throw AssertionError("HTML extraction failed. Events: $htmlEvents")
        docxEvents.findEventOnTargetDate() ?: throw AssertionError("DOCX extraction failed. Events: $docxEvents")
        pdfEvents.findEventOnTargetDate() ?: throw AssertionError("PDF extraction failed. Events: $pdfEvents")

        println("SUCCESS: All formats generated an event on 2026-01-01")
    }
})
