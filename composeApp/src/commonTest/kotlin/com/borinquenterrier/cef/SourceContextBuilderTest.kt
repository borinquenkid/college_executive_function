package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class SourceContextBuilderTest : FunSpec({
    val builder = SourceContextBuilder()

    test("buildContextBlocks returns empty for no fragments") {
        val result = builder.buildContextBlocks(emptyList()) { null }
        result.shouldBeEmpty()
    }

    test("buildContextBlocks groups fragments by source") {
        val fragment1 = SourceFragment("content 1", 1)
        val fragment2 = SourceFragment("content 2", 2)
        val source = SourceItem("test_source", listOf(fragment1, fragment2), SourceCategory.SYLLABUS)
        val pairs = listOf(source to fragment1, source to fragment2)

        val result = builder.buildContextBlocks(pairs) { null }

        result.shouldHaveSize(1)
        result[0].title shouldBe "test_source"
    }

    test("buildContextBlocks sorts by category priority") {
        val syllabusFragment = SourceFragment("syllabus content", 1)
        val readingFragment = SourceFragment("reading content", 1)

        val syllabusSource = SourceItem("syllabus", listOf(syllabusFragment), SourceCategory.SYLLABUS)
        val readingSource = SourceItem("reading", listOf(readingFragment), SourceCategory.READING_MATERIAL)

        val pairs = listOf(
            readingSource to readingFragment,
            syllabusSource to syllabusFragment
        )

        val result = builder.buildContextBlocks(pairs) { null }

        result.shouldHaveSize(2)
        result[0].title shouldBe "syllabus"
        result[1].title shouldBe "reading"
    }

    test("buildContextBlocks formats fragments with page numbers") {
        val fragment = SourceFragment("content here", pageNumber = 5)
        val source = SourceItem("test", listOf(fragment), SourceCategory.SYLLABUS)
        val pairs = listOf(source to fragment)

        val result = builder.buildContextBlocks(pairs) { null }

        result.shouldHaveSize(1)
        result[0].fragmentText shouldBe "Page 5: content here"
    }

    test("buildContextBlocks formats fragments without page numbers") {
        val fragment = SourceFragment("content here", pageNumber = null)
        val source = SourceItem("test", listOf(fragment), SourceCategory.SYLLABUS)
        val pairs = listOf(source to fragment)

        val result = builder.buildContextBlocks(pairs) { null }

        result.shouldHaveSize(1)
        result[0].fragmentText shouldBe "content here"
    }

    test("buildContextBlocks fetches metadata for each source") {
        val fragment = SourceFragment("content", 1)
        val source = SourceItem("test_source", listOf(fragment), SourceCategory.SYLLABUS)
        val pairs = listOf(source to fragment)

        val metadataFetcher: suspend (String) -> String? = { sourceId ->
            if (sourceId == "test_source") "test metadata" else null
        }

        val result = builder.buildContextBlocks(pairs, metadataFetcher)

        result.shouldHaveSize(1)
        result[0].metadata shouldBe "test metadata"
    }

    test("buildContextBlocks handles null metadata gracefully") {
        val fragment = SourceFragment("content", 1)
        val source = SourceItem("test_source", listOf(fragment), SourceCategory.SYLLABUS)
        val pairs = listOf(source to fragment)

        val result = builder.buildContextBlocks(pairs) { null }

        result.shouldHaveSize(1)
        result[0].metadata shouldBe null
    }

    test("buildContextBlocks preserves category name") {
        val fragment = SourceFragment("content", 1)
        val source = SourceItem("test", listOf(fragment), SourceCategory.LAB_MANUAL)
        val pairs = listOf(source to fragment)

        val result = builder.buildContextBlocks(pairs) { null }

        result.shouldHaveSize(1)
        result[0].category shouldBe "LAB_MANUAL"
    }

    test("buildContextBlocks combines multiple fragments from same source") {
        val fragment1 = SourceFragment("content 1", 1)
        val fragment2 = SourceFragment("content 2", 2)
        val source = SourceItem("test", listOf(fragment1, fragment2), SourceCategory.SYLLABUS)
        val pairs = listOf(source to fragment1, source to fragment2)

        val result = builder.buildContextBlocks(pairs) { null }

        result.shouldHaveSize(1)
        result[0].fragmentText shouldBe "Page 1: content 1\n\nPage 2: content 2"
    }
})
