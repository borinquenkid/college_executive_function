# Bug Fixing & Test Expansion Plan

This plan addresses the calendar empty state, unlinked save failures, and invalid data rejection in the College Executive Function (CEF) application.

---

## 📋 Identified Issues & Proposed Fixes

### 1. Hardcoded Date Range Filtering in Summer/Interim
* **File:** [AcademicCalendar.kt](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/AcademicCalendar.kt) and [SemesterResolver.kt](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/SemesterResolver.kt)
* **Bug:** The calendar's visible date range is retrieved via `SemesterResolver.getSemesterRange(today)`. Since the current date is in June (Interim/Summer), it returns a fixed 30-day window (`2026-06-14` to `2026-07-14`). However, the ingested syllabus `syllabus_bdan250.pdf` contains dates in Spring 2025, and any generated study blocks are mapped to either Spring or Fall. Because these events fall outside the 30-day Summer window, they are filtered out in [EventDisplayPipeline.kt](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/EventDisplayPipeline.kt), resulting in a blank Academic Calendar.
* **Proposed Fix:** Modify [AcademicCalendar.kt](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/AcademicCalendar.kt) to dynamically expand the visible calendar range if any in-memory or database-stored events lie outside the default semester/interim window.

### 2. Inability to Save/Push Locally when Google Calendar is Unlinked
* **File:** [CalendarAgent.kt](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/CalendarAgent.kt)
* **Bug:** In the default `local` run profile, `CalendarAgent.saveEvent()` and `updateEvent()` attempt to call the remote Google Calendar API first. If Google Calendar is not linked, these calls throw an exception, and the save/update operation fails entirely instead of falling back to saving locally.
* **Proposed Fix:** 
  1. Add a connection awareness check (`isGoogleLinked()`) in [CalendarAgent.kt](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/CalendarAgent.kt) by checking if `GOOGLE_ACCESS_TOKEN` is set in local Settings.
  2. If the user is unlinked, skip the remote calls and save locally.
  3. If the remote call fails with a network/connection exception (even if linked), catch the exception and fall back to local-only saving (`SyncStatus.LOCAL_ONLY`) to support offline operation.

### 3. "Push to Google Calendar" Button Disabled for Unlinked Users
* **File:** [StudioPanel.kt](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/StudioPanel.kt)
* **Bug:** In [StudioPanel.kt](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/StudioPanel.kt), the push button is disabled when `isConnected` (Google linked) is false, displaying `"Connect to Google to Push"`. This prevents offline users from committing their study plans.
* **Proposed Fix:** Keep the button enabled, but label it `"Save to Local Calendar"` when `isConnected` is false, and route it to standard calendar saving.

### 4. Preventing Invalid Data Rejection
* **Problem:** The system currently does not validate events before saving or updating them. We should reject invalid data such as blank titles or invalid time ranges (`startTime >= endTime` on `TimeEvent`).
* **Proposed Fix:** Implement a validation helper on the `Event` interface (`fun Event.validate()`) that throws `IllegalArgumentException` on invalid fields. Call this validation inside `CalendarAgent`'s write actions.

---

## 🧪 Testing Plan

### A. Unit Tests to Add/Expand
1. **Event Model Unit Tests:**
   * Test `Event.validate()` throws `IllegalArgumentException` for blank/empty titles.
   * Test `Event.validate()` throws `IllegalArgumentException` for `TimeEvent` where `startTime >= endTime`.
   * Test `Event.validate()` passes for valid `TimeEvent` and `DayEvent`.
2. **CalendarAgent Unit Tests (`CalendarAgentTest.kt`):**
   * Test `saveEvent` and `updateEvent` throw `IllegalArgumentException` if the event is invalid.
   * Test `saveEvent` saves locally only and bypasses remote calls if Google Calendar is unlinked.
   * Test `saveEvent` attempts remote save first if Google Calendar is linked.
   * Test `saveEvent` falls back to local save with `SyncStatus.LOCAL_ONLY` if the remote save throws a network exception.

### B. Integration Tests (IT) to Add/Expand
1. **Dynamic Calendar Range Expansion Test:**
   * In [EventDisplayPipelineTest.kt](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonTest/kotlin/com/borinquenterrier/cef/EventDisplayPipelineTest.kt) (or a new integration spec), verify that if we have events outside the default range, the returned list of events correctly includes them when the display range is expanded.
2. **Calendar Sync Integration Test (`CalendarSyncIntegrationTest.kt`):**
   * Add a test case verifying the full local-only save flow when the local profile is active but unlinked.

---

## 📝 Proposed Git Commit Message

```text
Fix calendar empty state, unlinked save failures, and prevent invalid data injection

- Validate events for blank titles and invalid time ranges before persistence.
- Check Google Calendar linking status dynamically and fall back to local-only saving when unlinked or offline.
- Enable study plan saving for local-only users with a "Save to Local Calendar" option.
- Dynamically expand the calendar visible range during Summer/Interim if events exist outside the default window.
```

---

## 🚀 Execution Steps

1. **Step 1:** Modify [Event.kt](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/Event.kt) to add `validate()`.
2. **Step 2:** Write/expand unit tests in `EventOverlapTest.kt` (or similar) to cover event validation.
3. **Step 3:** Modify [CalendarAgent.kt](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/CalendarAgent.kt) to check link status and fall back on failure.
4. **Step 4:** Add unit tests to `CalendarAgentTest.kt` to cover link checks, validation, and remote failure fallback.
5. **Step 5:** Modify [AcademicCalendar.kt](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/AcademicCalendar.kt) to dynamically expand date range.
6. **Step 6:** Expand `EventDisplayPipelineTest.kt` or `SemesterResolverTest.kt` for range expansion.
7. **Step 7:** Modify [StudioPanel.kt](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/StudioPanel.kt) to enable local save button.
8. **Step 8:** Run the local verification suite (`./verify_local.sh`) and build targets.
9. **Step 9:** Propose the final git commit.
