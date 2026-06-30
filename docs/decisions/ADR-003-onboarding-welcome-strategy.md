# ADR-003: Onboarding Welcome and Personalization Strategy

## Status
Accepted

## Date
2026-06-29

## Context

The College Executive Function (CEF) application integrates a student's personal academic data across multiple sources (Google Calendar, syllabi, class documents, and local routines) to construct a synchronized "Source of Truth." 

On first launch, or when connecting new data sources in the settings panel, users need a welcome experience that explains how the AI assistant functions, demonstrates its capabilities, and guides them on how to begin.

Students with executive function challenges can easily experience cognitive overload or anxiety when presented with:
1. A blank conversational chat interface ("what do I do now?").
2. Complex permission settings without clear context on why they are needed.
3. Unclear boundaries regarding how their private documents (e.g., syllabi, grades, notes) are parsed and stored.

We evaluated five welcome prompt archetypes for personalized assistants (Productivity Partner, Creative Muse, Trustworthy Guide, Insightful Synthesizer, and Showcase of Possibilities) to identify the best onboarding pattern for CEF.

---

## Decision

CEF formally adopts a hybrid onboarding welcome strategy combining the **"Transparent & Trustworthy Guide"** (FAQ-based radical transparency) and the **"Showcase of Possibilities"** (structured menus of choices):

1. **Radical Transparency (The Trustworthy Guide):**
   - The initial setup screen must immediately establish trust through a clear, question-and-answer formatted FAQ explaining data usage, security, and control:
     - **Q: Where is my data processed and stored?** 
     - **A:** All calendars, routines, and documents are stored locally on your device (in our SQLite/SQLDelight database). Syllabi are parsed securely using the Gemini API via the private key you provide.
     - **Q: Who has access to my documents?** 
     - **A:** Your documents and calendars are never shared or sold. You maintain full ownership and control.
     - **Q: Can I use the app offline?**
     - **A:** Yes. The calendar and routines function entirely offline, falling back to local TF-IDF rankers when embedding APIs are unavailable.
2. **Explicit Next Steps (The Showcase of Possibilities):**
   - To avoid the "blank canvas" syndrome, the landing screen will showcase a menu of 3 distinct, high-value academic pathways:
     1. **"Ingest a Course Syllabus"** (to automatically extract deliverables and deadlines).
     2. **"Set Up My Study Routine"** (to configure study hours, lunch/dinner breaks, and maximum workload constraints).
     3. **"Connect Google Calendar"** (to sync master calendar events).
   - This provides structured choices that directly support executive function (planning, initiation, and routing).
3. **Goldilocks Rule for Academic Insights:**
   - Any insight highlighted during onboarding must be low-stakes and strictly academic (e.g. "I noticed you uploaded 2 syllabi. Would you like me to map out your midterms week?").
   - We explicitly forbid scanning or synthesizing non-academic content (such as personal files or contacts) to prevent privacy concerns.

---

## Alternatives Considered

### The "Insightful Synthesizer" (Wow-Factor Cross-Source Connections)
- **Description:** Scanning files across all connected accounts (e.g. photos and emails) to synthesize complex personal connections.
- **Rejected:** Too invasive (violating the Goldilocks rule) and irrelevant to the academic domain of CEF. Students must feel secure that the app is not sniffing background activities.

### The "Inspirational Creative Muse" (Hobby-focused Engagement)
- **Description:** Focus on hobbies and creative inspiration.
- **Rejected:** While engaging, it distracts from the core goal of helping students manage executive function challenges for academic workload tracking.

---

## Consequences

### Positive
- **Reduced Cognitive Load:** Students are guided by a clear, structured list of startup actions, reducing initial friction.
- **Enhanced Trust:** Placing the transparency Q&A front-and-center matches the local-first, user-provided API key architecture of CEF.
- **Clear Expectations:** Explaining the system boundary prevents students from expecting the agent to be a magical genie, aligning with the Minion Ethos ([ADR-002](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/docs/decisions/ADR-002-adoption-of-minion-ethos-and-explicit-context.md)).

### Negative
- **UI Design Complexity:** Requires developing dedicated multi-choice launch cards and FAQ widgets in the `:composeApp` module rather than dropping the user straight into a simple chat layout.
