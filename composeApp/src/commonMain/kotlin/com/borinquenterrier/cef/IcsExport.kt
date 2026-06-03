package com.borinquenterrier.cef

expect fun generateIcsString(events: List<Event>): String

expect fun writeIcsFile(content: String): String
