package com.borinquenterrier.cef

/**
 * Manages Google Calendar synchronization logic and state.
 * Handles checking for sync proposals, applying negotiations, and error handling.
 */
class CalendarSyncManager(
    private val calendarAgent: CalendarAgent,
    private val logger: Logger
) {
    private val tag = "CalendarSyncManager"

    suspend fun initiateSyncIfNeeded(
        isGoogleLinked: Boolean,
        calendarId: String = "default"
    ): SyncNegotiation? {
        if (!isGoogleLinked) return null

        return try {
            val negotiation = calendarAgent.checkSyncProposals(calendarId)
            if (negotiation.proposals.isNotEmpty()) {
                logger.d(tag, "Found ${negotiation.proposals.size} sync proposals")
                negotiation
            } else {
                logger.d(tag, "No sync proposals, auto-applying")
                calendarAgent.applySyncNegotiation(negotiation, calendarId)
                null
            }
        } catch (e: CalendarNotFoundException) {
            logger.e(tag, "Calendar not found during sync init", e)
            throw e
        } catch (e: Exception) {
            logger.e(tag, "Sync initialization failed", e)
            null
        }
    }

    suspend fun applySyncProposal(
        negotiation: SyncNegotiation,
        calendarId: String = "default"
    ): Boolean {
        return try {
            calendarAgent.applySyncNegotiation(negotiation, calendarId)
            logger.d(tag, "Sync proposal applied successfully")
            true
        } catch (e: CalendarNotFoundException) {
            logger.e(tag, "Calendar not found during sync apply", e)
            throw e
        } catch (e: Exception) {
            logger.e(tag, "Failed to apply sync proposal", e)
            false
        }
    }

    suspend fun refreshEvents(calendarId: String = "default"): List<Event> {
        return try {
            calendarAgent.getEvents(calendarId)
        } catch (e: Exception) {
            logger.e(tag, "Failed to refresh events", e)
            emptyList()
        }
    }
}
