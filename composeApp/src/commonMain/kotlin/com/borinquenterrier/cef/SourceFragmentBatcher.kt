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
            val isFirstBatch = batches.isEmpty()
            val batch = fragments.subList(startIndex, endIndex).mapIndexed { idx, frag ->
                // The first fragment of every non-first batch is the overlap from the previous
                // batch. Tag it so the extraction prompt skips it — it was already processed.
                if (idx == 0 && !isFirstBatch) {
                    frag.copy(metadata = frag.metadata + ("contextOnly" to "true"))
                } else {
                    frag
                }
            }
            batches.add(batch)
            if (endIndex == fragments.size) break
            val nextIndex = startIndex + batchSize - overlap
            if (nextIndex <= startIndex || nextIndex >= fragments.size) break
            startIndex = nextIndex
        }
        return batches
    }
}
