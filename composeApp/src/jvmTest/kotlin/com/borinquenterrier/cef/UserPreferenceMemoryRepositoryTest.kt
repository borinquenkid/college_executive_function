package com.borinquenterrier.cef

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class UserPreferenceMemoryRepositoryTest : FunSpec({

    lateinit var driver: SqlDriver
    lateinit var database: AppDatabase
    lateinit var repository: UserPreferenceMemoryRepository

    beforeEach {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)
        repository = SqlDelightUserPreferenceMemoryRepository(database)
    }

    afterEach {
        driver.close()
    }

    test("logOverride saves manual overrides to database") {
        val testEvent = TimeEvent(
            id = "event-1",
            title = "Study Math",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = LocalDate(2026, 6, 8), // Monday
            startTime = LocalTime(18, 0),
            endTime = LocalTime(20, 0)
        )

        repository.logOverride(OverrideAction.DELETE, testEvent)

        val logs = database.appDatabaseQueries.selectAllOverrideLogs().executeAsList()
        logs.size shouldBe 1
        logs[0].actionType shouldBe "DELETE"
        logs[0].dayOfWeek shouldBe "MONDAY"
        logs[0].startHour shouldBe 18L
        logs[0].endHour shouldBe 20L
    }

    test("pruneOldLogs deletes logs older than threshold") {
        val testEvent = TimeEvent(
            id = "event-1",
            title = "Study Math",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = LocalDate(2026, 6, 8), // Monday
            startTime = LocalTime(18, 0),
            endTime = LocalTime(20, 0)
        )

        repository.logOverride(OverrideAction.DELETE, testEvent)

        // Wait a millisecond to make sure log timestamp is older than prune time
        val pruneTime = Clock.System.now().toEpochMilliseconds() + 10

        repository.pruneOldLogs(pruneTime)

        val logs = database.appDatabaseQueries.selectAllOverrideLogs().executeAsList()
        logs.size shouldBe 0
    }

    test("getDerivedConstraints groups contiguous hours exceeding threshold") {
        val mondayEvent1 = TimeEvent(
            id = "m1",
            title = "Study block 1",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = LocalDate(2026, 6, 8), // Monday
            startTime = LocalTime(18, 0),
            endTime = LocalTime(20, 0)
        )

        val mondayEvent2 = TimeEvent(
            id = "m2",
            title = "Study block 2",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = LocalDate(2026, 6, 15), // another Monday
            startTime = LocalTime(18, 0),
            endTime = LocalTime(20, 0)
        )

        val mondayEvent3 = TimeEvent(
            id = "m3",
            title = "Study block 3",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = LocalDate(2026, 6, 8), // Monday
            startTime = LocalTime(15, 0),
            endTime = LocalTime(16, 0)
        )

        val mondayEvent4 = TimeEvent(
            id = "m4",
            title = "Study block 4",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = LocalDate(2026, 6, 15), // another Monday
            startTime = LocalTime(15, 0),
            endTime = LocalTime(16, 0)
        )

        val fridayEvent = TimeEvent(
            id = "f1",
            title = "Friday block",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = LocalDate(2026, 6, 12), // Friday
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 0)
        )

        repository.logOverride(OverrideAction.DELETE, mondayEvent1)
        repository.logOverride(OverrideAction.DELETE, mondayEvent2)
        repository.logOverride(OverrideAction.DELETE, mondayEvent3)
        repository.logOverride(OverrideAction.DELETE, mondayEvent4)
        repository.logOverride(OverrideAction.DELETE, fridayEvent) // only logged once

        val constraints = repository.getDerivedConstraints(overrideThreshold = 2)

        constraints.size shouldBe 2

        val m15 = constraints.find { it.dayOfWeek == DayOfWeek.MONDAY && it.startHour == 15 }
        m15 shouldNotBe null
        m15!!.endHour shouldBe 16

        val m18 = constraints.find { it.dayOfWeek == DayOfWeek.MONDAY && it.startHour == 18 }
        m18 shouldNotBe null
        m18!!.endHour shouldBe 20

        constraints.any { it.dayOfWeek == DayOfWeek.FRIDAY } shouldBe false
    }
})
