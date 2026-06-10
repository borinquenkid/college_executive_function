package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class FileDuplicateFilterTest : StringSpec({
    val filter = FileDuplicateFilter()

    "filters out files with existing URIs" {
        val file1 = DriveFile(id = "file1", name = "doc1.pdf", mimeType = "application/pdf")
        val file2 = DriveFile(id = "file2", name = "doc2.pdf", mimeType = "application/pdf")
        val files = listOf(file1, file2)
        val existingUris = setOf("google_drive://file1")

        val result = filter.filterDuplicates(files, existingUris)

        result shouldBe listOf(file2)
    }

    "keeps files not in existing URIs" {
        val file1 = DriveFile(id = "file1", name = "doc1.pdf", mimeType = "application/pdf")
        val file2 = DriveFile(id = "file2", name = "doc2.pdf", mimeType = "application/pdf")
        val files = listOf(file1, file2)
        val existingUris = emptySet<String>()

        val result = filter.filterDuplicates(files, existingUris)

        result shouldBe listOf(file1, file2)
    }

    "removes duplicates within batch" {
        val file1 = DriveFile(id = "file1", name = "doc1.pdf", mimeType = "application/pdf")
        val file1Dup = DriveFile(id = "file1", name = "doc1.pdf", mimeType = "application/pdf")
        val file2 = DriveFile(id = "file2", name = "doc2.pdf", mimeType = "application/pdf")
        val files = listOf(file1, file1Dup, file2)

        val result = filter.filterDuplicates(files, emptySet())

        result.size shouldBe 2
        result.map { it.id } shouldBe listOf("file1", "file2")
    }

    "handles empty file list" {
        val files = emptyList<DriveFile>()
        val existingUris = setOf("google_drive://file1")

        val result = filter.filterDuplicates(files, existingUris)

        result shouldBe emptyList()
    }

    "handles empty existing URIs" {
        val file1 = DriveFile(id = "file1", name = "doc1.pdf", mimeType = "application/pdf")
        val files = listOf(file1)

        val result = filter.filterDuplicates(files, emptySet())

        result shouldBe listOf(file1)
    }

    "preserves order of new files" {
        val files = listOf(
            DriveFile(id = "file1", name = "a.pdf", mimeType = "application/pdf"),
            DriveFile(id = "file2", name = "b.pdf", mimeType = "application/pdf"),
            DriveFile(id = "file3", name = "c.pdf", mimeType = "application/pdf")
        )
        val existingUris = setOf("google_drive://file2")

        val result = filter.filterDuplicates(files, existingUris)

        result.map { it.id } shouldBe listOf("file1", "file3")
    }

    "handles complex mix of duplicates and existing" {
        val files = listOf(
            DriveFile(id = "file1", name = "a.pdf", mimeType = "application/pdf"),
            DriveFile(
                id = "file1",
                name = "a.pdf",
                mimeType = "application/pdf"
            ), // duplicate in batch
            DriveFile(id = "file2", name = "b.pdf", mimeType = "application/pdf"),
            DriveFile(id = "file3", name = "c.pdf", mimeType = "application/pdf"),
            DriveFile(
                id = "file3",
                name = "c.pdf",
                mimeType = "application/pdf"
            )  // duplicate in batch
        )
        val existingUris = setOf("google_drive://file2")

        val result = filter.filterDuplicates(files, existingUris)

        result.map { it.id } shouldBe listOf("file1", "file3")
    }
})
