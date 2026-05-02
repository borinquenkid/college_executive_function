package com.borinquenterrier.cef

actual class IcsCalendarSource actual constructor(private val icsContent: String) {
    actual suspend fun readSource(): List<SourcePart> {
        return listOf(SourcePart(text = icsContent, type = SourceType.CALENDAR))
    }
}
