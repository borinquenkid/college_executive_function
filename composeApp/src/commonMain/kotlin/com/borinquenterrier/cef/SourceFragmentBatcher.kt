package com.borinquenterrier.cef

object SourceFragmentBatcher {
    const val BATCH_SIZE = 3
    const val OVERLAP = 1

    fun batch(
        fragments: List<SourceFragment>,
        batchSize: Int = BATCH_SIZE,
        overlap: Int = OVERLAP
    ): List<List<SourceFragment>> {
        if (fragments.isEmpty()) return emptyList()
        val batches = mutableListOf<List<SourceFragment>>()
        var startIndex = 0
        while (startIndex < fragments.size) {
            val endIndex = (startIndex + batchSize).coerceAtMost(fragments.size)
            batches.add(fragments.subList(startIndex, endIndex))
            if (endIndex == fragments.size) break
            val nextIndex = startIndex + batchSize - overlap
            if (nextIndex <= startIndex || nextIndex >= fragments.size) break
            startIndex = nextIndex
        }
        return batches
    }
}
