package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SourceFragmentBatcherTest : FunSpec({

    fun frag(text: String, page: Int) = SourceFragment(text = text, pageNumber = page, type = SourceType.TEXT)

    // ── batch count and structure ─────────────────────────────────────────────

    test("empty list returns empty batches") {
        SourceFragmentBatcher.batch(emptyList()) shouldHaveSize 0
    }

    test("fragments <= BATCH_SIZE returns one batch") {
        val frags = (1..3).map { frag("page $it", it) }
        val batches = SourceFragmentBatcher.batch(frags)
        batches shouldHaveSize 1
        batches[0] shouldHaveSize 3
    }

    test("6 fragments with default settings produces 3 batches") {
        // BATCH_SIZE=3, OVERLAP=1: [0,1,2], [2,3,4], [4,5]
        val frags = (1..6).map { frag("page $it", it) }
        val batches = SourceFragmentBatcher.batch(frags)
        batches shouldHaveSize 3
    }

    test("all fragments from all batches cover the full document") {
        val frags = (1..6).map { frag("page $it", it) }
        val covered = SourceFragmentBatcher.batch(frags)
            .flatten()
            .map { it.pageNumber }
            .toSet()
        // Every page must appear at least once
        (1..6).forEach { page -> covered.contains(page) shouldBe true }
    }

    // ── contextOnly marking ───────────────────────────────────────────────────

    test("first batch has no contextOnly fragments") {
        val frags = (1..6).map { frag("page $it", it) }
        val firstBatch = SourceFragmentBatcher.batch(frags)[0]
        firstBatch.none { it.metadata["contextOnly"] == "true" } shouldBe true
    }

    test("first fragment of second batch is marked contextOnly") {
        val frags = (1..6).map { frag("page $it", it) }
        val secondBatch = SourceFragmentBatcher.batch(frags)[1]
        secondBatch[0].metadata["contextOnly"] shouldBe "true"
    }

    test("non-overlap fragments in second batch are not marked contextOnly") {
        val frags = (1..6).map { frag("page $it", it) }
        val secondBatch = SourceFragmentBatcher.batch(frags)[1]
        // index 1 and 2 are new content, not overlap
        secondBatch[1].metadata["contextOnly"] shouldNotBe "true"
        secondBatch[2].metadata["contextOnly"] shouldNotBe "true"
    }

    test("overlap fragment retains its original page number and text") {
        val frags = (1..6).map { frag("page $it", it) }
        val batches = SourceFragmentBatcher.batch(frags)
        // Batch 1 ends with page 3. Batch 2's first fragment is the page 3 overlap.
        val overlapInBatch1 = batches[0].last()
        val overlapInBatch2 = batches[1].first()
        overlapInBatch1.pageNumber shouldBe overlapInBatch2.pageNumber
        overlapInBatch1.text shouldBe overlapInBatch2.text
    }

    test("overlap fragment preserves pre-existing metadata alongside contextOnly") {
        val fragWithMeta = SourceFragment(
            text = "week anchors page",
            pageNumber = 3,
            type = SourceType.TEXT,
            metadata = mapOf("weekAnchors" to "Week 1: June 8-14")
        )
        val frags = (1..2).map { frag("page $it", it) } + fragWithMeta + (4..6).map { frag("page $it", it) }
        val batches = SourceFragmentBatcher.batch(frags)
        val overlapFrag = batches[1].first()
        overlapFrag.metadata["contextOnly"] shouldBe "true"
        overlapFrag.metadata["weekAnchors"] shouldBe "Week 1: June 8-14"
    }

    test("single fragment does not get contextOnly even with overlap parameter") {
        val frags = listOf(frag("only page", 1))
        val batches = SourceFragmentBatcher.batch(frags)
        batches shouldHaveSize 1
        batches[0][0].metadata["contextOnly"] shouldNotBe "true"
    }
})
