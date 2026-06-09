package com.borinquenterrier.cef

/**
 * Filters duplicate files based on existing URIs and within-batch duplicates.
 * Uses URI-based deduplication with fallback to file ID checking.
 */
class FileDuplicateFilter {

    /**
     * Filter out files that already exist (by URI) or are duplicates within the batch.
     *
     * @param files Files to deduplicate
     * @param existingUris URIs of files already known to the system
     * @return List of new files not seen before
     */
    fun filterDuplicates(files: List<DriveFile>, existingUris: Set<String>): List<DriveFile> {
        val seenIds = mutableSetOf<String>()
        return files.filter { file ->
            val uri = uriForFile(file.id)
            val isNew = !existingUris.contains(uri) && seenIds.add(file.id)
            isNew
        }
    }

    private fun uriForFile(fileId: String): String = "google_drive://$fileId"
}
