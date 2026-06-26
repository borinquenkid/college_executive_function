package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

class CollisionResolutionTest : FunSpec({

    val date = LocalDate(2026, 10, 1)

    // Helper to create a test database and repo
    fun setupTestAgent(): Pair<EventAgent, StudentCalendarRepository> {
        val database = createTestDatabase()
        SqlDelightLocalCalendarRepository(database)

        // Mock token repo & auth service for remote repository
        val tokenRepo = GoogleTokenRepository(MapSettings())
        val authService = GoogleAuthService(MapSettings(), AppEnv())
        val syncService = GoogleCalendarSyncService(mockk(relaxed = true), GoogleTokenService(tokenRepo, authService))
        val preferencesRepository = PreferencesRepository(MapSettings())
        val idResolver = CalendarIdResolver(syncService, preferencesRepository)
        val conflictDetector = EventConflictDetector()
        val remoteRepo = GoogleRemoteCalendarRepository(
            syncService,
            idResolver,
            conflictDetector,
        )

        // Settings with run_profile = test to bypass remote calls in testing
        val settings = MapSettings()
        settings.putString("run_profile", "test")
        val localRepoWithSettings = SqlDelightLocalCalendarRepository(database, settings)

        val calendarAgent = CalendarAgent(localRepoWithSettings, remoteRepo)
        val eventAgent = EventAgent(
            aiService = mockk(relaxed = true),
            repository = calendarAgent,
            database = database,
            normalizationService = NormalizationService(),
            logger = Logger(settings)
        )
        return Pair(eventAgent, localRepoWithSettings)
    }

    test("Headless Integration: Clean Push without overlaps") {
        val (eventAgent, localRepo) = setupTestAgent()

        val event1 = TimeEvent(
            title = "Clean Class A",
            source = EventSource.CLASS,
            category = AcademicCategory.CLASS,
            date = date,
            startTime = LocalTime(10, 0),
            endTime = LocalTime(11, 30)
        )
        val event2 = TimeEvent(
            title = "Clean Study Block B",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date,
            startTime = LocalTime(14, 0),
            endTime = LocalTime(15, 0)
        )

        // Inject state
        val stateProp = eventAgent::class.java.getDeclaredField("_lastGeneratedEvents")
        stateProp.isAccessible = true
        (stateProp.get(eventAgent) as kotlinx.coroutines.flow.MutableStateFlow<List<Event>>).value =
            listOf(event1, event2)

        // ACT
        val conflicts = runBlocking { eventAgent.pushToCalendar() }

        // ASSERT
        conflicts shouldHaveSize 0

        val dbEvents = runBlocking { localRepo.getAllEvents() }
        dbEvents shouldHaveSize 2
        dbEvents.find { it.title == "Clean Class A" } shouldNotBe null
        dbEvents.find { it.title == "Clean Study Block B" } shouldNotBe null
    }

    test("Headless Integration: Priority Bump and Shift Cascade") {
        val (eventAgent, localRepo) = setupTestAgent()

        // 1. Pre-populate database with a Study Block (priority 10) and a Personal Event (priority 30)
        val existingStudy = TimeEvent(
            id = "existing-study-1",
            title = "Quantum Study",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date,
            startTime = LocalTime(10, 0),
            endTime = LocalTime(11, 0)
        )
        val existingPersonal = TimeEvent(
            id = "existing-personal-1",
            title = "Doctor Appointment",
            source = EventSource.MANUAL,
            category = AcademicCategory.REGULAR,
            date = date,
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 0)
        )

        runBlocking {
            localRepo.saveEvent(existingStudy)
            localRepo.saveEvent(existingPersonal)
        }

        // 2. Generate a new Class event (priority 80) that occupies 10:00-11:00 (colliding with Quantum Study)
        val newClass = TimeEvent(
            title = "Physics Lecture",
            source = EventSource.CLASS,
            category = AcademicCategory.CLASS,
            date = date,
            startTime = LocalTime(10, 0),
            endTime = LocalTime(11, 0)
        )

        val stateProp = eventAgent::class.java.getDeclaredField("_lastGeneratedEvents")
        stateProp.isAccessible = true
        (stateProp.get(eventAgent) as kotlinx.coroutines.flow.MutableStateFlow<List<Event>>).value =
            listOf(newClass)

        // ACT: Push new class to calendar.
        // Physics Lecture (80) should bump Quantum Study (10).
        // Quantum Study tries to reschedule:
        // - 10:00-11:00 occupied by Physics Lecture (80 >= 10)
        // - 09:00-10:00 occupied by Doctor Appointment (30 >= 10)
        // - 11:00-12:00 is free (working hours, doesn't overlap lunch 12-1)
        // Therefore, Quantum Study should shift to 11:00-12:00.
        val conflicts = runBlocking { eventAgent.pushToCalendar() }

        // ASSERT
        expectKnownFailure(issue = "https://github.com/borinquenkid/college_executive_function/issues/3") {
            conflicts shouldHaveSize 0

            val dbEvents = runBlocking { localRepo.getAllEvents() }
            dbEvents shouldHaveSize 3

            val lecture = dbEvents.find { it.title == "Physics Lecture" } as TimeEvent
            lecture.startTime shouldBe LocalTime(10, 0)

            val quantumStudy = dbEvents.find { it.title == "Quantum Study" } as TimeEvent
            quantumStudy.startTime shouldBe LocalTime(11, 0)
            quantumStudy.endTime shouldBe LocalTime(12, 0)

            val doctor = dbEvents.find { it.title == "Doctor Appointment" } as TimeEvent
            doctor.startTime shouldBe LocalTime(9, 0)
        }
    }

    test("Headless Integration: Complex Overlapping Batch (Internal conflicts resolve against each other)") {
        val (eventAgent, localRepo) = setupTestAgent()

        // Batch contains two study blocks at the same time: 09:00 to 10:00
        val study1 = TimeEvent(
            title = "Study Math",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date,
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 0)
        )
        val study2 = TimeEvent(
            title = "Study Chemistry",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date,
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 0)
        )

        val stateProp = eventAgent::class.java.getDeclaredField("_lastGeneratedEvents")
        stateProp.isAccessible = true
        (stateProp.get(eventAgent) as kotlinx.coroutines.flow.MutableStateFlow<List<Event>>).value =
            listOf(study1, study2)

        // ACT
        val conflicts = runBlocking { eventAgent.pushToCalendar() }

        // ASSERT
        conflicts shouldHaveSize 0

        val dbEvents = runBlocking { localRepo.getAllEvents() }
        dbEvents shouldHaveSize 2

        val math = dbEvents.find { it.title == "Study Math" } as TimeEvent
        val chem = dbEvents.find { it.title == "Study Chemistry" } as TimeEvent

        // One of them should remain at 9:00, the other should shift (e.g. to 10:00 or 11:00 or 13:00 depending on resolution sequence)
        math.startTime shouldNotBe chem.startTime
    }

    test("Headless Integration: Post-Deadline Shift (Late Leeway warning)") {
        val (eventAgent, localRepo) = setupTestAgent()

        // Fill all working hours on Oct 1 and the preceding 7 days
        val existing = mutableListOf<Event>()
        for (i in 0..7) {
            val d = date.minus(i, DateTimeUnit.DAY)
            // Schedule personal events filling the active hours
            existing.add(
                TimeEvent(
                    title = "Busy 9-12",
                    source = EventSource.MANUAL,
                    date = d,
                    startTime = LocalTime(9, 0),
                    endTime = LocalTime(12, 0)
                )
            )
            existing.add(
                TimeEvent(
                    title = "Busy 13-17",
                    source = EventSource.MANUAL,
                    date = d,
                    startTime = LocalTime(13, 0),
                    endTime = LocalTime(17, 0)
                )
            )
            existing.add(
                TimeEvent(
                    title = "Busy 19-21",
                    source = EventSource.MANUAL,
                    date = d,
                    startTime = LocalTime(19, 0),
                    endTime = LocalTime(21, 0)
                )
            )
        }

        runBlocking {
            existing.forEach { localRepo.saveEvent(it) }
        }

        // New Study block on date
        val lateStudy = TimeEvent(
            title = "Late Night Study",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date,
            startTime = LocalTime(9, 0),
            endTime = LocalTime(11, 0)
        )

        val stateProp = eventAgent::class.java.getDeclaredField("_lastGeneratedEvents")
        stateProp.isAccessible = true
        (stateProp.get(eventAgent) as kotlinx.coroutines.flow.MutableStateFlow<List<Event>>).value =
            listOf(lateStudy)

        // ACT
        val conflicts = runBlocking { eventAgent.pushToCalendar() }

        // ASSERT
        conflicts shouldHaveSize 0

        val dbEvents = runBlocking { localRepo.getAllEvents() }
        val resolvedLateStudy = dbEvents.find { it.title == "Late Night Study" } as TimeEvent

        // Should have shifted forward to a future date
        resolvedLateStudy.date shouldBe date.plus(1, DateTimeUnit.DAY)
    }
})
