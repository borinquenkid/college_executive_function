package com.borinquenterrier.cef

enum class DriveFileType(val label: String) {
    GOOGLE_DOC("Google Doc"),
    PDF("PDF"),
    DOCX("DOCX"),
    ICS("ICS");

    companion object {
        fun from(file: DriveFile): DriveFileType? = when {
            file.mimeType == "application/vnd.google-apps.document" -> GOOGLE_DOC
            file.mimeType == "application/pdf" -> PDF
            file.mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DOCX
            file.name.endsWith(".ics", ignoreCase = true) -> ICS
            else -> null
        }
    }
}

class DriveFileFilter {
    fun filter(files: List<DriveFile>, query: String, type: DriveFileType?): List<DriveFile> =
        files
            .filter { matchesQuery(it, query) }
            .filter { type == null || matchesType(it, type) }

    fun sort(files: List<DriveFile>): List<DriveFile> =
        files.sortedBy { it.name.lowercase() }

    private fun matchesQuery(file: DriveFile, query: String): Boolean =
        query.isBlank() || file.name.contains(query, ignoreCase = true)

    private fun matchesType(file: DriveFile, type: DriveFileType): Boolean = when (type) {
        DriveFileType.GOOGLE_DOC -> file.mimeType == "application/vnd.google-apps.document"
        DriveFileType.PDF -> file.mimeType == "application/pdf"
        DriveFileType.DOCX -> file.mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        DriveFileType.ICS -> file.name.endsWith(".ics", ignoreCase = true)
    }
}
