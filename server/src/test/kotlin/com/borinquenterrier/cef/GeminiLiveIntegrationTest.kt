package com.borinquenterrier.cef

import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes

/**
 * End-to-end check that POST /api/sources actually reaches the real Gemini API through a
 * real (non-mocked) ServerContainerFactory tenant — not the mocked container every other
 * server test uses. Confirms the .env-based key + curl-upload flow verified manually during
 * the react branch update actually works as an automated, repeatable check.
 *
 * Excluded by default (see server/build.gradle.kts): costs real Gemini quota and needs a
 * configured key. Run explicitly with:
 *   ./gradlew :server:test -PrunAITests=true --tests "*GeminiLiveIntegrationTest*"
 *
 * Skips gracefully (not a failure) when no CEF_GEMINI_API_KEY/GEMINI_API_KEY is found in
 * the OS environment or the project-root .env file.
 */
class GeminiLiveIntegrationTest {

    private val dotenvInstance by lazy {
        dotenv {
            directory = listOf("../", "./").firstOrNull { File(it, ".env").exists() } ?: "./"
            ignoreIfMissing = true
            ignoreIfMalformed = true
        }
    }

    private fun resolveApiKey(): String? {
        fun resolve(key: String): String? =
            System.getenv(key)?.takeIf { it.isNotBlank() }
                ?: runCatching { dotenvInstance[key] }.getOrNull()?.takeIf { it.isNotBlank() }
        return resolve("CEF_GEMINI_API_KEY") ?: resolve("GEMINI_API_KEY")
    }

    private fun findSampleSyllabus(): File? = listOf(
        "contributions/mo/st_louis_community_college/2025-2026/summer/syllabi-202620-eng-101-601-20366.pdf",
        "../contributions/mo/st_louis_community_college/2025-2026/summer/syllabi-202620-eng-101-601-20366.pdf",
    ).map { File(it) }.firstOrNull { it.exists() }

    @Test
    fun `POST api sources ingests a real syllabus through the live Gemini pipeline`() = testApplication {
        val apiKey = resolveApiKey()
        if (apiKey == null) {
            println("SKIPPING: no Gemini API key found in env or .env")
            return@testApplication
        }
        val syllabus = findSampleSyllabus()
        if (syllabus == null) {
            println("SKIPPING: sample syllabus PDF not found under contributions/")
            return@testApplication
        }

        val baseDir = Files.createTempDirectory("cef-gemini-live-test").toFile()
        val factory = ServerContainerFactory(tenantBaseDir = baseDir.absolutePath)
        try {
            val container = runBlocking { factory.containerFor("live-test") }
            // Test-only seeding: production never auto-imports the operator's .env key into
            // a tenant (see ServerContainerFactory) — each tenant must set its own via
            // POST /api/settings. Here we seed it directly since this test IS the automated
            // check that the live pipeline works end to end.
            container.settings.putString("CEF_GEMINI_API_KEY", apiKey)
            container.settings.putString("GEMINI_API_KEY", apiKey)

            application { module(testContainer = container) }

            withTimeout(3.minutes) {
                val response = client.submitFormWithBinaryData(
                    url = "/api/sources",
                    formData = formData {
                        append("file", syllabus.readBytes(), Headers.build {
                            append(HttpHeaders.ContentType, "application/pdf")
                            append(HttpHeaders.ContentDisposition, "filename=\"${syllabus.name}\"")
                        })
                    }
                )

                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains("SYLLABUS"), "expected the live pipeline to classify this as a syllabus: $body")

                val sourcesResponse = client.get("/api/sources")
                assertTrue(sourcesResponse.bodyAsText().contains("fragments"))
            }
        } finally {
            runBlocking { factory.closeAll() }
            baseDir.deleteRecursively()
        }
    }
}
