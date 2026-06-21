package com.borinquenterrier.cef

class ResolvedEventWriter(
    private val repository: CalendarAgent,
    private val logger: Logger?
) {
    private val tag = "ResolvedEventWriter"

    suspend fun persist(
        resolvedList: List<Event>,
        calendarId: String,
        conflicts: MutableList<Event>,
        localOnlyExisting: List<Event> = emptyList()
    ): Pair<Int, Int> {
        var successCount = 0
        var localOnlyCount = 0
        for (resolved in resolvedList) {
            try {
                // Purge stale LOCAL_ONLY rows with the same title+date before re-saving.
                // LOCAL_ONLY events were never on remote, so hard-deleting them is safe.
                localOnlyExisting
                    .filter { it.title == resolved.title && it.date == resolved.date }
                    .mapNotNull { it.id }
                    .forEach { staleId -> repository.hardDeleteLocalOnly(staleId, calendarId) }
                repository.saveEvent(resolved, calendarId)
                successCount++
            } catch (e: OverlapException) {
                logger?.d(tag, "Conflict detected for: ${resolved.title}")
                conflicts.add(resolved)
            } catch (e: RemoteSyncFailedException) {
                logger?.d(tag, "Remote push failed, saved locally: ${resolved.title}")
                localOnlyCount++
            }
        }
        return Pair(successCount, localOnlyCount)
    }
}
