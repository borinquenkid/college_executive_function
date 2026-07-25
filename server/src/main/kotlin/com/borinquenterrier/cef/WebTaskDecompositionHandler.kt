package com.borinquenterrier.cef

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respondBytesWriter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Streams "Break it Down" task decomposition (Phase 6.5, ROADMAP.md) as an AG-UI SSE run —
 *  see SPEC.md 4.2's STATE_SNAPSHOT event for the result shape. */
object WebTaskDecompositionHandler {
    suspend fun handleDecomposeStream(call: ApplicationCall, eventId: String, container: DependencyContainer) {
        call.response.cacheControl(io.ktor.http.CacheControl.NoCache(null))

        call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
            val writer = SseEventWriter(this)
            writer.emit("RUN_STARTED", "{}")

            val event = container.calendarAgent.getEvents("default").find { it.id == eventId }
            if (event == null) {
                writer.emit(
                    "ERROR",
                    "{\"message\":\"No event found for id ${eventId.escapeJsonString()}\"}"
                )
            } else {
                writer.emit("TOOL_CALL_START", "{\"toolName\":\"decomposeTask\"}")
                val tasks = try {
                    container.taskDecompositionService.decompose(event)
                } catch (e: Throwable) {
                    println("[handleDecomposeStream] decomposeTask failed: ${e.message}")
                    emptyList()
                }
                writer.emit("TOOL_CALL_RESULT", "{\"toolName\":\"decomposeTask\",\"success\":true}")
                writer.emit(
                    "STATE_SNAPSHOT",
                    "{\"decomposedTasks\":${Json.encodeToString(tasks)}}"
                )
            }

            writer.emit("RUN_FINISHED", "{}")
        }
    }
}
