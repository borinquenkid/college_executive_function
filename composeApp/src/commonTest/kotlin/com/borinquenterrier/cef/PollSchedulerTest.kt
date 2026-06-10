package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

class PollSchedulerTest : StringSpec({

    "shouldPoll returns true when force=true" {
        val settings = mockk<Settings>()
        val logger = mockk<Logger>()
        val scheduler = PollScheduler(settings, logger)

        scheduler.shouldPoll(force = true) shouldBe true
    }

    "getLastPollTime retrieves from settings" {
        val lastTime = 1234567890L
        val settings = mockk<Settings>()
        every { settings.getLong("cef_harness_last_poll_time", 0L) } returns lastTime

        val logger = mockk<Logger>()
        val scheduler = PollScheduler(settings, logger)

        scheduler.getLastPollTime() shouldBe lastTime
    }

    "setLastPollTime stores to settings" {
        val settings = mockk<Settings>()
        val slot = slot<Long>()
        every { settings.putLong("cef_harness_last_poll_time", capture(slot)) } returns Unit

        val logger = mockk<Logger>()
        val scheduler = PollScheduler(settings, logger)

        scheduler.setLastPollTime(9876543210L)
        slot.captured shouldBe 9876543210L
    }
})
