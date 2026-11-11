package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Ratchet against date rot: shared code must take a `clock: Clock = Clock.System` parameter instead of calling
 * `Clock.System` directly, so tests (on every target) can pin "today". Counts below are the direct calls that
 * still exist; a file may only go DOWN. Adding a call, or a new file with one, fails this test: inject a clock.
 * Default-parameter declarations (`clock: Clock = Clock.System`) are the sanctioned form and are not counted.
 */
class ClockUsageGuardTest : FunSpec({

    val allowed = mapOf(
        "AcademicCalendar.kt" to 1, "AddRoutineItemDialog.kt" to 1, "AgentHarness.kt" to 2, "AppContent.kt" to 2,
        "AppController.kt" to 3, "ChatPanel.kt" to 3, "ContextAgent.kt" to 2, "DatePickerField.kt" to 1,
        "Event.kt" to 2, "EventBuilder.kt" to 2, "EventItemView.kt" to 1, "GeminiModelNegotiator.kt" to 2,
        "GeminiRetryService.kt" to 7, "HttpOtelTracer.kt" to 1, "IngestingProgressDialog.kt" to 1,
        "PollScheduler.kt" to 1, "SqlDelightChatRepository.kt" to 1, "SqlDelightSourceRepository.kt" to 3,
        "SqlDelightUserPreferenceMemoryRepository.kt" to 2, "StudioPanel.kt" to 1, "TermProfileRepository.kt" to 1
    )

    test("direct Clock.System calls in commonMain never grow") {
        val dir = File("src/commonMain")
        val found = dir.walkTopDown().filter { it.extension == "kt" }
            .associate { f ->
                f.name to f.readLines().count { "Clock.System" in it && "Clock = Clock.System" !in it }
            }
            .filterValues { it > 0 }
        val grew = found.filter { (name, n) -> n > (allowed[name] ?: 0) }
        grew shouldBe emptyMap()
    }
})
