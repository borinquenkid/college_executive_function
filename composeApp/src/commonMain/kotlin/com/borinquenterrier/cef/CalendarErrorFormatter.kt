package com.borinquenterrier.cef

object CalendarErrorFormatter {
    fun format(e: Throwable): String {
        val msg = e.message ?: "Unknown error"
        return when {
            msg.contains("401") || msg.contains(
                "Unauthorized",
                ignoreCase = true
            ) || msg.contains("invalid_grant", ignoreCase = true) ->
                "Your Google session has expired. Please disconnect and reconnect your Google account."

            msg.contains("403") || msg.contains("Forbidden", ignoreCase = true) ->
                "Access denied. Please ensure the Google Calendar API is enabled in your Google Cloud Project."

            msg.startsWith("Google API Error") || msg.contains("{\n") -> {
                val cleanMsg = msg.substringBefore("\n").substringBefore("{")
                if (cleanMsg.isNotBlank()) cleanMsg.trim() else "Google API error. Please reconnect your account."
            }

            else -> msg
        }
    }
}
