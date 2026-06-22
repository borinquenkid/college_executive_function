package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GoogleCalendarSyncServiceBranchTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true }

    fun mockClient(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    fun tokenRepo(access: String? = "access-token", refresh: String? = "refresh-token") =
        mockk<GoogleTokenRepository>(relaxed = true).also {
            coEvery { it.getAccessToken() } returns access
            coEvery { it.getRefreshToken() } returns refresh
        }

    fun authService(newToken: String? = "new-token") =
        mockk<GoogleAuthService>(relaxed = true).also {
            coEvery { it.refreshAccessToken(any()) } returns newToken
        }

    fun makeService(
        engine: MockEngine,
        repo: GoogleTokenRepository = tokenRepo(),
        auth: GoogleAuthService = authService()
    ) = GoogleCalendarSyncService(mockClient(engine), GoogleTokenService(repo, auth))

    val jsonHeader = headersOf("Content-Type", ContentType.Application.Json.toString())

    // ── getEvents ─────────────────────────────────────────────────────────────

    test("getEvents returns TimeEvents when dateTime fields are present") {
        val engine = MockEngine { _ ->
            respond(
                """{"items":[{"id":"e1","summary":"Lecture",
                   "start":{"dateTime":"2026-09-01T09:00:00Z"},
                   "end":{"dateTime":"2026-09-01T10:00:00Z"}}],"nextPageToken":null}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val events = makeService(engine).getEvents("cal-id")
        events shouldHaveSize 1
        (events[0] is TimeEvent) shouldBe true
        events[0].title shouldBe "Lecture"
    }

    test("getEvents returns DayEvents when only date fields are present") {
        val engine = MockEngine { _ ->
            respond(
                """{"items":[{"id":"e1","summary":"Essay Due",
                   "start":{"date":"2026-09-05"}}],"nextPageToken":null}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val events = makeService(engine).getEvents("cal-id")
        events shouldHaveSize 1
        (events[0] is DayEvent) shouldBe true
        (events[0] as DayEvent).date shouldBe LocalDate(2026, 9, 5)
    }

    test("getEvents falls back to 2024-01-01 when start.date is null") {
        val engine = MockEngine { _ ->
            respond(
                """{"items":[{"id":"e1","summary":"Mystery"}],"nextPageToken":null}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val events = makeService(engine).getEvents("cal-id")
        events shouldHaveSize 1
        (events[0] as DayEvent).date shouldBe LocalDate(2024, 1, 1)
    }

    test("getEvents uses Untitled Event when summary is null") {
        val engine = MockEngine { _ ->
            respond(
                """{"items":[{"id":"e1","start":{"date":"2026-09-01"}}],"nextPageToken":null}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val events = makeService(engine).getEvents()
        events[0].title shouldBe "Untitled Event"
    }

    test("getEvents handles invalid updated timestamp gracefully") {
        val engine = MockEngine { _ ->
            respond(
                """{"items":[{"id":"e1","summary":"Exam","updated":"not-a-timestamp",
                   "start":{"date":"2026-09-01"}}],"nextPageToken":null}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val events = makeService(engine).getEvents()
        (events[0] as DayEvent).updatedAt shouldBe 0L
    }

    test("getEvents handles null updated field") {
        val engine = MockEngine { _ ->
            respond(
                """{"items":[{"id":"e1","summary":"Quiz","start":{"date":"2026-09-01"}}],
                   "nextPageToken":null}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val events = makeService(engine).getEvents()
        (events[0] as DayEvent).updatedAt shouldBe 0L
    }

    test("getEvents follows pagination to collect all events") {
        var requestCount = 0
        val engine = MockEngine { req ->
            requestCount++
            if (requestCount == 1) {
                respond(
                    """{"items":[{"id":"e1","summary":"Event1","start":{"date":"2026-09-01"}}],
                       "nextPageToken":"page2"}""",
                    HttpStatusCode.OK, jsonHeader
                )
            } else {
                respond(
                    """{"items":[{"id":"e2","summary":"Event2","start":{"date":"2026-09-02"}}],
                       "nextPageToken":null}""",
                    HttpStatusCode.OK, jsonHeader
                )
            }
        }
        val events = makeService(engine).getEvents()
        events shouldHaveSize 2
        requestCount shouldBe 2
    }

    // ── withToken / auth ──────────────────────────────────────────────────────

    test("no access token throws Not authenticated") {
        val engine = MockEngine { _ -> respond("", HttpStatusCode.OK) }
        val repo = tokenRepo(access = null)
        val ex = shouldThrow<Exception> { makeService(engine, repo).getEvents() }
        ex.message shouldContain "Not authenticated"
    }

    test("401 triggers token refresh and retries successfully") {
        var callCount = 0
        val engine = MockEngine { _ ->
            callCount++
            if (callCount == 1) {
                respond("Unauthorized", HttpStatusCode.Unauthorized, jsonHeader)
            } else {
                respond(
                    """{"items":[],"nextPageToken":null}""",
                    HttpStatusCode.OK, jsonHeader
                )
            }
        }
        val repo = tokenRepo()
        val auth = authService(newToken = "refreshed-token")
        makeService(engine, repo, auth).getEvents()
        coVerify { repo.saveTokens("refreshed-token", any()) }
        callCount shouldBe 2
    }

    test("401 with no refresh token rethrows original error") {
        val engine = MockEngine { _ ->
            respond("Unauthorized", HttpStatusCode.Unauthorized, jsonHeader)
        }
        val repo = tokenRepo(refresh = null)
        shouldThrow<GoogleApiException> { makeService(engine, repo).getEvents() }
    }

    test("401 with auth service returning null rethrows original error") {
        val engine = MockEngine { _ ->
            respond("Unauthorized", HttpStatusCode.Unauthorized, jsonHeader)
        }
        val auth = authService(newToken = null)
        shouldThrow<GoogleApiException> { makeService(engine, auth = auth).getEvents() }
    }

    test("non-401 GoogleApiException is rethrown without refresh attempt") {
        val engine = MockEngine { _ ->
            respond("Server Error", HttpStatusCode.InternalServerError, jsonHeader)
        }
        val auth = mockk<GoogleAuthService>(relaxed = true)
        val ex = shouldThrow<GoogleApiException> { makeService(engine, auth = auth).getEvents() }
        ex.statusCode shouldBe 500
        coVerify(exactly = 0) { auth.refreshAccessToken(any()) }
    }

    // ── syncEvent ─────────────────────────────────────────────────────────────

    test("syncEvent with DayEvent completes without error") {
        val engine = MockEngine { req ->
            req.url.encodedPath shouldContain "events"
            respond("""{"id":"new-id"}""", HttpStatusCode.OK, jsonHeader)
        }
        val event = DayEvent(
            title = "Essay", source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE, date = LocalDate(2026, 9, 1)
        )
        makeService(engine).syncEvent(event, "cal-id")
    }

    test("syncEvent with TimeEvent completes without error") {
        val engine = MockEngine { req ->
            req.url.encodedPath shouldContain "events"
            respond("""{"id":"new-id"}""", HttpStatusCode.OK, jsonHeader)
        }
        val event = TimeEvent(
            title = "Lecture", source = EventSource.AI_GENERATED,
            category = AcademicCategory.CLASS,
            startTime = LocalTime(9, 0), endTime = LocalTime(10, 0),
            date = LocalDate(2026, 9, 1)
        )
        makeService(engine).syncEvent(event, "cal-id")
    }

    // ── createCalendar / listCalendars / deleteEvent ──────────────────────────

    test("createCalendar returns new calendar id") {
        val engine = MockEngine { _ ->
            respond(
                """{"id":"new-cal-id","summary":"My Cal"}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val id = makeService(engine).createCalendar("My Cal")
        id shouldBe "new-cal-id"
    }

    test("listCalendars maps items to RemoteCalendarMetadata") {
        val engine = MockEngine { _ ->
            respond(
                """{"items":[{"id":"cal1","summary":"School"},{"id":"cal2","summary":null}]}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val calendars = makeService(engine).listCalendars()
        calendars shouldHaveSize 2
        calendars[0].id shouldBe "cal1"
        calendars[0].name shouldBe "School"
        calendars[1].name shouldBe "Untitled Calendar"
    }

    test("deleteEvent succeeds on 200") {
        val engine = MockEngine { _ -> respond("", HttpStatusCode.OK, jsonHeader) }
        makeService(engine).deleteEvent("cal-id", "event-id")
    }

    test("ensureSuccess throws GoogleApiException on non-2xx") {
        val engine = MockEngine { _ ->
            respond("Bad Request", HttpStatusCode.BadRequest, jsonHeader)
        }
        val ex = shouldThrow<GoogleApiException> { makeService(engine).listCalendars() }
        ex.statusCode shouldBe 400
    }

    // ── toCalendarException ───────────────────────────────────────────────────

    test("toCalendarException(404) returns CalendarNotFoundException") {
        val ex = GoogleApiException(404, "Not Found")
        val result = ex.toCalendarException("cal-123")
        (result is CalendarNotFoundException) shouldBe true
        result.message shouldContain "cal-123"
    }

    test("toCalendarException(403) returns CalendarNotFoundException") {
        val ex = GoogleApiException(403, "Forbidden")
        val result = ex.toCalendarException("cal-456")
        (result is CalendarNotFoundException) shouldBe true
        result.message shouldContain "cal-456"
    }

    test("toCalendarException(500) returns same GoogleApiException") {
        val ex = GoogleApiException(500, "Server Error")
        val result = ex.toCalendarException("cal-id")
        result shouldBe ex
    }

    // ── syncEvent default arg bridge ──────────────────────────────────────────

    test("syncEvent uses primary calendar when calendarId is omitted") {
        val engine = MockEngine { req ->
            req.url.encodedPath shouldContain "primary"
            respond("""{"id":"new-id"}""", HttpStatusCode.OK, jsonHeader)
        }
        val event = DayEvent(
            title = "Essay", source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE, date = LocalDate(2026, 9, 1)
        )
        makeService(engine).syncEvent(event)  // no calendarId → default "primary"
    }

    // ── parseDateTime fallback for naive datetime strings ─────────────────────

    test("getEvents falls back to naive LocalDateTime parse when no tz offset") {
        val engine = MockEngine { _ ->
            respond(
                """{"items":[{"id":"e1","summary":"Exam",
                   "start":{"dateTime":"2026-09-01T09:00:00"},
                   "end":{"dateTime":"2026-09-01T10:00:00"}}],"nextPageToken":null}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val events = makeService(engine).getEvents("cal-id")
        events shouldHaveSize 1
        (events[0] is TimeEvent) shouldBe true
        (events[0] as TimeEvent).startTime shouldBe LocalTime(9, 0)
    }

    // ── TimeEvent with null summary ───────────────────────────────────────────

    test("getEvents uses Untitled Event for TimeEvent with null summary") {
        val engine = MockEngine { _ ->
            respond(
                """{"items":[{"id":"e1",
                   "start":{"dateTime":"2026-09-01T09:00:00Z"},
                   "end":{"dateTime":"2026-09-01T10:00:00Z"}}],"nextPageToken":null}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val events = makeService(engine).getEvents("cal-id")
        events[0].title shouldBe "Untitled Event"
    }

    // ── getEvents start.dateTime non-null but end.dateTime null → DayEvent path

    test("getEvents falls back to DayEvent when end has no dateTime") {
        val engine = MockEngine { _ ->
            respond(
                """{"items":[{"id":"e1","summary":"Mixed",
                   "start":{"dateTime":"2026-09-01T09:00:00Z"},
                   "end":{"date":"2026-09-01"}}],"nextPageToken":null}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val events = makeService(engine).getEvents("cal-id")
        events shouldHaveSize 1
        (events[0] is DayEvent) shouldBe true
    }

    // ── DayEvent with non-null start but null date field ─────────────────────

    test("getEvents uses fallback date when start.date is null on DayEvent path") {
        val engine = MockEngine { _ ->
            // start exists but has no date or dateTime fields
            respond(
                """{"items":[{"id":"e1","summary":"Mystery",
                   "start":{}}],"nextPageToken":null}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val events = makeService(engine).getEvents("cal-id")
        events shouldHaveSize 1
        (events[0] as DayEvent).date shouldBe LocalDate(2024, 1, 1)
    }

    // ── getEvents end absent when start.dateTime non-null ────────────────────

    test("getEvents falls back to DayEvent when end is absent") {
        val engine = MockEngine { _ ->
            respond(
                """{"items":[{"id":"e1","summary":"Test",
                   "start":{"dateTime":"2026-09-01T09:00:00Z"}}],"nextPageToken":null}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val events = makeService(engine).getEvents("cal-id")
        events shouldHaveSize 1
        (events[0] is DayEvent) shouldBe true
    }

    // ── GoogleCalendarItem with explicit null fields ───────────────────────

    test("getEvents handles item where all nullable fields are explicitly null") {
        val engine = MockEngine { _ ->
            respond(
                """{"items":[{"id":"e1","summary":null,"description":null,
                   "start":null,"end":null,"updated":null}],"nextPageToken":null}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val events = makeService(engine).getEvents("cal-id")
        events shouldHaveSize 1
        (events[0] as DayEvent).date shouldBe LocalDate(2024, 1, 1)
    }

    // ── GoogleCalendarItem with description field in response ─────────────

    test("getEvents parses item with description field in response") {
        val engine = MockEngine { _ ->
            respond(
                """{"items":[{"id":"e1","summary":"Lecture","description":"Room 101",
                   "start":{"date":"2026-09-01"}}],"nextPageToken":null}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val events = makeService(engine).getEvents("cal-id")
        events shouldHaveSize 1
        events[0].title shouldBe "Lecture"
    }

    // ── GoogleCalendarItem with all fields populated ──────────────────────────

    test("getEvents parses GoogleCalendarItem with all fields present") {
        val engine = MockEngine { _ ->
            respond(
                """{"items":[{"id":"e1","summary":"Lecture","description":"Room 101",
                   "start":{"dateTime":"2026-09-01T09:00:00Z","timeZone":"UTC"},
                   "end":{"dateTime":"2026-09-01T10:00:00Z","timeZone":"UTC"},
                   "updated":"2026-01-01T00:00:00.000Z"}],"nextPageToken":null}""",
                HttpStatusCode.OK, jsonHeader
            )
        }
        val events = makeService(engine).getEvents("cal-id")
        events shouldHaveSize 1
        events[0].title shouldBe "Lecture"
        ((events[0] as TimeEvent).updatedAt > 0L) shouldBe true
    }

    // ── @Serializable serialization paths (serialize() method coverage) ───────
    // These classes are only deserialized through Ktor body<T>() in production.
    // Explicitly calling encodeToString exercises the serialize() branches.

    test("GoogleCalendarItem serializes with all non-null fields") {
        val item = GoogleCalendarItem(
            id = "e1", summary = "Lecture", description = "Room 101",
            start = GoogleEventDateTime(dateTime = "2026-09-01T09:00:00Z", timeZone = "UTC"),
            end = GoogleEventDateTime(dateTime = "2026-09-01T10:00:00Z", timeZone = "UTC"),
            updated = "2026-01-01T00:00:00Z"
        )
        val out = Json.encodeToString(item)
        out shouldContain "Lecture"
        out shouldContain "Room 101"
    }

    test("GoogleCalendarItem serializes with all null fields") {
        val item = GoogleCalendarItem(id = "e1")
        val out = Json.encodeToString(item)
        out shouldContain "e1"
    }

    test("GoogleCalendarEventsResponse serializes with nextPageToken non-null") {
        val resp = GoogleCalendarEventsResponse(
            items = listOf(GoogleCalendarItem(id = "e1", summary = "Test")),
            nextPageToken = "page2"
        )
        val out = Json.encodeToString(resp)
        out shouldContain "page2"
    }

    test("GoogleCalendarEventsResponse serializes with nextPageToken null") {
        val resp = GoogleCalendarEventsResponse(items = emptyList(), nextPageToken = null)
        val out = Json.encodeToString(resp)
        out shouldContain "items"
    }

    test("GoogleCalendarListDiscoveryResponse serializes correctly") {
        val resp = GoogleCalendarListDiscoveryResponse(
            items = listOf(
                GoogleCalendarDiscoveryItem(id = "cal1", summary = "School"),
                GoogleCalendarDiscoveryItem(id = "cal2", summary = null)
            )
        )
        val out = Json.encodeToString(resp)
        out shouldContain "School"
        out shouldContain "cal2"
    }

    test("GoogleCalendarDiscoveryItem serializes with non-null summary") {
        val item = GoogleCalendarDiscoveryItem(id = "cal1", summary = "Math")
        val out = Json.encodeToString(item)
        out shouldContain "Math"
    }

    test("GoogleCalendarDiscoveryItem serializes with null summary") {
        val item = GoogleCalendarDiscoveryItem(id = "cal1", summary = null)
        val out = Json.encodeToString(item)
        out shouldContain "cal1"
    }

    test("GoogleEvent serializes TimeEvent datetime correctly") {
        val event = GoogleEvent(
            summary = "Lecture",
            start = GoogleEventDateTime(dateTime = "2026-09-01T09:00:00Z"),
            end = GoogleEventDateTime(dateTime = "2026-09-01T10:00:00Z")
        )
        val out = Json.encodeToString(event)
        out shouldContain "Lecture"
    }

    test("GoogleEvent serializes DayEvent date correctly") {
        val event = GoogleEvent(
            summary = "Essay Due",
            start = GoogleEventDateTime(date = "2026-09-01"),
            end = GoogleEventDateTime(date = "2026-09-02")
        )
        val out = Json.encodeToString(event)
        out shouldContain "Essay Due"
    }

    test("GoogleEventDateTime serializes with timeZone set") {
        val dt = GoogleEventDateTime(dateTime = "2026-09-01T09:00:00Z", timeZone = "America/New_York")
        val out = Json.encodeToString(dt)
        out shouldContain "America/New_York"
    }

    // ── Round-trip serialization for all @Serializable classes ───────────────

    test("GoogleCalendarItem round-trips through JSON with all fields") {
        val item = GoogleCalendarItem(
            id = "x", summary = "s", description = "d",
            start = GoogleEventDateTime(dateTime = "2026-01-01T00:00:00Z", date = "2026-01-01", timeZone = "UTC"),
            end = GoogleEventDateTime(dateTime = "2026-01-01T01:00:00Z", date = null, timeZone = null),
            updated = "2026-01-01T00:00:00Z"
        )
        val roundTripped = Json.decodeFromString<GoogleCalendarItem>(Json.encodeToString(item))
        roundTripped.id shouldBe "x"
        roundTripped.description shouldBe "d"
    }

    test("GoogleCalendarEventsResponse round-trips with non-null nextPageToken") {
        val resp = GoogleCalendarEventsResponse(
            items = listOf(GoogleCalendarItem(id = "e1")),
            nextPageToken = "tok"
        )
        val roundTripped = Json.decodeFromString<GoogleCalendarEventsResponse>(Json.encodeToString(resp))
        roundTripped.nextPageToken shouldBe "tok"
    }

    test("GoogleCalendarDiscoveryItem round-trips with and without summary") {
        val withSummary = GoogleCalendarDiscoveryItem(id = "c1", summary = "Cal")
        val withoutSummary = GoogleCalendarDiscoveryItem(id = "c2", summary = null)
        Json.decodeFromString<GoogleCalendarDiscoveryItem>(Json.encodeToString(withSummary)).summary shouldBe "Cal"
        Json.decodeFromString<GoogleCalendarDiscoveryItem>(Json.encodeToString(withoutSummary)).summary shouldBe null
    }

    test("GoogleCalendarListDiscoveryResponse round-trips correctly") {
        val resp = GoogleCalendarListDiscoveryResponse(items = listOf(
            GoogleCalendarDiscoveryItem(id = "c1", summary = "A"),
            GoogleCalendarDiscoveryItem(id = "c2", summary = null)
        ))
        val rt = Json.decodeFromString<GoogleCalendarListDiscoveryResponse>(Json.encodeToString(resp))
        rt.items shouldHaveSize 2
    }

    test("GoogleEvent round-trips correctly") {
        val event = GoogleEvent(
            summary = "Test",
            start = GoogleEventDateTime(dateTime = "2026-09-01T09:00:00Z", timeZone = "UTC"),
            end = GoogleEventDateTime(date = "2026-09-02", timeZone = null)
        )
        val rt = Json.decodeFromString<GoogleEvent>(Json.encodeToString(event))
        rt.summary shouldBe "Test"
        rt.end.date shouldBe "2026-09-02"
    }
})
