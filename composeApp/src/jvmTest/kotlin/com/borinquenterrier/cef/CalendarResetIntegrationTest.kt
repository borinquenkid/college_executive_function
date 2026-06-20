package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.LocalDate

private const val TEST_CALENDAR_ID = "test-calendar@group.calendar.google.com"

class CalendarResetIntegrationTest : FunSpec({

    val date = LocalDate(2025, 9, 15)

    /**
     * Builds a CalendarAgent backed by a shared MapSettings so that
     * GOOGLE_ACCESS_TOKEN written by GoogleTokenRepository is visible to
     * CalendarAgent.isGoogleLinked() via localRepo.getSettings().
     */
    fun buildAgent(mockEngine: MockEngine): Triple<SqlDelightLocalCalendarRepository, CalendarAgent, MapSettings> {
        val settings = MapSettings()
        val tokenRepo = GoogleTokenRepository(settings)
        tokenRepo.saveTokens("mock-access", "mock-refresh")
        settings.putString("calendar_id", TEST_CALENDAR_ID)

        val localRepo = SqlDelightLocalCalendarRepository(createTestDatabase(), settings)
        val authService = GoogleAuthService(settings)
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
            }
        }
        val prefs = PreferencesRepository(settings)
        val syncService = GoogleCalendarSyncService(httpClient, tokenRepo, authService)
        val idResolver = CalendarIdResolver(syncService, prefs)
        val remoteRepo = GoogleRemoteCalendarRepository(
            syncService, prefs, idResolver,
            EventConflictDetector(), EventRangeFilter()
        )
        return Triple(localRepo, CalendarAgent(localRepo, remoteRepo), settings)
    }

    fun emptyRemoteEngine() = MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path.contains("/calendarList") -> respond(
                content = """{"items":[{"id":"$TEST_CALENDAR_ID","summary":"CEF Academic (Test)"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
            path.endsWith("/events") && request.method == HttpMethod.Get -> respond(
                content = """{"items":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
            else -> respond(content = "", status = HttpStatusCode.NoContent)
        }
    }

    test("resetCalendar clears all local events") {
        val (localRepo, agent) = buildAgent(emptyRemoteEngine())
        repeat(3) { i ->
            localRepo.updateEvent(
                DayEvent(id = "evt-$i", title = "Event $i", source = EventSource.CLASS, date = date)
            )
        }
        localRepo.getAllEvents().size shouldBe 3

        agent.resetCalendar()

        localRepo.getAllEvents().size shouldBe 0
    }

    test("resetCalendar sends DELETE to TEST_CALENDAR for each remote event") {
        val deleteIds = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.contains("/calendarList") -> respond(
                    content = """{"items":[{"id":"$TEST_CALENDAR_ID","summary":"CEF Academic (Test)"}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                path.endsWith("/events") && request.method == HttpMethod.Get -> respond(
                    content = """{"items":[
                        {"id":"remote-1","summary":"Study Math","start":{"date":"2025-09-15"},"end":{"date":"2025-09-15"}},
                        {"id":"remote-2","summary":"Study CS","start":{"date":"2025-09-16"},"end":{"date":"2025-09-16"}}
                    ]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                request.method == HttpMethod.Delete -> {
                    deleteIds.add(path.substringAfterLast("/"))
                    respond(content = "", status = HttpStatusCode.NoContent)
                }
                else -> respond(
                    content = """{"id":"mock"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val (_, agent) = buildAgent(mockEngine)
        agent.resetCalendar()

        deleteIds.toSet() shouldBe setOf("remote-1", "remote-2")
    }

    test("resetCalendar increments resetVersion") {
        val (_, agent) = buildAgent(emptyRemoteEngine())
        val before = agent.resetVersion.value
        agent.resetCalendar()
        agent.resetVersion.value shouldBe before + 1
    }

    test("resetCalendar clears local even when not linked to Google") {
        // No tokens → isGoogleLinked() = false; only local clear should run
        val settings = MapSettings()
        val localRepo = SqlDelightLocalCalendarRepository(createTestDatabase(), settings)
        repeat(2) { i ->
            localRepo.updateEvent(
                DayEvent(id = "loc-$i", title = "Local $i", source = EventSource.CLASS, date = date)
            )
        }
        val mockEngine = MockEngine { respond(content = "", status = HttpStatusCode.NoContent) }
        val httpClient = HttpClient(mockEngine) { install(ContentNegotiation) { json() } }
        val tokenRepo = GoogleTokenRepository(settings) // no tokens saved
        val authService = GoogleAuthService(settings)
        val prefs = PreferencesRepository(settings)
        val syncService = GoogleCalendarSyncService(httpClient, tokenRepo, authService)
        val idResolver = CalendarIdResolver(syncService, prefs)
        val remoteRepo = GoogleRemoteCalendarRepository(syncService, prefs, idResolver, EventConflictDetector(), EventRangeFilter())
        val agent = CalendarAgent(localRepo, remoteRepo)

        agent.resetCalendar()

        localRepo.getAllEvents().size shouldBe 0
        mockEngine.requestHistory.size shouldBe 0
    }
})
