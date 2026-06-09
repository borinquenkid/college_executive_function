package com.borinquenterrier.cef

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Deletes a source and cleans up associated events from the calendar.
 * Handles event filtering by source title and calendar re-synchronization.
 */
class SourceDeleter(
    private val sourceRepository: SqlDelightSourceRepository,
    private val localRepository: SqlDelightLocalCalendarRepository,
    private val calendarAgent: CalendarAgent,
    private val logger: Logger,
    private val scope: CoroutineScope
) {
    fun deleteSource(source: SourceItem) {
        scope.launch {
            try {
                logger.d("SourceDeleter", "Deleting source: ${source.title}")
                sourceRepository.deleteSource(source.title)

                val existingEvents = localRepository.getAllEvents("default")
                existingEvents.forEach { event ->
                    val id = event.id
                    if (id != null && (id.startsWith(source.title) || event.warning?.contains(source.title) == true)) {
                        localRepository.hardDeleteEvent(id, "default")
                    }
                }

                calendarAgent.synchronize("default")
                logger.d("SourceDeleter", "Successfully deleted source: ${source.title}")
            } catch (e: Exception) {
                logger.e("SourceDeleter", "Failed to delete source: ${source.title}", e)
            }
        }
    }
}
