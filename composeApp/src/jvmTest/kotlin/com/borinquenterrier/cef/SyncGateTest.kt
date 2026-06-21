package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class SyncGateTest : FunSpec({

    fun repoWithSettings(settings: MapSettings?): StudentCalendarRepository {
        val repo = mockk<StudentCalendarRepository>(relaxed = true)
        every { repo.getSettings() } returns settings
        return repo
    }

    test("isLive returns false when settings are null") {
        val gate = SyncGate(repoWithSettings(null))
        gate.isLive() shouldBe false
    }

    test("isLive returns false when run_profile is test") {
        val s = MapSettings().also { it.putString("run_profile", "test") }
        val gate = SyncGate(repoWithSettings(s))
        gate.isLive() shouldBe false
    }

    test("isLive returns false when GOOGLE_ACCESS_TOKEN is missing") {
        val s = MapSettings().also { it.putString("run_profile", "local") }
        val gate = SyncGate(repoWithSettings(s))
        gate.isLive() shouldBe false
    }

    test("isLive returns false when GOOGLE_ACCESS_TOKEN is blank") {
        val s = MapSettings().also {
            it.putString("run_profile", "local")
            it.putString("GOOGLE_ACCESS_TOKEN", "   ")
        }
        val gate = SyncGate(repoWithSettings(s))
        gate.isLive() shouldBe false
    }

    test("isLive returns true when profile is local and token is present") {
        val s = MapSettings().also {
            it.putString("run_profile", "local")
            it.putString("GOOGLE_ACCESS_TOKEN", "valid-token")
        }
        val gate = SyncGate(repoWithSettings(s))
        gate.isLive() shouldBe true
    }

    test("isLive returns true when run_profile is absent (defaults to local)") {
        val s = MapSettings().also {
            it.putString("GOOGLE_ACCESS_TOKEN", "valid-token")
        }
        val gate = SyncGate(repoWithSettings(s))
        gate.isLive() shouldBe true
    }
})
