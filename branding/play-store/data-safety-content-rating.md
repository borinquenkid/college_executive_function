# Play Console — Content Rating & Data Safety (draft answers)

> These are **attestations you submit**, so review each answer against the app's real
> behavior before entering it in Play Console. Answers below reflect CEF as of 2.0.3.
> Key fact that drives most of this: **CEF has no backend of its own.** Documents are
> processed via *the user's own* Google Gemini API key, and calendar events sync to
> *the user's own* Google Calendar. The only data CEF itself transmits is opt-in,
> anonymized crash diagnostics (the "Share Anonymous Bug Reports" toggle, **off by default**).

---

## Content Rating (IARC questionnaire)

- **App category:** Utility / Productivity (Education)
- Violence — **None**
- Sexual or suggestive content — **None**
- Profanity or crude humor — **None**
- Controlled substances (drugs/alcohol/tobacco) — **None**
- Gambling / simulated gambling — **None**
- Horror/fear themes — **None**
- **Users can interact / communicate:** No user-to-user interaction. The in-app "chat"
  is the user querying an AI over their **own** uploaded documents — not a social feature,
  no other users, no shared content.
- **Shares user location:** No
- **Digital purchases:** No
- **Unrestricted internet access:** Yes (the app makes network calls to Google Gemini and
  Google Calendar). Not a browser.

**Expected result:** Everyone / PEGI 3 (rated for all ages).

---

## Data Safety form

### 1. Does your app collect or share any required user data types?
**Yes** — but narrowly. Two distinct flows:

**A) Content processed via the user's own Google Gemini key (core feature).**
The syllabus/document text the user imports is sent to Google's Gemini API to extract
events. This is disclosed as **sharing** with a third party (Google) for app functionality.
- Data type: **Files and docs** (the imported syllabus/document text).
- Collected? For processing, transmitted to Google's API; CEF stores the *derived events*
  locally + in the user's Google Calendar. CEF keeps no copy on any CEF server (there is none).
- Shared? **Yes — with Google (Gemini API)**, to provide the extraction feature.
- Purpose: **App functionality.**
- Processed ephemerally? Largely yes (used to generate events), but declare conservatively
  as collected/shared for functionality.

**B) Opt-in anonymous crash diagnostics ("Share Anonymous Bug Reports", OFF by default).**
- Data types: **Crash logs**, **Diagnostics** (platform type, exception name + stack traces,
  anonymized telemetry counts like JSON-parse/rate-limit issues).
- Collected/shared? Collected only when the user turns it on.
- Purpose: **App functionality / analytics (crash diagnostics).**
- Linked to the user's identity? **No** — anonymized.
- Can the user request deletion / is it optional? **Optional** (toggle), and off by default.

### 2. Data types NEVER collected or shared (declare as such)
- Calendar event **contents, descriptions, or titles** — never sent to CEF; they live on the
  device and the user's own Google Calendar.
- Personal academic **syllabus files / documents / raw text** — never sent to CEF's servers.
- **API keys, passwords, credentials, Google account tokens** — stored locally on device only.
- Name, email, contacts, location, financial info, health, photos, messages — **not collected.**

### 3. Security practices
- **Data encrypted in transit:** Yes (HTTPS to Google APIs).
- **Users can request data deletion:** The app stores data locally + in the user's own
  Google Calendar; "Reset Calendar" clears it. There is no CEF-server account to delete.

---

## Notes / open questions for review
- Confirm you're comfortable declaring imported document text as "shared with Google (Gemini)
  for functionality." That's the honest reading since the app posts document text to Gemini —
  even though it's the user's own key. Under-declaring third-party sharing is a common
  Play rejection reason, so err toward disclosure.
- The bug-reporting toggle wording in the app already matches this (What is shared / What is
  NEVER shared) — keep the Data Safety answers consistent with that in-app copy.
