package com.borinquenterrier.cef

object StudioStatusFormatter {

    fun format(
        statusMessage: String,
        isLoading: Boolean,
        pendingRequests: Int,
        remainingSeconds: Int
    ): String {
        if (!isLoading || pendingRequests <= 1) return statusMessage
        val timeStr = when {
            remainingSeconds >= 60 -> "~${remainingSeconds / 60}m ${remainingSeconds % 60}s remaining"
            remainingSeconds > 0   -> "~${remainingSeconds}s remaining"
            else                   -> ""
        }
        val timePart = if (timeStr.isNotEmpty()) ", $timeStr" else ""
        return "$statusMessage ($pendingRequests requests queued$timePart)"
    }
}
