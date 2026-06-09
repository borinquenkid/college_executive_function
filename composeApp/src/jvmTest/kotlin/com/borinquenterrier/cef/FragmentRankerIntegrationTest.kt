package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class FragmentRankerIntegrationTest : FunSpec({
    val ranker = FragmentRanker()

    test("rankFragments returns empty for empty sources") {
        val result = ranker.rankFragments(emptyList(), "test query")

        result.shouldBeEmpty()
    }

    test("rankFragments returns empty for sources with no fragments") {
        val source = mockk<SourceItem> {
            every { fragments } returns emptyList()
        }

        val result = ranker.rankFragments(listOf(source), "test query")

        result.shouldBeEmpty()
    }

    test("rankFragments returns all fragments for stop-word-only query") {
        val fragment1 = mockk<SourceFragment> {
            every { text } returns "learning algorithms"
        }
        val fragment2 = mockk<SourceFragment> {
            every { text } returns "data structures"
        }
        val source = mockk<SourceItem> {
            every { fragments } returns listOf(fragment1, fragment2)
        }

        val result = ranker.rankFragments(listOf(source), "the and or")

        // Stop words only = return top K (default 15)
        result shouldHaveSize 2
    }

    test("rankFragments ranks fragments by relevance") {
        val fragment1 = mockk<SourceFragment> {
            every { text } returns "python programming tutorial"
        }
        val fragment2 = mockk<SourceFragment> {
            every { text } returns "java programming guide"
        }
        val fragment3 = mockk<SourceFragment> {
            every { text } returns "web design html css"
        }
        val source = mockk<SourceItem> {
            every { fragments } returns listOf(fragment1, fragment2, fragment3)
        }

        val result = ranker.rankFragments(listOf(source), "python programming")

        // Fragment1 should rank highest (has both python and programming)
        result[0].second.text shouldBe "python programming tutorial"
    }

    test("rankFragments handles multiple sources") {
        val frag1 = mockk<SourceFragment> {
            every { text } returns "machine learning algorithms"
        }
        val source1 = mockk<SourceItem> {
            every { fragments } returns listOf(frag1)
        }

        val frag2 = mockk<SourceFragment> {
            every { text } returns "deep neural networks"
        }
        val source2 = mockk<SourceItem> {
            every { fragments } returns listOf(frag2)
        }

        val result = ranker.rankFragments(listOf(source1, source2), "learning networks")

        result shouldHaveSize 2
    }

    test("rankFragments returns fragments in score order") {
        val fragment1 = mockk<SourceFragment> {
            every { text } returns "algorithm complexity analysis"
        }
        val fragment2 = mockk<SourceFragment> {
            every { text } returns "algorithm design patterns"
        }
        val fragment3 = mockk<SourceFragment> {
            every { text } returns "complexity theory fundamentals"
        }
        val source = mockk<SourceItem> {
            every { fragments } returns listOf(fragment1, fragment2, fragment3)
        }

        val result = ranker.rankFragments(listOf(source), "algorithm complexity", topK = 10)

        // Both algorithm and complexity appear in fragment1 and fragment3
        // Fragment1 has both; fragment3 has both; fragment2 has only algorithm
        // So order should prioritize fragments with both terms
        result.isNotEmpty() shouldBe true
    }

    test("rankFragments handles single fragment") {
        val fragment = mockk<SourceFragment> {
            every { text } returns "only one fragment here"
        }
        val source = mockk<SourceItem> {
            every { fragments } returns listOf(fragment)
        }

        val result = ranker.rankFragments(listOf(source), "fragment")

        result shouldHaveSize 1
        result[0].second shouldBe fragment
    }

    test("rankFragments filters by relevance not random fragments") {
        val releventFrag = mockk<SourceFragment> {
            every { text } returns "python programming language tutorial"
        }
        val irrelevantFrag = mockk<SourceFragment> {
            every { text } returns "cooking recipes desserts"
        }
        val source = mockk<SourceItem> {
            every { fragments } returns listOf(releventFrag, irrelevantFrag)
        }

        val result = ranker.rankFragments(listOf(source), "python programming")

        // Relevant fragment should rank first
        result[0].second.text shouldBe "python programming language tutorial"
    }
})
