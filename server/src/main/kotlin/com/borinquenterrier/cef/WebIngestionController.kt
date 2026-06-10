package com.borinquenterrier.cef

import io.ktor.server.application.ApplicationCall

object WebIngestionController {
    suspend fun handleGetSources(call: ApplicationCall, container: DependencyContainer) {
        WebSourceHandler.handleGetSources(call, container)
    }

    suspend fun handlePostSource(call: ApplicationCall, container: DependencyContainer) {
        WebSourceHandler.handlePostSource(call, container)
    }

    suspend fun handleDeleteSource(call: ApplicationCall, id: String, container: DependencyContainer) {
        WebSourceDeletion.handleDeleteSource(call, id, container)
    }

    suspend fun handleGetEvents(call: ApplicationCall, container: DependencyContainer) {
        WebEventHandler.handleGetEvents(call, container)
    }

    suspend fun handleSyncEvents(call: ApplicationCall, container: DependencyContainer) {
        WebEventHandler.handleSyncEvents(call, container)
    }

    suspend fun handleGetSettings(call: ApplicationCall, container: DependencyContainer) {
        WebSettingsHandler.handleGetSettings(call, container)
    }

    suspend fun handleSaveSettings(call: ApplicationCall, container: DependencyContainer) {
        WebSettingsHandler.handleSaveSettings(call, container)
    }

    suspend fun handleGetGoogleAuthStatus(call: ApplicationCall, container: DependencyContainer) {
        WebSettingsHandler.handleGetGoogleAuthStatus(call, container)
    }

    suspend fun handleGetCalendars(call: ApplicationCall, container: DependencyContainer) {
        WebSettingsHandler.handleGetCalendars(call, container)
    }

    suspend fun handleCreateCalendar(call: ApplicationCall, container: DependencyContainer) {
        WebSettingsHandler.handleCreateCalendar(call, container)
    }
}
