package com.borinquenterrier.cef

/**
 * Resolves collisions between locally-scheduled Study Blocks and the proposed merged
 * calendar, producing [SyncProposal.StudyBlockShift] proposals for any block that needs
 * to move (or that couldn't be placed without a collision).
 */
class StudyBlockShiftResolver(
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository? = null,
    private val preferencesRepository: PreferencesRepository? = null
) {

    suspend fun resolveShifts(localEvents: List<Event>, proposedBaseCalendar: List<Event>): List<SyncProposal.StudyBlockShift> {
        val localStudyBlocks = localEvents.filter { it.category == AcademicCategory.STUDY_BLOCK }
        val resolvedStudyBlocks = mutableListOf<Event>()
        val proposals = mutableListOf<SyncProposal.StudyBlockShift>()

        val preferences = preferencesRepository?.getPreferences() ?: StudyPreferences()
        val userConstraints = userPreferenceMemoryRepository?.getDerivedConstraints() ?: emptyList()
        val resolver = CollisionResolver(preferences = preferences, userConstraints = userConstraints)

        localStudyBlocks.forEach { studyBlock ->
            val collides = proposedBaseCalendar.any { it.overlaps(studyBlock) } ||
                           resolvedStudyBlocks.any { it.overlaps(studyBlock) }

            if (!collides) {
                resolvedStudyBlocks.add(studyBlock)
                return@forEach
            }

            val collidingEvent = findCollidingEvent(proposedBaseCalendar, resolvedStudyBlocks, studyBlock)
            val currentCalendarState = proposedBaseCalendar + resolvedStudyBlocks
            val result = resolver.resolve(studyBlock, currentCalendarState)

            if (result is ResolutionResult.Success) {
                val shifted = result.resolvedEvents.first()
                if (hasShifted(shifted, studyBlock)) {
                    proposals.add(SyncProposal.StudyBlockShift(studyBlock, shifted, collidingEvent))
                    resolvedStudyBlocks.addAll(result.resolvedEvents)
                } else {
                    resolvedStudyBlocks.add(studyBlock)
                }
            } else {
                proposals.add(SyncProposal.StudyBlockShift(studyBlock, studyBlock, collidingEvent))
                resolvedStudyBlocks.add(studyBlock)
            }
        }

        return proposals
    }

    private fun findCollidingEvent(proposedBaseCalendar: List<Event>, resolvedStudyBlocks: List<Event>, studyBlock: Event): Event =
        proposedBaseCalendar.firstOrNull { it.overlaps(studyBlock) }
            ?: resolvedStudyBlocks.firstOrNull { it.overlaps(studyBlock) }
            ?: studyBlock

    private fun hasShifted(shifted: Event, original: Event): Boolean =
        shifted.date != original.date ||
            (shifted is TimeEvent && original is TimeEvent && (shifted.startTime != original.startTime || shifted.endTime != original.endTime))
}
