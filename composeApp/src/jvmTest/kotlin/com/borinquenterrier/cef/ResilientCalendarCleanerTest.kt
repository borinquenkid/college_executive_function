package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

class ResilientCalendarCleanerTest : FunSpec({

    fun day(id: String) = DayEvent(
        id = id, title = id, source = EventSource.STUDENT,
        category = AcademicCategory.DEADLINE, updatedAt = 1L, date = LocalDate(2026, 7, 1)
    )

    fun rateLimit() = GoogleApiException(403, """{"error":{"errors":[{"reason":"rateLimitExceeded"}],"message":"Rate Limit Exceeded"}}""")

    fun cleaner(remote: FakeRemoteCalendar) =
        ResilientCalendarCleaner(remote, maxRetries = 3, delayFn = {}) // no real backoff wait in tests

    test("clears an entire calendar") {
        val remote = FakeRemoteCalendar().apply { seed((1..5).map { day("e$it") }) }
        val result = runBlocking { cleaner(remote).clear("default") }
        result.deleted shouldBe 5
        result.allCleared.shouldBeTrue()
        remote.size() shouldBe 0
    }

    test("retries a rate-limited delete and still clears everything in one pass") {
        val remote = FakeRemoteCalendar().apply { seed((1..5).map { day("e$it") }) }
        val failedOnce = mutableSetOf<String>()
        remote.beforeDelete = { id -> if (id == "e3" && failedOnce.add(id)) throw rateLimit() }

        val result = runBlocking { cleaner(remote).clear("default") }

        result.deleted shouldBe 5   // e3 succeeded on retry
        remote.size() shouldBe 0
    }

    test("continues past an event it can never delete, reporting the failure") {
        val remote = FakeRemoteCalendar().apply { seed((1..5).map { day("e$it") }) }
        remote.beforeDelete = { id -> if (id == "e3") throw GoogleApiException(500, "permanent") }

        val result = runBlocking { cleaner(remote).clear("default") }

        result.deleted shouldBe 4   // the other four still cleared
        result.failed shouldBe 1
        remote.size() shouldBe 1    // only the un-deletable one remains → a re-run can retry it
    }
})
