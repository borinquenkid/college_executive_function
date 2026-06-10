package com.borinquenterrier.cef

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

object WebSourceDeletion {
    suspend fun handleDeleteSource(call: ApplicationCall, id: String, container: DependencyContainer) {
        val sourceItem = getAllSourceItems(container).find { it.title == id }
        if (sourceItem == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Source not found"))
            return
        }
        container.sourceRepository.deleteSource(sourceItem.title)
        deleteSourceEvents(sourceItem.title, container)
        container.calendarAgent.synchronize("default")
        call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
    }

    private suspend fun deleteSourceEvents(sourceTitle: String, container: DependencyContainer) {
        val existingEvents = container.localRepository.getAllEvents("default")
        existingEvents.forEach { event ->
            val eventId = event.id ?: return@forEach
            if (shouldDeleteEvent(event, eventId, sourceTitle)) {
                container.localRepository.hardDeleteEvent(eventId, "default")
            }
        }
    }

    private fun shouldDeleteEvent(event: Event, eventId: String, sourceTitle: String): Boolean {
        return eventId.startsWith(sourceTitle) || event.warning?.contains(sourceTitle) == true
    }
}

