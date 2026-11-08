# ADR 0015: Auto-Push Extracted Events to Calendar Without a Confirm Step

## Status
Proposed (2026-11-07) — pending Walter's acceptance. Origin: T8 of `tasks/best-practices-review.md`.

## Context

A best-practices review flagged the "Rule of Two" exposure: untrusted syllabus text, private student
data, and an external action (Google Calendar write) meet in one flow. T8 traced the UI path and
confirmed there is **no human-confirm step** between extraction and the calendar write.

Traced paths (all reach `EventAgent.pushToCalendar()` → `CalendarPusher.push` →
`CalendarPushResolver.resolveAndPush`):

1. **Auto-push on source add** — `AppController.processSourceAutoPush` (also `reanalyzeSource`,
   `rebuildFromSources`) → `HarnessSourceProcessor.processSource` → `SourceProcessingPipeline.processSource`
   → `pushToCalendar()` (`SourceProcessingPipeline.kt:53`). No review boundary.
2. **Unattended entry points** — `AgentHarness` (local file / Drive polling) and the server's
   `WebSourceHandler` call the same pipeline.
3. **Manual Push button** — `StudioPanel` `push_calendar_button`; an explicit user action, no dialog.

Confirm dialogs exist only for deletes.

## Decision

Keep auto-push **without a blocking confirmation**. The product premise is a frictionless
syllabus-to-calendar flow; a per-source confirm would undo it. The exposure is bounded by existing
mitigations rather than a gate:

- **Grounding gate** (`GroundingGuard`, Confabulation Gate mandate) filters ungrounded/hallucinated dates before any push.
- **Dedup + conflict resolution** (`EventDeduplicator`, `ConflictResolver`) prevent duplicate or overwriting writes.
- **Scope**: writes go only to the user's own calendar; no invitations or messages to others.
- **Reversibility**: every pushed event is tagged with `sourceId`; deleting a source removes its events
  (`SourceDeleter`), and `CalendarReconciler` removes orphans.

## Consequences / Follow-ups (not yet implemented)

Not found in this review (grep-level check only; verify before building):

- A post-push summary listing what was added, with a one-tap undo.
- A per-source cap on events pushed, so an injected or malformed document cannot flood the calendar.

If either is wanted, run the Clarify Protocol before adding to `ROADMAP.md`.

## Revisit triggers

- Observed PDF-borne injection producing bogus calendar entries (also reopens ADR 0003's rejected CI scan).
- Any feature that makes the calendar write reach other people (invites, shared calendars).
