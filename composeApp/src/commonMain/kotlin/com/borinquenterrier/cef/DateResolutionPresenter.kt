package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Pure logic behind the date-picker dialog that resolves [DateResolutionItem]s — deliverables
 * the pipeline caught with an ungrounded date. Keeps the Compose layer a thin shell.
 */
object DateResolutionPresenter {

    /** The evidence to show next to the picker: the source snippet, or a "likely invented" warning. */
    fun evidenceText(item: DateResolutionItem): String =
        item.sourceSnippet?.let { "From your source:\n\"$it\"" }
            ?: "No matching text was found in your source — this item may have been invented. " +
            "Confirm a date only if it is real."

    /** The confabulated date the picker should open on. */
    fun initialDate(item: DateResolutionItem): LocalDate = item.event.date

    /** Returns [event] re-dated to [date], preserving everything else. */
    fun withDate(event: Event, date: LocalDate): Event = when (event) {
        is TimeEvent -> event.copy(date = date)
        is DayEvent -> event.copy(date = date)
    }

    /**
     * User confirmed [date] for [item]: drop it from [pending] and add the re-dated event to
     * [pushList] (the list staged for the calendar). Returns the updated (pending, pushList).
     */
    fun confirm(
        pending: List<DateResolutionItem>,
        pushList: List<Event>,
        item: DateResolutionItem,
        date: LocalDate,
    ): Pair<List<DateResolutionItem>, List<Event>> =
        Pair(pending.filterNot { it == item }, pushList + withDate(item.event, date))

    /** User discarded [item] as confabulated: drop it from [pending], push list untouched. */
    fun discard(pending: List<DateResolutionItem>, item: DateResolutionItem): List<DateResolutionItem> =
        pending.filterNot { it == item }
}
