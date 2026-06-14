package com.borinquenterrier.cef

/**
 * Filters and deduplicates local files against existing URIs.
 * Centralizes deduplication logic for clarity and reusability.
 */
class LocalFileFilter {

    fun filterNewFiles(files: List<String>, existingUris: Set<String>): List<String> {
        val seenUris = mutableSetOf<String>()
        return files.filter { file ->
            val isNew = !existingUris.contains(file) && seenUris.add(file)
            isNew
        }
    }
}
