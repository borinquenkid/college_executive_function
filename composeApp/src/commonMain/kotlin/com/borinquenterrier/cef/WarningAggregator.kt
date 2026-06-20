package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

object WarningAggregator {

    fun collect(
        generatedEvents: List<Event>,
        persistedWarnings: List<String>,
        extractionWarning: String?,
        today: LocalDate
    ): List<String> {
        val semesterRange = WarningClassifier.activeSemesterFrom(generatedEvents, today)
        return (generatedEvents.mapNotNull { it.warning } + persistedWarnings + listOfNotNull(extractionWarning))
            .distinct()
            .map { WarningClassifier.classify(it, semesterRange) }
    }
}
