package com.borinquenterrier.cef

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A clock fixed well before every date the extraction tests use. Production reads "today" to drop events a
 * model parked on the run date; with the real clock a test whose hard-coded date equals the day the suite runs
 * fails for that one day (PipelineMatrixTest's demo scenario did, on 2026-11-10). Tests that build a real
 * [EventGenerationService] pass this so their fixed dates mean the same on every run.
 */
val FIXED_TEST_CLOCK: Clock = object : Clock {
    override fun now(): Instant = Instant.parse("2026-01-15T12:00:00Z")
}
