# ADR 0012: Decouple Source Upload from AI Processing (Async Ingestion + Progress Streaming)

## Status
Proposed

## Context
`POST /api/sources` (the web ingestion endpoint) currently does upload and AI processing as one
blocking unit inside a single HTTP request:

* `WebSourceHandler.processFileIngestion` (`server/src/main/kotlin/com/borinquenterrier/cef/WebSourceHandler.kt:52-64`)
  writes the upload to a **temp** directory, awaits `container.sourceProcessingPipeline
  .processSource(sourceItem)` in-line on the request's own coroutine, then deletes the temp
  directory in a `finally` block — the raw upload never persists past the request. `handleMultipartFile`
  (lines 41-44) only calls `call.respond(HttpStatusCode.OK, sourceItem)` after all of that completes.
* `SourceProcessingPipeline.processSource` (`composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/SourceProcessingPipeline.kt:20-36`)
  sequentially awaits `contextAgent.analyzeSource` → `eventAgent.extractDeliverables` →
  `eventAgent.pushToCalendar`, all on that same coroutine — no scope is spawned, nothing runs in
  the background.

For a real syllabus this holds the HTTP request open 20-30+ seconds. A live rehearsal (2026-07-23)
confirmed the resulting UX is a single "Uploading and parsing..." spinner with zero visibility into
which phase (upload vs. context analysis vs. extraction/critique vs. conflict resolution vs.
calendar write) is running — and a separate, now-fixed bug (an automatic `alert()` firing the
instant the `fetch()` resolved, see `web/src/App.tsx`) made the browser tab appear to hang for
several seconds afterward, which is what actually surfaced this architecture as worth fixing, not
just the individual bug.

**This is scoped to the HTTP ingestion path, not all four platforms equally.** Android/iOS/Desktop
invoke `SourceProcessingPipeline.processSource` in-process (no network hop) and already get live
phase text via `EventAgent.updateStatus()` (`EventAgent.kt:175-177`, backing a `_statusMessage`
`StateFlow` their Compose UI observes reactively) — they don't share the web client's specific
"one blocking request" problem. This ADR only changes the web (HTTP) path, though item 2 below
benefits the other platforms for free since it's the same shared pipeline class.

The user's framing for the fix: upload should be its own fast, acknowledged step with its own
progress; digestion should be a separately-tracked step with its own progress — not one
undifferentiated wait, and this should be the general shape for any client hitting this endpoint
over HTTP, present or future.

The server already has a working precedent for exactly this kind of granular progress streaming:
`GET /api/agent/stream` (`Application.kt:321+`) uses `call.respondBytesWriter(contentType =
ContentType.Text.EventStream)` with a small `emit(type, dataJson)` helper, and a
`CriticProgressListener` phase-callback (`CriticPhase.ACTOR_START`/`CRITIQUE_START`/etc.) to turn
an opaque multi-step chat operation into discrete SSE events for the web client's existing
`useAgentStream.ts` hook. This ADR reuses that pattern rather than inventing a second async
mechanism.

## Decision
1. **Split `/api/sources` into a fast persist step and a background digestion step.** The upload
   handler writes the bytes to durable per-tenant storage (not a temp dir deleted in `finally`),
   creates the `SourceItem` immediately with a pending status, and responds `202 Accepted` with the
   source id as soon as the bytes are safely stored — before any AI call happens.
2. **Add discrete phase markers to `SourceProcessingPipeline.processSource`**, alongside the
   `eventAgent.updateStatus(...)` calls already at lines 24/28-30 — `ANALYZING_CONTEXT`,
   `EXTRACTING_DELIVERABLES`, `RESOLVING_CONFLICTS`, `WRITING_CALENDAR`, `DONE`, `FAILED` — via a
   listener interface shaped like the existing `CriticProgressListener`.
3. **Launch the pipeline on an application-scoped `CoroutineScope`**, not the request's, so the
   `202` response in step 1 doesn't wait on it.
4. **Add `GET /api/sources/{id}/stream`**, built the same way as `/api/agent/stream` — same
   `respondBytesWriter`/`ContentType.Text.EventStream`/`emit` shape — streaming the phase
   transitions from step 2 as they occur.
5. **Web client changes from one blocking `await fetch()` to two tracked states**: `uploadFile`/
   `addSourceUrl` in `web/src/App.tsx` call `POST /api/sources` (now fast), then open a stream
   against `/api/sources/{id}/stream` mirroring `useAgentStream.ts`'s existing `EventSource`
   pattern, rendering "Uploaded" (done immediately) and a live digestion-phase label separately —
   replacing the single "Uploading and parsing..." spinner.
6. Android/iOS/Desktop need no client-side change for this ADR (see Context) but inherit the
   richer phase granularity from item 2 into their existing `_statusMessage` StateFlow for free.

## Alternatives Considered
* **Polling `GET /api/sources/{id}` instead of SSE.** Simpler, but the project already has a
  working SSE mechanism and a client hook shaped exactly for this (`useAgentStream.ts`) — reusing
  it beats introducing a second, different async pattern for a nearly identical problem.
* **A persisted job queue/table surviving server restarts**, instead of an in-memory
  application-scoped coroutine. Deferred, not rejected — the current deployment is a single,
  non-clustered server container (`docker-compose.yml`'s `server:` service), so losing an in-flight
  job on restart just means the user re-uploads, an acceptable failure mode today. Revisit if the
  server ever runs multiple replicas, or restart-during-ingestion becomes a real incident source.
* **A client-side simulated/fake progress bar** instead of real phase events. Rejected — doesn't
  fix the actual problem: still one blocking call under the hood, still no true visibility into
  what's actually happening, still the same robustness risk on slow networks or unusually large
  documents.

## Consequences

### Positive
* Matches the user's explicit framing: upload and digestion become independently visible,
  independently trackable steps instead of one undifferentiated wait.
* No more multi-second-to-multi-minute HTTP request held open for AI processing — removes a real
  timeout/robustness risk (slow network, large document, transient Gemini retries all currently
  extend the same single request).
* Reuses proven SSE infrastructure and an existing client hook shape instead of inventing a new
  mechanism.
* Android/iOS/Desktop status text gets more granular for free (item 2), with zero platform-specific
  work required for this ADR.

### Negative
* A source now has a real lifecycle (`PENDING` → digesting → `DONE`/`FAILED`) that the app must
  handle everywhere it's displayed — today's model has no such state machine at all, so this is new
  surface area, not just a progress-bar cosmetic change.
* A page reload mid-digestion needs to reconnect to the right stream (or re-derive current phase
  from persisted state) rather than just re-rendering already-complete data as today's model does.
* An application-scoped in-memory job is lost if the server restarts mid-digestion (see
  "Alternatives Considered") — must be documented as a known limitation, and the UI must treat a
  vanished job as a failure requiring re-upload rather than waiting forever on a stream that will
  never emit again.
