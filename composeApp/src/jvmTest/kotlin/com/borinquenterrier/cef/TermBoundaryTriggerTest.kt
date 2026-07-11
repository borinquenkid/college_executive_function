package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

/**
 * Tests for [TermBoundaryTrigger] and [processNewlyCompletedTerms] (ADR 0004 / ROADMAP Phase 13,
 * XM-3). Uses a real in-memory SQLite DB (not mocked) via [TermProfileRepository] — no live API
 * calls, so this is a plain unit test despite exercising the DB layer.
 */
class TermBoundaryTriggerTest : FunSpec({

    fun deadline(sourceId: String, date: LocalDate) = DayEvent(
        id = "$sourceId-$date",
        title = "Assignment",
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE,
        date = date,
        sourceId = sourceId
    )

    fun newRepository(): TermProfileRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        return TermProfileRepository(AppDatabase(driver))
    }

    // Fall 2025 range: Aug 1 - Dec 31 2025. Spring 2026 range: Jan 1 - May 31 2026.
    val fallEvent = deadline("M408N_differential_calculus.pdf", LocalDate(2025, 10, 6))
    val springEvent = deadline("M408N_differential_calculus.pdf", LocalDate(2026, 3, 4))

    test("detectNewlyCompletedTerms flags the older term but excludes the term containing the newest event") {
        val result = TermBoundaryTrigger.detectNewlyCompletedTerms(
            events = listOf(fallEvent, springEvent),
            alreadyRecordedTermStarts = emptySet()
        )
        result shouldBe listOf(LocalDate(2025, 8, 1) to LocalDate(2025, 12, 31))
    }

    test("detectNewlyCompletedTerms returns nothing once the term is already recorded") {
        val result = TermBoundaryTrigger.detectNewlyCompletedTerms(
            events = listOf(fallEvent, springEvent),
            alreadyRecordedTermStarts = setOf(LocalDate(2025, 8, 1))
        )
        result shouldBe emptyList()
    }

    test("detectNewlyCompletedTerms on an empty event list detects nothing") {
        TermBoundaryTrigger.detectNewlyCompletedTerms(emptyList(), emptySet()) shouldBe emptyList()
    }

    test("processNewlyCompletedTerms writes exactly one profile for the completed term, none for the ongoing one") {
        val repository = newRepository()
        processNewlyCompletedTerms(listOf(fallEvent, springEvent), repository)

        repository.count() shouldBe 1L
        val saved = repository.getAll().single()
        saved.termStart shouldBe LocalDate(2025, 8, 1)
    }

    test("re-processing the same event list is idempotent — no duplicate row") {
        val repository = newRepository()
        processNewlyCompletedTerms(listOf(fallEvent, springEvent), repository)
        processNewlyCompletedTerms(listOf(fallEvent, springEvent), repository)

        repository.count() shouldBe 1L
    }

    test("once a later term's events arrive, the previously-ongoing term also becomes eligible and gets recorded") {
        val repository = newRepository()
        processNewlyCompletedTerms(listOf(fallEvent, springEvent), repository)
        repository.count() shouldBe 1L // only fall so far

        val summerEvent = deadline("summer_course.pdf", LocalDate(2026, 7, 1))
        processNewlyCompletedTerms(listOf(fallEvent, springEvent, summerEvent), repository)

        repository.count() shouldBe 2L // fall + spring now both completed; summer still ongoing
        repository.getAll().map { it.termStart }.toSet() shouldBe setOf(
            LocalDate(2025, 8, 1), LocalDate(2026, 1, 1)
        )
    }
})
