package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class FragmentRankerTest : StringSpec({
    val ranker = FragmentRanker()

    "rankFragments returns empty for empty sources" {
        val result = ranker.rankFragments(emptyList(), "test question", topK = 5)
        result.shouldBeEmpty()
    }

    "rankFragments handles query with no content terms" {
        val fragment = SourceFragment("the a an", 1)
        val source = SourceItem("test", listOf(fragment), SourceCategory.SYLLABUS)

        val result = ranker.rankFragments(listOf(source), "the a an", topK = 5)

        result.shouldHaveSize(1)
    }

    "rankFragments ranks by term relevance" {
        val fragment1 = SourceFragment("introduction to computer science algorithms", 1)
        val fragment2 = SourceFragment("data structures and complexity", 2)
        val source = SourceItem("test", listOf(fragment1, fragment2), SourceCategory.SYLLABUS)

        val result = ranker.rankFragments(listOf(source), "algorithms complexity", topK = 2)

        result.shouldHaveSize(2)
    }

    "rankFragments respects topK limit" {
        val fragments = (1..10).map { SourceFragment("fragment $it content text", it) }
        val source = SourceItem("test", fragments, SourceCategory.SYLLABUS)

        val result = ranker.rankFragments(listOf(source), "fragment content", topK = 3)

        result.shouldHaveSize(3)
    }

    "rankFragments handles multiple sources" {
        val frags1 = listOf(SourceFragment("java programming language", 1))
        val frags2 = listOf(SourceFragment("python scripting language", 1))
        val source1 = SourceItem("java_book", frags1, SourceCategory.SYLLABUS)
        val source2 = SourceItem("python_book", frags2, SourceCategory.READING_MATERIAL)

        val result = ranker.rankFragments(listOf(source1, source2), "programming language", topK = 5)

        result.shouldHaveSize(2)
    }

    "rankFragments filters stop words from query" {
        val fragment = SourceFragment("the database system is efficient", 1)
        val source = SourceItem("test", listOf(fragment), SourceCategory.SYLLABUS)

        val result = ranker.rankFragments(listOf(source), "the database is efficient", topK = 5)

        result.shouldHaveSize(1)
    }

    "rankFragments scores term frequency correctly" {
        val fragment1 = SourceFragment("test test test content content", 1)
        val fragment2 = SourceFragment("test content other text", 2)
        val source = SourceItem("test", listOf(fragment1, fragment2), SourceCategory.SYLLABUS)

        val result = ranker.rankFragments(listOf(source), "test content", topK = 2)

        result.shouldHaveSize(2)
    }

    "rankFragments preserves original order on tie score" {
        val fragments = listOf(
            SourceFragment("alpha beta gamma", 1),
            SourceFragment("alpha beta gamma", 2),
            SourceFragment("alpha beta gamma", 3)
        )
        val source = SourceItem("test", fragments, SourceCategory.SYLLABUS)

        val result = ranker.rankFragments(listOf(source), "alpha beta", topK = 3)

        result.shouldHaveSize(3)
        result[0].second.pageNumber shouldBe 1
        result[1].second.pageNumber shouldBe 2
        result[2].second.pageNumber shouldBe 3
    }

    "rankFragments handles empty fragment text" {
        val fragment1 = SourceFragment("", 1)
        val fragment2 = SourceFragment("content here", 2)
        val source = SourceItem("test", listOf(fragment1, fragment2), SourceCategory.SYLLABUS)

        val result = ranker.rankFragments(listOf(source), "content", topK = 5)

        result.shouldHaveSize(2)
    }
})
