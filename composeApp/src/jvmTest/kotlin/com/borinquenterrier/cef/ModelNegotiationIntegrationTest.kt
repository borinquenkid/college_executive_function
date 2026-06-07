package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import kotlinx.coroutines.runBlocking

class ModelNegotiationIntegrationTest : FunSpec({

    // TODO: re-enable once a valid CEF_GEMINI_API_KEY/GOOGLE_ACCESS_TOKEN is restored to .env —
    // currently fails with "Unauthorized" against the live Gemini API and blocks publishing.
    test("GeminiAIService should successfully negotiate a model using .env credentials").config(enabled = false) {
        // 1. Resolve Credentials
        val envFile = listOf(File("../.env"), File(".env")).find { it.exists() }
        val envMap = envFile?.readLines()?.associate { 
            val key = it.substringBefore("=").trim()
            val value = it.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
            key to value 
        } ?: emptyMap()

        val apiKey = (envMap["CEF_GEMINI_API_KEY"] ?: envMap["GEMINI_API_KEY"])?.takeIf { it.isNotBlank() }
        val accessToken = envMap["GOOGLE_ACCESS_TOKEN"]?.takeIf { it.isNotBlank() }
        
        if (apiKey == null && accessToken == null) {
            println("SKIPPING NEGOTIATION TEST: No credentials found in .env")
            return@config
        }

        println("Testing negotiation with Key: ${apiKey?.take(8)}...")

        // 2. Instantiate Service
        val geminiService = GeminiAIService(apiKey = apiKey, accessToken = accessToken)

        // 3. Trigger a call that requires negotiation
        // Since negotiateModelName is private, we call generateCalendarEvents with minimal text
        val events = try {
            runBlocking { 
                geminiService.generateCalendarEvents(listOf(SourceFragment("Test event on 2024-12-01"))) 
            }
        } catch (e: Exception) {
            if (e.message?.contains("QuotaExhausted") == true) {
                println("SKIPPING NEGOTIATION TEST: Gemini quota/rate-limit exhausted.")
                return@config
            }
            throw e
        }

        // 4. Verify results
        events shouldNotBe null
        println("Negotiation successful. Extracted ${events.size} events.")
        
        // If it didn't crash and returned events, negotiation worked.
    }
})
