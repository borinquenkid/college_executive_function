package com.borinquenterrier.cef

/**
 * Lightweight facade for collision resolution.
 * Delegates to SchedulingAlgorithm, ConstraintValidator, and CollisionDetector.
 */
class CollisionResolver(
    maxDepth: Int = 3,
    val preferences: StudyPreferences = StudyPreferences(),
    val userConstraints: List<UserPreferenceConstraint> = emptyList()
) {
    private val validator = ConstraintValidator(preferences, userConstraints)
    private val detector = CollisionDetector(preferences)
    private val algorithm = SchedulingAlgorithm(maxDepth, validator, detector)

    fun resolve(
        event: Event,
        existingEvents: List<Event>,
        depth: Int = 0
    ): ResolutionResult {
        return algorithm.resolve(event, existingEvents, depth)
    }
}
