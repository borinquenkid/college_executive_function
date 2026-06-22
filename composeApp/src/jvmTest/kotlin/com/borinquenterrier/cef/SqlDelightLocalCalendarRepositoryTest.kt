package com.borinquenterrier.cef

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class SqlDelightLocalCalendarRepositoryTest : FunSpec({

    lateinit var driver: SqlDriver
    lateinit var database: AppDatabase
    lateinit var repo: SqlDelightLocalCalendarRepository

    beforeEach {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)
        repo = SqlDelightLocalCalendarRepository(database)
    }

    afterEach { driver.close() }

    // ── helpers ───────────────────────────────────────────────────────────────

    fun dayEvent(
        id: String = "d1",
        title: String = "Essay",
        gradeWeight: Float? = null,
        recurrence: Recurrence? = null,
        completionStatus: CompletionStatus = CompletionStatus.INCOMPLETE
    ) = DayEvent(
        id = id,
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE,
        date = LocalDate(2026, 9, 1),
        gradeWeight = gradeWeight,
        recurrence = recurrence,
        completionStatus = completionStatus
    )

    fun timeEvent(
        id: String = "t1",
        title: String = "Lecture",
        gradeWeight: Float? = null,
        recurrence: Recurrence? = null
    ) = TimeEvent(
        id = id,
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.CLASS,
        date = LocalDate(2026, 9, 1),
        startTime = LocalTime(9, 0),
        endTime = LocalTime(10, 0),
        gradeWeight = gradeWeight,
        recurrence = recurrence
    )

    // ── saveEvent / getAllEvents ───────────────────────────────────────────────

    test("saveEvent and getAllEvents round-trips a DayEvent") {
        repo.saveEvent(dayEvent())
        val events = repo.getAllEvents()
        events shouldHaveSize 1
        (events[0] is DayEvent) shouldBe true
        events[0].title shouldBe "Essay"
    }

    test("saveEvent and getAllEvents round-trips a TimeEvent") {
        repo.saveEvent(timeEvent())
        val events = repo.getAllEvents()
        events shouldHaveSize 1
        (events[0] is TimeEvent) shouldBe true
        (events[0] as TimeEvent).startTime shouldBe LocalTime(9, 0)
    }

    test("getAllEvents excludes DELETED_LOCALLY events") {
        repo.saveEvent(dayEvent(id = "d1"))
        repo.saveEvent(dayEvent(id = "d2"))
        repo.deleteEvent("d1")
        val events = repo.getAllEvents()
        events shouldHaveSize 1
        events[0].id shouldBe "d2"
    }

    // ── updateEvent ───────────────────────────────────────────────────────────

    test("updateEvent overwrites existing record") {
        repo.saveEvent(dayEvent(id = "d1", title = "Old Title"))
        repo.updateEvent(dayEvent(id = "d1", title = "New Title"))
        val events = repo.getAllEvents()
        events shouldHaveSize 1
        events[0].title shouldBe "New Title"
    }

    test("updateEvent preserves DayEvent null startTime and endTime") {
        repo.saveEvent(dayEvent())
        val events = repo.getAllEvents()
        (events[0] is DayEvent) shouldBe true
    }

    // ── gradeWeight null vs non-null ──────────────────────────────────────────

    test("DayEvent with null gradeWeight round-trips correctly") {
        repo.saveEvent(dayEvent(gradeWeight = null))
        val events = repo.getAllEvents()
        (events[0] as DayEvent).gradeWeight shouldBe null
    }

    test("DayEvent with non-null gradeWeight round-trips correctly") {
        repo.saveEvent(dayEvent(gradeWeight = 0.25f))
        val events = repo.getAllEvents()
        (events[0] as DayEvent).gradeWeight shouldBe 0.25f
    }

    test("TimeEvent with null gradeWeight round-trips correctly") {
        repo.saveEvent(timeEvent(gradeWeight = null))
        val events = repo.getAllEvents()
        (events[0] as TimeEvent).gradeWeight shouldBe null
    }

    test("TimeEvent with non-null gradeWeight round-trips correctly") {
        repo.saveEvent(timeEvent(gradeWeight = 0.5f))
        val events = repo.getAllEvents()
        (events[0] as TimeEvent).gradeWeight shouldBe 0.5f
    }

    // ── recurrence null vs non-null ───────────────────────────────────────────

    test("DayEvent with null recurrence round-trips correctly") {
        repo.saveEvent(dayEvent(recurrence = null))
        val events = repo.getAllEvents()
        (events[0] as DayEvent).recurrence shouldBe null
    }

    test("TimeEvent with null recurrence round-trips correctly") {
        repo.saveEvent(timeEvent(recurrence = null))
        val events = repo.getAllEvents()
        (events[0] as TimeEvent).recurrence shouldBe null
    }

    test("DayEvent with non-null recurrence round-trips correctly") {
        val rec = Recurrence(
            daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            startDate = LocalDate(2026, 9, 1),
            endDate = LocalDate(2026, 12, 1)
        )
        repo.saveEvent(dayEvent(recurrence = rec))
        val events = repo.getAllEvents()
        val result = (events[0] as DayEvent).recurrence
        result shouldNotBe null
        result!!.daysOfWeek shouldBe listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
    }

    test("TimeEvent with non-null recurrence round-trips correctly") {
        val rec = Recurrence(
            daysOfWeek = listOf(DayOfWeek.FRIDAY),
            startDate = LocalDate(2026, 9, 1),
            endDate = LocalDate(2026, 12, 15)
        )
        repo.saveEvent(timeEvent(recurrence = rec))
        val events = repo.getAllEvents()
        val result = (events[0] as TimeEvent).recurrence
        result shouldNotBe null
        result!!.daysOfWeek shouldBe listOf(DayOfWeek.FRIDAY)
    }

    // ── completionStatus fallback ─────────────────────────────────────────────

    test("mapEntityToEvent falls back to INCOMPLETE for invalid completionStatus") {
        // Insert a row directly with an invalid completionStatus to trigger the catch block
        database.appDatabaseQueries.insertEvent(
            id = "bad", title = "Bad", source = EventSource.AI_GENERATED.name,
            category = AcademicCategory.DEADLINE.name, date = "2026-09-01",
            startTime = null, endTime = null, recurrence = null,
            syncStatus = SyncStatus.LOCAL_ONLY.name, updatedAt = 0L,
            studyPlanStart = null, gradeWeight = null,
            completionStatus = "INVALID_STATUS", warning = null
        )
        val events = repo.getAllEvents()
        events shouldHaveSize 1
        events[0].completionStatus shouldBe CompletionStatus.INCOMPLETE
    }

    // ── hardDeleteEvent ───────────────────────────────────────────────────────

    test("hardDeleteEvent removes event from database") {
        repo.saveEvent(dayEvent(id = "d1"))
        repo.hardDeleteEvent("d1")
        repo.getAllEvents() shouldHaveSize 0
    }

    // ── clearLocalCalendar ────────────────────────────────────────────────────

    test("clearLocalCalendar removes all events") {
        repo.saveEvent(dayEvent(id = "d1"))
        repo.saveEvent(dayEvent(id = "d2"))
        repo.clearLocalCalendar()
        repo.getAllEvents() shouldHaveSize 0
    }

    // ── getEventsInRange ──────────────────────────────────────────────────────

    test("getEventsInRange returns only events within range") {
        repo.saveEvent(dayEvent(id = "before").copy(date = LocalDate(2026, 8, 31)))
        repo.saveEvent(dayEvent(id = "inside").copy(date = LocalDate(2026, 9, 1)))
        repo.saveEvent(dayEvent(id = "after").copy(date = LocalDate(2026, 9, 5)))
        val events = repo.getEventsInRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 3))
        events shouldHaveSize 1
        events[0].id shouldBe "inside"
    }

    // ── getEventsBySyncStatus ─────────────────────────────────────────────────

    test("getEventsBySyncStatus filters by sync status") {
        repo.saveEvent(dayEvent(id = "local").copy(syncStatus = SyncStatus.LOCAL_ONLY))
        repo.saveEvent(dayEvent(id = "synced").copy(syncStatus = SyncStatus.SYNCED))
        val localOnly = repo.getEventsBySyncStatus(SyncStatus.LOCAL_ONLY)
        localOnly shouldHaveSize 1
        localOnly[0].id shouldBe "local"
    }

    // ── getIncompleteEventsBefore ─────────────────────────────────────────────

    test("getIncompleteEventsBefore returns incomplete events before date") {
        repo.saveEvent(dayEvent(id = "old", completionStatus = CompletionStatus.INCOMPLETE).copy(date = LocalDate(2026, 8, 1)))
        repo.saveEvent(dayEvent(id = "future").copy(date = LocalDate(2026, 10, 1)))
        val events = repo.getIncompleteEventsBefore(LocalDate(2026, 9, 1))
        events shouldHaveSize 1
        events[0].id shouldBe "old"
    }

    // ── mapEntityToEvent: startTime set but endTime null → DayEvent ──────────

    test("mapEntityToEvent treats row with startTime but no endTime as DayEvent") {
        // Directly insert a row with startTime non-null but endTime null (malformed state)
        database.appDatabaseQueries.insertEvent(
            id = "partial", title = "Partial", source = EventSource.AI_GENERATED.name,
            category = AcademicCategory.DEADLINE.name, date = "2026-09-01",
            startTime = "09:00", endTime = null, recurrence = null,
            syncStatus = SyncStatus.LOCAL_ONLY.name, updatedAt = 0L,
            studyPlanStart = null, gradeWeight = null,
            completionStatus = CompletionStatus.INCOMPLETE.name, warning = null
        )
        val events = repo.getAllEvents()
        events shouldHaveSize 1
        (events[0] is DayEvent) shouldBe true
    }

    // ── getSettings ──────────────────────────────────────────────────────────

    test("getSettings returns null when no settings provided") {
        repo.getSettings() shouldBe null
    }
})
