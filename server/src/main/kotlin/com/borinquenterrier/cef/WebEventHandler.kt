package com.borinquenterrier.cef

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

object WebEventHandler {
    suspend fun handleGetEvents(call: ApplicationCall, container: DependencyContainer) {
        val events = container.calendarAgent.getEvents("default")
        call.respond(events)
    }

    suspend fun handleSyncEvents(call: ApplicationCall, container: DependencyContainer) {
        container.calendarAgent.synchronize("default")
        call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
    }
}
