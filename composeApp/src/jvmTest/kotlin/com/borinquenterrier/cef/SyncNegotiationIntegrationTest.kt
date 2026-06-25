package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class SyncNegotiationIntegrationTest : FunSpec({

    val date = LocalDate(2026, 10, 1) // Thursday

    fun createGoogleMockEngine(eventsJson: String): MockEngine {
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

    test("SyncNegotiation: should propose shifting study block when remote event collides") {
        val localRepo = SqlDelightLocalCalendarRepository(createTestDatabase())
        val settings = MapSettings()
        val preferencesRepository = PreferencesRepository(settings)

        // Save user study preferences: 9:00 to 17:00 study hours
        preferencesRepository.savePreferences(
            StudyPreferences(
                studyStartHour = 9,
                studyEndHour = 17
            )
        )

        // Save a local study block from 10:00 to 11:00 on Thursday (date = 2026-10-01)
        val studyBlock = TimeEvent(
            id = "study-1",
            title = "Study Math",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date,
            startTime = LocalTime(10, 0),
            endTime = LocalTime(11, 0),
            syncStatus = SyncStatus.SYNCED
        )
        localRepo.updateEvent(studyBlock)

        // Remote has a new exam from 09:30 to 10:30 on the same day, which collides with the study block!
        val eventsJson = """
        {
            "items": [
                {
                    "id": "exam-1",
                    "summary": "Midterm Exam",
                    "start": {"dateTime": "2026-10-01T09:30:00"},
                    "end": {"dateTime": "2026-10-01T10:30:00"},
                    "updated": "2026-06-05T08:00:00Z"
                }
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
        val authService = GoogleAuthService(MapSettings(), AppEnv())
        val syncService = GoogleCalendarSyncService(httpClient, GoogleTokenService(tokenRepo, authService))
        val idResolver = CalendarIdResolver(syncService, preferencesRepository)
        val conflictDetector = EventConflictDetector()
        val remoteRepo = GoogleRemoteCalendarRepository(
            syncService,
            idResolver,
            conflictDetector,
        )

        val unifiedRepo = CalendarAgent(
            localRepo = localRepo,
            remoteRepo = remoteRepo,
            userPreferenceMemoryRepository = UserPreferenceMemoryRepository.NoOp,
            preferencesRepository = preferencesRepository
        )

        // Check for proposals
        val negotiation = unifiedRepo.checkSyncProposals()
        negotiation.proposals shouldHaveSize 1

        val proposal = negotiation.proposals.first()
        proposal.shouldBeInstanceOf<SyncProposal.StudyBlockShift>()
        proposal.originalEvent.id shouldBe "study-1"

        // Since 9:30 to 10:30 is taken by the exam, and study start is 9:00, 
        // the proposed event should shift outside of that range (e.g. 10:30 or later)
        val proposed = proposal.proposedEvent as TimeEvent
        proposed.startTime shouldBe LocalTime(10, 30)
        proposed.endTime shouldBe LocalTime(11, 30)

        // Apply negotiation
        unifiedRepo.applySyncNegotiation(negotiation)

        // Verify local database now has both the exam and the shifted study block
        val allEvents = localRepo.getAllEvents()
        allEvents shouldHaveSize 2

        val dbStudy = allEvents.find { it.id == "study-1" } as TimeEvent
        dbStudy.startTime shouldBe LocalTime(10, 30)
        dbStudy.endTime shouldBe LocalTime(11, 30)

        val dbExam = allEvents.find { it.id == "exam-1" } as TimeEvent
        dbExam.title shouldBe "Midterm Exam"
    }
})
