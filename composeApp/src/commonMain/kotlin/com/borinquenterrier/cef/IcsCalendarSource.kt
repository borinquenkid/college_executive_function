package com.borinquenterrier.cef

expect class IcsCalendarSource(icsContent: String) {
    suspend fun extractChunks(): List<SourceChunk>
}
