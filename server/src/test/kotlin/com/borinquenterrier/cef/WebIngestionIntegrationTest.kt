package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.FragmentEntity
import com.borinquenterrier.cef.db.SourceEntity
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.datetime.LocalDate

class WebIngestionIntegrationTest {

    /** A relaxed mockk DependencyContainer never invokes real logic — [launchInBackground]'s
     * default relaxed behavior returns a mock Job without ever running the block, so any test
     * that needs the background pipeline call to actually happen must stub this explicitly. */
    private fun DependencyContainer.stubBackgroundLaunchToRunInline() {
        every { launchInBackground(any()) } answers {
            val block = firstArg<suspend CoroutineScope.() -> Unit>()
            CoroutineScope(Dispatchers.Default).launch { block() }
        }
    }

    @Test
    fun testGetSources() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        
        // Mock SqlDelightSourceRepository
        val mockSourceRepo = mockk<SqlDelightSourceRepository>(relaxed = true)
        every { mockContainer.sourceRepository } returns mockSourceRepo
        
        // Mock SourceEntity
        val mockSourceEntity = mockk<SourceEntity>(relaxed = true) {
            every { id } returns "test-source-id"
            every { title } returns "syllabus.pdf"
            every { category } returns "SYLLABUS"
            every { status } returns "DONE"
        }
        coEvery { mockSourceRepo.getAllSources() } returns listOf(mockSourceEntity)
        
        // Mock FragmentEntity
        val mockFragmentEntity = mockk<FragmentEntity>(relaxed = true) {
            every { text } returns "Welcome to CS 101"
            every { pageNumber } returns 1L
            every { sectionTitle } returns "Intro"
            every { type } returns "TEXT"
        }
        coEvery { mockSourceRepo.getFragmentsForSource("test-source-id") } returns listOf(mockFragmentEntity)

        application {
            module(mockContainer)
        }

        val response = client.get("/api/sources")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("syllabus.pdf"))
        assertTrue(body.contains("Welcome to CS 101"))
    }

    @Test
    fun testGetEvents() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)
        every { mockContainer.calendarAgent } returns mockCalendarAgent

        val testEvent = DayEvent(
            id = "test-event-1",
            title = "Math Midterm Exam",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2026, 6, 15)
        )
        coEvery { mockCalendarAgent.getEvents("default") } returns listOf(testEvent)

        application {
            module(mockContainer)
        }

        val response = client.get("/api/events")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Math Midterm Exam"))
        assertTrue(body.contains("DEADLINE"))
    }

    @Test
    fun testSyncEvents() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)
        every { mockContainer.calendarAgent } returns mockCalendarAgent

        application {
            module(mockContainer)
        }

        val response = client.post("/api/events/sync")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("success"))
        coVerify(exactly = 1) { mockCalendarAgent.synchronize("default") }
    }

    @Test
    fun testDeleteSourceSuccess() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockSourceRepo = mockk<SqlDelightSourceRepository>(relaxed = true)
        val mockLocalRepo = mockk<SqlDelightLocalCalendarRepository>(relaxed = true)
        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)
        every { mockContainer.sourceRepository } returns mockSourceRepo
        every { mockContainer.localRepository } returns mockLocalRepo
        every { mockContainer.calendarAgent } returns mockCalendarAgent

        val mockSourceEntity = mockk<SourceEntity>(relaxed = true) {
            every { id } returns "calculus-id"
            every { title } returns "Calculus Syllabus"
            every { category } returns "SYLLABUS"
            every { status } returns "DONE"
        }
        coEvery { mockSourceRepo.getAllSources() } returns listOf(mockSourceEntity)

        application {
            module(mockContainer)
        }

        val response = client.delete("/api/sources/Calculus%20Syllabus")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("success"))
        
        coVerify(exactly = 1) { mockSourceRepo.deleteSource("Calculus Syllabus") }
        coVerify(exactly = 1) { mockCalendarAgent.synchronize("default") }
    }

    @Test
    fun testDeleteSourceRemovesEventsBySourceId() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockSourceRepo = mockk<SqlDelightSourceRepository>(relaxed = true)
        val mockLocalRepo = mockk<SqlDelightLocalCalendarRepository>(relaxed = true)
        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)
        every { mockContainer.sourceRepository } returns mockSourceRepo
        every { mockContainer.localRepository } returns mockLocalRepo
        every { mockContainer.calendarAgent } returns mockCalendarAgent

        val mockSourceEntity = mockk<SourceEntity>(relaxed = true) {
            every { id } returns "calculus-id"
            every { title } returns "Calculus Syllabus"
            every { category } returns "SYLLABUS"
            every { status } returns "DONE"
        }
        coEvery { mockSourceRepo.getAllSources() } returns listOf(mockSourceEntity)

        // A modern event: its id is a content hash unrelated to the source title, but its
        // sourceId correctly links it back — this is the case the legacy id-prefix/warning
        // heuristic alone cannot catch (see EventGenerationService.generateDeterministicId).
        val linkedEvent = DayEvent(
            id = "a1b2c3d4e5f6",
            title = "Midterm Exam",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2026, 6, 15),
            sourceId = "Calculus Syllabus"
        )
        val unrelatedEvent = DayEvent(
            id = "f6e5d4c3b2a1",
            title = "Unrelated Event",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2026, 6, 20),
            sourceId = "Other Source"
        )
        coEvery { mockLocalRepo.getAllEvents("default") } returns listOf(linkedEvent, unrelatedEvent)

        application {
            module(mockContainer)
        }

        val response = client.delete("/api/sources/Calculus%20Syllabus")
        assertEquals(HttpStatusCode.OK, response.status)

        coVerify(exactly = 1) { mockLocalRepo.hardDeleteEvent("a1b2c3d4e5f6", "default") }
        coVerify(exactly = 0) { mockLocalRepo.hardDeleteEvent("f6e5d4c3b2a1", "default") }
    }

    @Test
    fun testDeleteSourceNotFound() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockSourceRepo = mockk<SqlDelightSourceRepository>(relaxed = true)
        every { mockContainer.sourceRepository } returns mockSourceRepo
        coEvery { mockSourceRepo.getAllSources() } returns emptyList()

        application {
            module(mockContainer)
        }

        val response = client.delete("/api/sources/NonExistent")
        assertEquals(HttpStatusCode.NotFound, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Source not found"))
    }

    @Test
    fun testGetSettings() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockSettings = mockk<com.russhwolf.settings.Settings>(relaxed = true)
        val mockPreferencesRepo = mockk<PreferencesRepository>(relaxed = true)
        every { mockContainer.settings } returns mockSettings
        every { mockContainer.preferencesRepository } returns mockPreferencesRepo

        every { mockSettings.getString("CEF_GEMINI_API_KEY", "") } returns "mocked-gemini-key"
        coEvery { mockPreferencesRepo.getPreferences() } returns StudyPreferences(
            studyStartHour = 8,
            studyEndHour = 22
        )

        application {
            module(mockContainer)
        }

        val response = client.get("/api/settings")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertFalse(body.contains("mocked-gemini-key"), "the stored API key must never be sent to the client")
        assertTrue(body.contains("\"hasApiKey\":true"))
        assertTrue(body.contains("studyStartHour"))
    }

    @Test
    fun testSaveSettings() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockSettings = mockk<com.russhwolf.settings.Settings>(relaxed = true)
        val mockPreferencesRepo = mockk<PreferencesRepository>(relaxed = true)
        every { mockContainer.settings } returns mockSettings
        every { mockContainer.preferencesRepository } returns mockPreferencesRepo

        application {
            module(mockContainer)
        }

        val settingsPayload = WebSettings(
            apiKey = "new-api-key",
            studyPreferences = StudyPreferences(studyStartHour = 10)
        )

        val response = client.post("/api/settings") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(settingsPayload))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("success"))

        verify(exactly = 1) { mockSettings.putString("CEF_GEMINI_API_KEY", "new-api-key") }
        verify(exactly = 1) { mockSettings.putString("GEMINI_API_KEY", "new-api-key") }
        coVerify(exactly = 1) { mockPreferencesRepo.savePreferences(any()) }
    }

    @Test
    fun testPostSourceUrl() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockIngestionAgent = mockk<IngestionAgent>(relaxed = true)
        val mockPipeline = mockk<SourceProcessingPipeline>(relaxed = true)
        every { mockContainer.ingestionAgent } returns mockIngestionAgent
        every { mockContainer.sourceProcessingPipeline } returns mockPipeline
        mockContainer.stubBackgroundLaunchToRunInline()

        val mockSourceItem = SourceItem(
            title = "syllabus.ics",
            fragments = emptyList(),
            category = SourceCategory.CALENDAR,
            status = SourceStatus.PENDING
        )
        coEvery { mockIngestionAgent.addUrl("https://example.com/syllabus.ics") } returns mockSourceItem

        application {
            module(mockContainer)
        }

        val response = client.submitFormWithBinaryData(
            url = "/api/sources",
            formData = formData {
                append("url", "https://example.com/syllabus.ics")
            }
        )

        assertEquals(HttpStatusCode.Accepted, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("syllabus.ics"))
        assertTrue(body.contains("CALENDAR"))
        withTimeout(2000) {
            coVerify(exactly = 1) { mockPipeline.processSource(mockSourceItem) }
        }
    }

    @Test
    fun testPostSourceFile() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockIngestionAgent = mockk<IngestionAgent>(relaxed = true)
        val mockPipeline = mockk<SourceProcessingPipeline>(relaxed = true)
        every { mockContainer.ingestionAgent } returns mockIngestionAgent
        every { mockContainer.sourceProcessingPipeline } returns mockPipeline
        mockContainer.stubBackgroundLaunchToRunInline()

        val mockSourceItem = SourceItem(
            title = "sample.pdf",
            fragments = emptyList(),
            category = SourceCategory.SYLLABUS,
            status = SourceStatus.PENDING
        )
        coEvery { mockIngestionAgent.addLocalFile(any(), any()) } returns mockSourceItem

        application {
            module(mockContainer)
        }

        val response = client.submitFormWithBinaryData(
            url = "/api/sources",
            formData = formData {
                append("file", byteArrayOf(1, 2, 3, 4), Headers.build {
                    append(HttpHeaders.ContentType, "application/pdf")
                    append(HttpHeaders.ContentDisposition, "filename=\"sample.pdf\"")
                })
            }
        )

        assertEquals(HttpStatusCode.Accepted, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("sample.pdf"))
        assertTrue(body.contains("SYLLABUS"))
        withTimeout(2000) {
            coVerify(exactly = 1) { mockPipeline.processSource(mockSourceItem) }
        }
    }

    @Test
    fun testPostSourceFileRespondsBeforePipelineCompletes() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockIngestionAgent = mockk<IngestionAgent>(relaxed = true)
        val mockPipeline = mockk<SourceProcessingPipeline>(relaxed = true)
        every { mockContainer.ingestionAgent } returns mockIngestionAgent
        every { mockContainer.sourceProcessingPipeline } returns mockPipeline
        mockContainer.stubBackgroundLaunchToRunInline()

        val mockSourceItem = SourceItem(
            title = "slow.pdf",
            fragments = emptyList(),
            category = SourceCategory.SYLLABUS,
            status = SourceStatus.PENDING
        )
        coEvery { mockIngestionAgent.addLocalFile(any(), any()) } returns mockSourceItem

        // Gates the pipeline call open until the test explicitly releases it — proves the HTTP
        // response does not wait on processSource (the core claim of ADR 0012 / AU-1+AU-2).
        val pipelineGate = CompletableDeferred<Unit>()
        coEvery { mockPipeline.processSource(mockSourceItem) } coAnswers { pipelineGate.await() }

        application {
            module(mockContainer)
        }

        val response = withTimeout(2000) {
            client.submitFormWithBinaryData(
                url = "/api/sources",
                formData = formData {
                    append("file", byteArrayOf(1, 2, 3, 4), Headers.build {
                        append(HttpHeaders.ContentType, "application/pdf")
                        append(HttpHeaders.ContentDisposition, "filename=\"slow.pdf\"")
                    })
                }
            )
        }

        // The response already landed above without the gate ever being released — if
        // processSource were still awaited inline, this request would have hung on the 2s timeout.
        assertEquals(HttpStatusCode.Accepted, response.status)
        pipelineGate.complete(Unit)
    }

    @Test
    fun testGetGoogleAuthStatus() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockTokenRepo = mockk<GoogleTokenRepository>(relaxed = true)
        every { mockContainer.tokenRepository } returns mockTokenRepo
        every { mockTokenRepo.hasTokens() } returns true

        application {
            module(mockContainer)
        }

        val response = client.get("/api/auth/google/status")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"linked\":true"))
    }

    @Test
    fun testGetCalendars() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockRemoteRepo = mockk<GoogleRemoteCalendarRepository>(relaxed = true)
        every { mockContainer.remoteRepository } returns mockRemoteRepo
        coEvery { mockRemoteRepo.getAvailableCalendars() } returns listOf(
            RemoteCalendarMetadata(id = "cal-1", name = "Primary")
        )

        application {
            module(mockContainer)
        }

        val response = client.get("/api/calendars")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("cal-1"))
        assertTrue(response.bodyAsText().contains("Primary"))
    }

    @Test
    fun testCreateCalendar() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockSyncService = mockk<GoogleCalendarSyncService>(relaxed = true)
        every { mockContainer.syncService } returns mockSyncService
        coEvery { mockSyncService.createCalendar("New Study Calendar") } returns "cal-new-id"

        application {
            module(mockContainer)
        }

        val response = client.post("/api/calendars") {
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"New Study Calendar\"}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("cal-new-id"))
        assertTrue(body.contains("New Study Calendar"))
    }
}

