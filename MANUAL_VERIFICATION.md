# Manual Verification Script: College Executive Function (CEF)

This manual test plan guides a human tester through verifying all core scenarios, agent workflows, and features using any of the three primary clients: **JVM Desktop (Compose JVM)**, **Android**, or **iOS**.

---

## 🛠️ Prerequisites & Setup

1. **Build & Run the Target Client**:
   - **JVM Desktop:** Run `./gradlew :composeApp:jvmRun`
   - **Android:** Run `./gradlew :androidApp:installDebug` (or launch in Android Studio)
   - **iOS:** Open `iosApp/iosApp.xcworkspace` in Xcode and run on a Simulator or Device.
2. **Access Credentials**:
   - A free **Gemini API Key** (from Google AI Studio).
   - A Google Account for testing **Google Calendar & Google Drive Sync**.
3. **Sample Documents**:
   - A sample syllabus document (PDF, DOCX, or TXT) containing a few deadlines and weekly class times.

---

## 📋 Test Scenarios

### Scenario 1: Setup & API Key Injection
* **Goal:** Verify that the user can configure their API key and that it persists correctly.
* **Steps:**
  1. Open the app and navigate to the **Settings** panel (gear icon).
  2. Input a valid Gemini API Key into the input field and click **Save**.
  3. Exit the settings screen and return; verify the saved key persists (or is obfuscated/masked).
  4. Click **Clear API Key**; verify the input is cleared and the key is removed from storage.
  5. Save the valid key again before proceeding.

---

### Scenario 2: Weekly Routine Management
* **Goal:** Verify creating, viewing, and persisting recurring weekly schedule items.
* **Steps:**
  1. Navigate to the **Weekly Routine** screen (e.g. from the calendar panel).
  2. Click **Add Routine Item**.
  3. Enter a title (e.g., "Physics Lecture"), select a Day of the Week (e.g., Monday), and set a time range (e.g., 09:30 AM to 11:30 AM). Click **Save**.
  4. Verify the item appears in the weekly grid/list.
  5. Close and restart the app; verify the routine item persists.

---

### Scenario 3: Syllabus Ingestion & Source Categorization
* **Goal:** Verify native file loading, automatic source categorization, and metadata display.
* **Steps:**
  1. Go to the **Studio** (home) panel.
  2. Click **Add Source** → select **Local File** and pick your sample syllabus (e.g., a PDF/DOCX).
  3. Wait a few seconds for extraction and AI analysis to complete.
  4. Verify the source appears in the list under the correct category tag: **"Syllabus"** (it should automatically classify it).
  5. Verify the source fragments list displays correct page extracts.

---

### Scenario 4: AI Study Plan Generation & Weighted Deliverables
* **Goal:** Verify AI analysis extracts deadlines, reads weights, and schedules study blocks around constraints.
* **Steps:**
  1. From the **Studio** (home) panel, select the syllabus you just added and click **Generate Study Plan**.
  2. Switch to the **Academic Calendar** view.
  3. Verify that the AI generated both:
     - **Deliverable events** (Exams, Assignments) matching the dates in the syllabus.
     - Proactive **Study Blocks** leading up to those deadlines.
  4. Look for an event with a higher grade weight (e.g. Final Project - 25% weight) and verify that its study blocks are longer/more frequent compared to a lower-weight event (e.g. Quiz - 5%).
  5. Check that all study blocks are placed within preferred study hours (default: 9:00 AM to 9:00 PM) and avoid lunch/dinner breaks.

---

### Scenario 5: Programmatic Collision Resolution
* **Goal:** Verify that a newly synced event that overlaps a local study block triggers automatic rescheduling.
* **Steps:**
  1. Locate a day with an AI study block in the app's **Academic Calendar**.
  2. In **Google Calendar** (browser), create a new event (e.g., a class or exam) on the same day and time that overlaps with that study block.
  3. Return to the app and tap **Sync** on the calendar screen.
  4. Verify that the system automatically shifts the colliding study block to the next available slot within study hours (without overlapping classes, exams, or breaks).

---

### Scenario 6: Task Decomposition ("Break It Down" UI)
* **Goal:** Verify recursive task decomposition into sub-tasks via the dialog.
* **Steps:**
  1. In the **Academic Calendar** view, click on a generated **Deadline** or **Finals** event.
  2. Click the **"Break It Down"** button in the details panel/dialog.
  3. Wait for the AI to decompose the assignment into recursive sub-tasks.
  4. Verify the checklist of steps (e.g. "Research topic", "Write draft", "Review citations") is displayed with individual estimated durations.
  5. Click **Add Steps to Calendar** and verify these sub-tasks are scheduled as new calendar events.

---

### Scenario 7: Multi-Source Chat Context
* **Goal:** Verify that the chat assistant reasons across all stored documents and maintains history.
* **Steps:**
  1. Ingest a second syllabus/reading note source.
  2. Open the **Chat** panel.
  3. Verify the scope is set to **"All Sources"** (default). If not, tap to switch to it.
  4. Ask a question that requires comparing both documents (e.g. *"What are my exam dates for both classes?"*). Verify the AI references both sources.
  5. Send a follow-up query (e.g. *"Which of these happens first?"*). Verify the AI remembers the previous turn's context.

---

### Scenario 8: Interactive Sync Negotiation
* **Goal:** Verify two-way synchronization, conflict detection, and interactive shift proposals.
* **Steps:**
  1. Connect your Google Account in **Settings** to link Workspace.
  2. Perform an initial **Sync** and verify local events upload to Google Calendar.
  3. Open Google Calendar in a browser and **move** a synced class/exam event to a different time slot (which will overlap with a local study block).
  4. Return to the app and click **Sync Now** on the calendar screen.
  5. Verify that a **"Google Calendar Sync Proposals"** dialog appears showing:
     - The remote update ("Midterm Exam moved from Monday to Tuesday").
     - Proposed shifts ("Shift study block 'Study Math' to Wednesday to avoid collision").
  6. Click **Accept Proposal** and verify that both the remote update and proposed shifts are written to your local calendar.

---

### Scenario 9: ICS Calendar Export
* **Goal:** Verify exporting your study plan to a standard `.ics` file.
* **Steps:**
  1. Navigate to the **Studio** (home) panel.
  2. Click the **Export Study Plan to .ics** button.
  3. Verify a success toast/dialog appears with the export location:
     - **JVM:** Check your `~/Downloads` directory for `cef_export.ics`.
     - **Android:** Check the device downloads directory.
     - **iOS:** Verify the native share sheet pops up asking where to save or send the `.ics` file.

---

### Scenario 10: Progress & Time-Remaining Indicators
* **Goal:** Verify visual countdowns and project completion tracking.
* **Steps:**
  1. View your deadline events in the calendar list.
  2. Verify each deadline card displays a **"Due in X days"** countdown chip.
  3. Open a deadline event card; verify the presence of a progress bar reflecting the completed time between the start of the study plan and the final due date.
  4. Check the **Semester Health** card in the Studio dashboard to verify it summarizes the count of upcoming deliverables in the **Next 7 Days** and **Next 30 Days**.

---

## 🚧 Not Yet Implemented

### Scenario: Stateful User Preference Memory
* **Status:** The data layer (`UserPreferenceMemoryRepository`, `logOverride`) exists but is not yet wired to any UI action. Deleting or rescheduling a study block does not currently log an override.
* **Intended behavior when implemented:** Deleting the same time-slot ≥2 times causes the AI to treat that slot as a restricted zone on the next study plan generation.
