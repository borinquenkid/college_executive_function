package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldNotBeEmpty
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.runBlocking
import java.io.File
import com.russhwolf.settings.MapSettings

class MultiFormatAiIntegrationTest : FunSpec({

    test("AI should generate equivalent events from HTML, DOCX, and PDF using local model") {
        // 1. Resolve Model Path
        val userDir = System.getProperty("user.dir") ?: "."
        val root = if (userDir.endsWith("composeApp")) {
            File(userDir).parentFile ?: File(userDir)
        } else {
            File(userDir)
        }
        val modelDir = File(root, "models")
        val modelFile = File(modelDir, "Qwen3.5-9B-Q4_K_M.gguf")
        
        if (!modelFile.exists()) {
            println("AI model missing. Downloading to ${modelFile.absolutePath}...")
            val httpClient = io.ktor.client.HttpClient(io.ktor.client.engine.java.Java)
            val modelManager = ModelManager(httpClient, modelDir.absolutePath)
            runBlocking {
                modelManager.downloadModel().collect { progress ->
                    if (progress.isDone) println("Download complete.")
                }
            }
            httpClient.close()
        }

        if (!modelFile.exists()) {
            throw AssertionError("Failed to download AI model.")
        }

        val settings = MapSettings()
        val logger = Logger(settings)
        val aiService = AIService(settings, logger, null, modelFile.absolutePath)

        // 2. Prepare Source Contents
        
        // A. HTML - Very simple, same info as DOCX/PDF
        val htmlContent = "<html><body>MATH 101 on 2026-01-01 from 08:00 to 09:00 MWF</body></html>"
        val webReader = WebSourceReader()
        val cleanedHtmlText = webReader.cleanHtml(htmlContent)
        val htmlChunks = TextChunker.chunk(cleanedHtmlText)

        // B. DOCX
        var docxFile = File("src/commonTest/resources/calendar.docx")
        if (!docxFile.exists()) {
            val altPath = File("composeApp/src/commonTest/resources/calendar.docx")
            if (altPath.exists()) {
                 docxFile = altPath
            }
        }
        val docxReader = DocxReader()
        val docxChunks = runBlocking { docxReader.extractChunks(docxFile.absolutePath) }

        // C. PDF
        var pdfFile = File("src/commonTest/resources/calendar.pdf")
        if (!pdfFile.exists()) {
            val altPath = File("composeApp/src/commonTest/resources/calendar.pdf")
            if (altPath.exists()) {
                pdfFile = altPath
            }
        }
        val pdfReader = PdfReader()
        val pdfChunks = runBlocking { pdfReader.extractChunks(pdfFile.absolutePath) }

        // 3. Run AI Extraction for each
        val htmlEvents = runBlocking { htmlChunks.flatMap { aiService.generateCalendarEvents(it) } }
        val docxEvents = runBlocking { docxChunks.flatMap { aiService.generateCalendarEvents(it) } }
        val pdfEvents = runBlocking { pdfChunks.flatMap { aiService.generateCalendarEvents(it) } }


        // 4. Verify Equivalence
        htmlEvents.shouldNotBeEmpty()
        docxEvents.shouldNotBeEmpty()
        pdfEvents.shouldNotBeEmpty()
        
        fun List<Event>.findEventOnTargetDate() = this.find { 
            val date = (it as? TimeEvent)?.date ?: (it as DayEvent).date
            date == LocalDate(2026, 1, 1)
        }

        htmlEvents.findEventOnTargetDate() ?: throw AssertionError("HTML extraction failed. Events: $htmlEvents")
        docxEvents.findEventOnTargetDate() ?: throw AssertionError("DOCX extraction failed. Events: $docxEvents")
        pdfEvents.findEventOnTargetDate() ?: throw AssertionError("PDF extraction failed. Events: $pdfEvents")

        println("SUCCESS: All formats generated an event on 2026-01-01")
    }
})
