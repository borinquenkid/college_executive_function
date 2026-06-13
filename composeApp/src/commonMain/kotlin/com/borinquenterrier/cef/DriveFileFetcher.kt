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
}
