package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class SourcesPanelTest : FunSpec({

    test("New source is added to the list") {
        val initialSources = listOf(
            SourceItem("Syllabus", "CS 101 Syllabus"),
            SourceItem("Calendar", "Fall 2024 Calendar")
        )
        var updatedSources = initialSources

        val newSource = SourceItem("New Document", "This is a new document")

        // Simulate the onSourceAdded callback
        val onSourceAdded = { source: SourceItem ->
            updatedSources = updatedSources + source
        }

        onSourceAdded(newSource)

        updatedSources.size shouldBe 3
        updatedSources shouldContain newSource
    }
})
