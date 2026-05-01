package com.borinquenterrier.cef

actual class IcsCalendarSource actual constructor(private val icsContent: String) {
    actual suspend fun extractChunks(): List<SourceChunk> {
        return listOf(SourceChunk(text = icsContent, type = SourceType.CALENDAR))
    }
}
