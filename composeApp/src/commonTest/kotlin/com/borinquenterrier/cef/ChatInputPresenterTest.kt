package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith

class ChatInputPresenterTest : FunSpec({

    context("placeholder") {
        test("prompts to add sources when all-sources mode and no sources") {
            ChatInputPresenter.placeholder(useAllSources = true, sourceCount = 0, selectedTitle = null) shouldBe
                    "Add sources to get started…"
        }

        test("shows source count in all-sources mode with sources") {
            val p = ChatInputPresenter.placeholder(useAllSources = true, sourceCount = 3, selectedTitle = null)
            p shouldContain "3 source(s)"
        }

        test("shows selected source title when scoped") {
            val p = ChatInputPresenter.placeholder(useAllSources = false, sourceCount = 2, selectedTitle = "Syllabus")
            p shouldContain "Syllabus"
        }

        test("truncates long selected source title to 20 chars") {
            val longTitle = "A".repeat(30)
            val p = ChatInputPresenter.placeholder(useAllSources = false, sourceCount = 1, selectedTitle = longTitle)
            p shouldContain "A".repeat(20)
            p shouldEndWith "…"
        }

        test("prompts to select source when scoped but none selected") {
            ChatInputPresenter.placeholder(useAllSources = false, sourceCount = 0, selectedTitle = null) shouldBe
                    "Select a source, or switch to All Sources mode…"
        }
    }

    context("chipLabel") {
        test("returns title unchanged when within limit") {
            ChatInputPresenter.chipLabel("Short", maxChars = 22) shouldBe "Short"
        }

        test("truncates to maxChars and appends ellipsis") {
            val title = "A".repeat(30)
            val result = ChatInputPresenter.chipLabel(title, maxChars = 22)
            result shouldBe "A".repeat(22) + "…"
        }

        test("title exactly at limit is not truncated") {
            val title = "A".repeat(22)
            ChatInputPresenter.chipLabel(title, maxChars = 22) shouldBe title
        }

        test("default maxChars is 22") {
            val title = "A".repeat(23)
            ChatInputPresenter.chipLabel(title) shouldBe "A".repeat(22) + "…"
        }
    }
})
