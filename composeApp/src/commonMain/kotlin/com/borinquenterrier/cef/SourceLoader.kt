package com.borinquenterrier.cef

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Loads sources from the database and reconstructs SourceItem objects with fragments.
 * Handles initial population of the source list and auto-selection of first item.
 */
class SourceLoader(
    private val sourceRepository: SqlDelightSourceRepository,
    private val logger: Logger,
    private val scope: CoroutineScope
) {
    suspend fun loadSources(): List<SourceItem> {
        return try {
            val sources = sourceRepository.getAllSources()
            sources.map { entity ->
                val fragments = sourceRepository.getFragmentsForSource(entity.id).map { frag ->
                    SourceFragment(
                        text = frag.text,
                        pageNumber = frag.pageNumber?.toInt(),
                        sectionTitle = frag.sectionTitle,
                        type = SourceType.valueOf(frag.type),
                        metadata = emptyMap()
                    )
                }
                SourceItem(
                    title = entity.title,
                    fragments = fragments,
                    category = SourceCategory.valueOf(entity.category)
                )
            }
        } catch (e: Exception) {
            logger.e("SourceLoader", "Failed to load sources from database", e)
            emptyList()
        }
    }
}
