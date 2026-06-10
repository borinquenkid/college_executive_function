package com.borinquenterrier.cef

/**
 * Facade coordinating concurrent folder fetching and file deduplication.
 * Delegates to specialized services for async orchestration and filtering.
 */
class DriveFileFetcher(
    private val folderFetcher: ConcurrentFolderFetcher,
    private val duplicateFilter: FileDuplicateFilter
) {

    /**
     * Fetch files from multiple folders and deduplicate against existing files.
     *
     * @param folderIds List of folder IDs to fetch from
     * @param existingUris URIs of files already in the system
     * @return Deduplicated list of new files
     */
    suspend fun fetchFromFolders(
        folderIds: List<String>,
        existingUris: Set<String> = emptySet()
    ): List<DriveFile> {
        val allFiles = folderFetcher.fetchFromFolders(folderIds)
        return duplicateFilter.filterDuplicates(allFiles, existingUris)
    }

    /**
     * Deduplicate files against existing URIs.
     * Kept for backward compatibility; prefer fetchFromFolders() which integrates both steps.
     *
     * @param files Files to deduplicate
     * @param existingUris URIs of files already known
     * @return Filtered list of new files
     */
    fun deduplicateFiles(files: List<DriveFile>, existingUris: Set<String>): List<DriveFile> {
        return duplicateFilter.filterDuplicates(files, existingUris)
    }
}
