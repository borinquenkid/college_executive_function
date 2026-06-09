package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SettingsPreferencesParserTest : FunSpec({

    val defaults = StudyPreferences()

    fun parse(
        studyStart: String   = "9",
        studyEnd: String     = "21",
        lunchStart: String   = "12",
        lunchEnd: String     = "13",
        dinnerStart: String  = "17",
        dinnerEnd: String    = "19",
        maxBlock: String     = "2",
        breakMin: String     = "30",
        bugReports: Boolean  = false,
        calId: String        = "default",
        calName: String      = "CEF Academic",
        current: StudyPreferences = defaults
    ) = SettingsPreferencesParser.parse(
        studyStartStr            = studyStart,
        studyEndStr              = studyEnd,
        lunchStartStr            = lunchStart,
        lunchEndStr              = lunchEnd,
        dinnerStartStr           = dinnerStart,
        dinnerEndStr             = dinnerEnd,
        maxStudyBlockStr         = maxBlock,
        preferredBreakStr        = breakMin,
        shareAnonymousBugReports = bugReports,
        googleCalendarId         = calId,
        googleCalendarName       = calName,
        currentPrefs             = current
    )

    // ── Happy-path parsing ────────────────────────────────────────────────────

    test("parses all valid integer strings into correct StudyPreferences fields") {
        val result = parse(
            studyStart  = "8",
            studyEnd    = "20",
            lunchStart  = "11",
            lunchEnd    = "12",
            dinnerStart = "18",
            dinnerEnd   = "20",
            maxBlock    = "3",
            breakMin    = "15"
        )

        result.studyStartHour        shouldBe 8
        result.studyEndHour          shouldBe 20
        result.lunchStartHour        shouldBe 11
        result.lunchEndHour          shouldBe 12
        result.dinnerStartHour       shouldBe 18
        result.dinnerEndHour         shouldBe 20
        result.maxStudyBlockHours    shouldBe 3
        result.preferredBreakMinutes shouldBe 15
    }

    // ── Fallback on invalid input ─────────────────────────────────────────────

    test("falls back to currentPrefs.studyStartHour when studyStartStr is blank") {
        val current = StudyPreferences(studyStartHour = 7)
        val result  = parse(studyStart = "", current = current)
        result.studyStartHour shouldBe 7
    }

    test("falls back to currentPrefs.studyEndHour when studyEndStr is non-numeric") {
        val current = StudyPreferences(studyEndHour = 22)
        val result  = parse(studyEnd = "abc", current = current)
        result.studyEndHour shouldBe 22
    }

    test("falls back to currentPrefs.lunchStartHour when lunchStartStr is non-numeric") {
        val current = StudyPreferences(lunchStartHour = 11)
        val result  = parse(lunchStart = "noon", current = current)
        result.lunchStartHour shouldBe 11
    }

    test("falls back to currentPrefs.maxStudyBlockHours when maxStudyBlockStr is empty") {
        val current = StudyPreferences(maxStudyBlockHours = 4)
        val result  = parse(maxBlock = "", current = current)
        result.maxStudyBlockHours shouldBe 4
    }

    test("falls back to currentPrefs.preferredBreakMinutes when preferredBreakStr is non-numeric") {
        val current = StudyPreferences(preferredBreakMinutes = 45)
        val result  = parse(breakMin = "half hour", current = current)
        result.preferredBreakMinutes shouldBe 45
    }

    // ── Boolean and string passthrough ────────────────────────────────────────

    test("passes shareAnonymousBugReports through correctly") {
        parse(bugReports = true).shareAnonymousBugReports  shouldBe true
        parse(bugReports = false).shareAnonymousBugReports shouldBe false
    }

    test("passes googleCalendarId and googleCalendarName through correctly") {
        val result = parse(calId = "cal_123", calName = "My School Calendar")
        result.googleCalendarId   shouldBe "cal_123"
        result.googleCalendarName shouldBe "My School Calendar"
    }

    // ── Default fallback when no currentPrefs supplied ────────────────────────

    test("uses StudyPreferences defaults when all strings are invalid and no currentPrefs given") {
        val result = SettingsPreferencesParser.parse(
            studyStartStr            = "x",
            studyEndStr              = "x",
            lunchStartStr            = "x",
            lunchEndStr              = "x",
            dinnerStartStr           = "x",
            dinnerEndStr             = "x",
            maxStudyBlockStr         = "x",
            preferredBreakStr        = "x",
            shareAnonymousBugReports = false,
            googleCalendarId         = "default",
            googleCalendarName       = "CEF Academic"
            // currentPrefs omitted → uses StudyPreferences() default
        )

        result.studyStartHour        shouldBe defaults.studyStartHour
        result.studyEndHour          shouldBe defaults.studyEndHour
        result.lunchStartHour        shouldBe defaults.lunchStartHour
        result.lunchEndHour          shouldBe defaults.lunchEndHour
        result.dinnerStartHour       shouldBe defaults.dinnerStartHour
        result.dinnerEndHour         shouldBe defaults.dinnerEndHour
        result.maxStudyBlockHours    shouldBe defaults.maxStudyBlockHours
        result.preferredBreakMinutes shouldBe defaults.preferredBreakMinutes
    }
})
