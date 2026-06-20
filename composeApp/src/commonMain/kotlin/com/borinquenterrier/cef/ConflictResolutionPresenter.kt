package com.borinquenterrier.cef

object ConflictResolutionPresenter {

    fun bodyText(count: Int): String =
        "The following $count event(s) conflict with your calendar and cannot be automatically " +
        "rescheduled.\n\nThese events require your professor's permission to reschedule:"

    fun instructionsText(): String =
        "1. Contact your professor regarding each conflicting event\n" +
        "2. Arrange a new time that works with both of you\n" +
        "3. Return here and manually update the event time"

    fun hasReason(reason: String): Boolean = reason.isNotBlank()
}
