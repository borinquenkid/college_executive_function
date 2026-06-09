package com.borinquenterrier.cef

class SourceContextBuilder {
    private val categoryPriority = mapOf(
        SourceCategory.SYLLABUS to 0,
        SourceCategory.LECTURE_NOTES to 1,
        SourceCategory.LAB_MANUAL to 2,
        SourceCategory.READING_MATERIAL to 3,
        SourceCategory.OTHER to 4
    )

    suspend fun buildContextBlocks(
        topFragmentPairs: List<Pair<SourceItem, SourceFragment>>,
        sourceMetadataFetcher: suspend (String) -> String?
    ): List<SourceContextBlock> {
        val groupedBySource = topFragmentPairs.groupBy { it.first }
        val sortedSourcesWithFragments = groupedBySource.keys.sortedBy { categoryPriority[it.category] ?: 5 }

        return sortedSourcesWithFragments.map { source ->
            val metadata = sourceMetadataFetcher(source.title)
            val pairsForSource = groupedBySource[source] ?: emptyList()
            val fragmentText = formatFragments(pairsForSource)

            SourceContextBlock(
                title = source.title,
                category = source.category.name,
                metadata = metadata,
                fragmentText = fragmentText
            )
        }
    }

    private fun formatFragments(pairsForSource: List<Pair<SourceItem, SourceFragment>>): String {
        return pairsForSource.joinToString("\n\n") { (_, fragment) ->
            if (fragment.pageNumber != null) "Page ${fragment.pageNumber}: ${fragment.text}"
            else fragment.text
        }
    }
}
