package com.borinquenterrier.cef

/**
 * Constructs Drive API queries for file filtering and builds MIME type criteria.
 * Centralizes query logic for maintainability and testability.
 */
class DriveQueryBuilder {

    fun buildQueryForFolder(folderId: String): String {
        return "'$folderId' in parents and " + buildMimeTypeCriteria()
    }

    private fun buildMimeTypeCriteria(): String {
        val mimeTypes = listOf(
            "mimeType = 'application/vnd.google-apps.document'",
            "mimeType = 'application/pdf'",
            "mimeType = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'",
            "mimeType = 'text/plain'",
            "name contains '.ics'"
        )
        return "(" + mimeTypes.joinToString(" or ") + ")"
    }
}
