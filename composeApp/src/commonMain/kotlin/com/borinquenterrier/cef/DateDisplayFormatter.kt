package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Renders a date as "Jul 5, 2026" instead of raw ISO "2026-07-05" — unambiguous in English
 * regardless of MM/DD vs DD/MM regional convention. Stopgap until real locale-aware date
 * formatting lands (see ROADMAP.md internationalization backlog).
 */
object DateDisplayFormatter {
    fun format(date: LocalDate): String {
        val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        return "$month ${date.day}, ${date.year}"
    }
}
