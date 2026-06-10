package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.LocalDate

class CalendarSyncIntegrationTest : FunSpec({

    val date = LocalDate(2024, 9, 2)
    val preferencesRepository = PreferencesRepository(MapSettings())

    /**
     * Helper to create a robust mock engine for Google API in tests.
     */
    fun createGoogleMockEngine(eventsJson: String = "{\"items\": []}"): MockEngine {
        return MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.contains("/calendarList") -> {
                    respond(
                        content = """{"items": [{"id": "primary", "summary": "Primary"}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                path.endsWith("/events") -> {
                    respond(
                        content = eventsJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                path.contains("/events/") -> { // Delete or Update
                    if (request.method == io.ktor.http.HttpMethod.Delete) {
                        respond(content = "", status = HttpStatusCode.NoContent)
                    } else {
                        respond(
                            content = """{"id": "mock-id"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }

                else -> {
                    respond(
                        content = """{"id": "mock-id"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
    }

    test("LocalScenario: Addition while offline pushes to Remote on sync") {
        val localRepo = SqlDelightLocalCalendarRepository(createTestDatabase())
        val tokenRepo = GoogleTokenRepository(MapSettings())
        tokenRepo.saveTokens("mock-access", "mock-refresh")

        // 1. Add event while 'offline'
        val event = DayEvent(
            id = "local-1",
            title = "Offline Class",
            source = EventSource.CLASS,
            date = date,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        localRepo.updateEvent(event)

        // 2. Synchronize (now online)
        var getCallCount = 0
        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.contains("/calendarList") -> {
                    respond(
                        content = """{"items": [{"id": "primary", "summary": "CEF Academic"}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                path.endsWith("/events") && request.method == io.ktor.http.HttpMethod.Get -> {
                    getCallCount++
                    // 1st call: inside saveEvent() overlap check
                    // 2nd call: Step 3 final state fetch
                    val content = if (getCallCount == 1) "{\"items\": []}"
                    else """{"items": [{"id": "remote-1", "summary": "Offline Class", "start": {"date": "2024-09-02"}, "end": {"date": "2024-09-02"}}]}"""
                    respond(
                        content = content,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                else -> respond(
                    content = """{"id": "remote-1"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                })
            }
        }
        val authService =
            GoogleAuthService(tokenRepo.let { MapSettings() }) // Use a fresh MapSettings for dummy auth
        val syncService = GoogleCalendarSyncService(httpClient, tokenRepo, authService)
        val idResolver = CalendarIdResolver(syncService, preferencesRepository)
        val conflictDetector = EventConflictDetector()
        val eventFilter = EventRangeFilter()
        val remoteRepo = GoogleRemoteCalendarRepository(
            syncService,
            preferencesRepository,
            idResolver,
            conflictDetector,
            eventFilter
        )
        val unifiedRepo = CalendarAgent(localRepo, remoteRepo)

        unifiedRepo.synchronize()

        // 3. Verify Local is now SYNCED and has Remote ID (and the old local-1 is gone)
        val allEvents = localRepo.getAllEvents()
        allEvents shouldHaveSize 1
        allEvents.first().id shouldBe "remote-1"
        allEvents.first().syncStatus shouldBe SyncStatus.SYNCED
    }

    test("LocalScenario: Deletion while offline pushes to Remote on sync") {
        val localRepo = SqlDelightLocalCalendarRepository(createTestDatabase())
        val event = DayEvent(
            id = "remote-1",
            title = "To Be Deleted",
            source = EventSource.CLASS,
            date = date,
            syncStatus = SyncStatus.SYNCED
        )
        localRepo.updateEvent(event)

        // 1. Delete while offline
        localRepo.deleteEvent("remote-1")
        localRepo.getEventsBySyncStatus(SyncStatus.DELETED_LOCALLY) shouldHaveSize 1

        // 2. Synchronize (now online)
        val mockEngine = createGoogleMockEngine("{\"items\": []}")
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                })
            }
        }
        val tokenRepo = GoogleTokenRepository(MapSettings())
        tokenRepo.saveTokens("mock-access", "mock-refresh")
        val authService = GoogleAuthService(MapSettings())
        val syncService = GoogleCalendarSyncService(httpClient, tokenRepo, authService)
        val idResolver = CalendarIdResolver(syncService, preferencesRepository)
        val conflictDetector = EventConflictDetector()
        val eventFilter = EventRangeFilter()
        val remoteRepo = GoogleRemoteCalendarRepository(
            syncService,
            preferencesRepository,
            idResolver,
            conflictDetector,
            eventFilter
        )
        val unifiedRepo = CalendarAgent(localRepo, remoteRepo)

        unifiedRepo.synchronize()

        // 3. Verify
        localRepo.getAllEvents() shouldHaveSize 0
    }

    test("LocalScenario: Addition while online but NOT connected to Workspace fails with exception") {
        val localRepo = SqlDelightLocalCalendarRepository(createTestDatabase())
        val tokenRepo = GoogleTokenRepository(MapSettings())
        // Explicitly NOT saving any tokens
        val authService = GoogleAuthService(MapSettings())
        val httpClient = HttpClient(MockEngine {
            respond(content = "Unauthorized", status = HttpStatusCode.Unauthorized)
        }) {
            install(ContentNegotiation) {
                json(kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                })
            }
        }
        val syncService = GoogleCalendarSyncService(httpClient, tokenRepo, authService)
        val idResolver = CalendarIdResolver(syncService, preferencesRepository)
        val conflictDetector = EventConflictDetector()
        val eventFilter = EventRangeFilter()
        val remoteRepo = GoogleRemoteCalendarRepository(
            syncService,
            preferencesRepository,
            idResolver,
            conflictDetector,
            eventFilter
        )
        val unifiedRepo = CalendarAgent(localRepo, remoteRepo)

        val event =
            DayEvent(id = "local-1", title = "New Class", source = EventSource.CLASS, date = date)

        // 1. Attempt to save - should now THROW
        io.kotest.assertions.throwables.shouldThrow<Exception> {
            unifiedRepo.saveEvent(event)
        }

        // 2. Verify it's NOT saved locally as SYNCED (it shouldn't be saved at all via saveEvent if remote fails)
        val allEvents = localRepo.getAllEvents()
        allEvents shouldHaveSize 0

        // 3. Save explicitly locally
        unifiedRepo.saveEventLocally(event)
        localRepo.getAllEvents() shouldHaveSize 1
        localRepo.getAllEvents().first().syncStatus shouldBe SyncStatus.LOCAL_ONLY
    }

    test("RemoteScenario: Remote changes supersede Local (Gold Standard)") {
        val localRepo = SqlDelightLocalCalendarRepository(createTestDatabase())
        // Local has one synced event
        val event = DayEvent(
            id = "remote-1",
            title = "Old Title",
            source = EventSource.CLASS,
            date = date,
            syncStatus = SyncStatus.SYNCED
        )
        localRepo.updateEvent(event)

        // Remote has the SAME event but with a NEW title and a NEW event
        val eventsJson = """
        {
            "items": [
                { "id": "remote-1", "summary": "New Title", "start": {"date": "2024-09-02"}, "end": {"date": "2024-09-02"} },
                { "id": "remote-2", "summary": "Added Remotely", "start": {"date": "2024-09-03"}, "end": {"date": "2024-09-03"} }
            ]
        }
        """.trimIndent()

        val mockEngine = createGoogleMockEngine(eventsJson)
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                })
            }
        }
        val tokenRepo = GoogleTokenRepository(MapSettings())
        tokenRepo.saveTokens("mock-access", "mock-refresh")
        val authService = GoogleAuthService(MapSettings())
        val syncService = GoogleCalendarSyncService(httpClient, tokenRepo, authService)
        val idResolver = CalendarIdResolver(syncService, preferencesRepository)
        val conflictDetector = EventConflictDetector()
        val eventFilter = EventRangeFilter()
        val remoteRepo = GoogleRemoteCalendarRepository(
            syncService,
            preferencesRepository,
            idResolver,
            conflictDetector,
            eventFilter
        )
        val unifiedRepo = CalendarAgent(localRepo, remoteRepo)

        // Synchronize
        unifiedRepo.synchronize()

        // Verify Local matches Remote exactly
        val allLocal = localRepo.getAllEvents()
        allLocal shouldHaveSize 2
        allLocal.find { it.id == "remote-1" }?.title shouldBe "New Title"
        allLocal.find { it.id == "remote-2" }?.title shouldBe "Added Remotely"
    }
})

// Helper to create an in-memory database for testing
fun createTestDatabase(): com.borinquenterrier.cef.db.AppDatabase {
    val driver =
        app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver(app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.IN_MEMORY)
    com.borinquenterrier.cef.db.AppDatabase.Schema.create(driver)
    return com.borinquenterrier.cef.db.AppDatabase(driver)
}
