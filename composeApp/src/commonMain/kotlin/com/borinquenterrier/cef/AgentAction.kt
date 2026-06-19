package com.borinquenterrier.cef

internal interface AgentAction<in Input, out Result> {
    suspend fun run(input: Input, calendarId: String): Result
}
