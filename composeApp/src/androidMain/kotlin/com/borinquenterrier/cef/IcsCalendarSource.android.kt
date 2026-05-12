package com.borinquenterrier.cef

actual class IcsCalendarSource actual constructor(private val icsContent: String) {
    actual suspend fun readSource(): List<SourceFragment> {
        return listOf(SourceFragment(text = icsContent, type = SourceType.CALENDAR))
    }
}
