package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.ints.shouldBeLessThanOrEqual

class ConversationTitleTest : FunSpec({

    test("short messages are used verbatim after whitespace normalization") {
        ConversationTitle.fromFirstMessage("  When is  the exam? ") shouldBe "When is the exam?"
    }

    test("blank input falls back to the default title") {
        ConversationTitle.fromFirstMessage("   ") shouldBe Conversation.DEFAULT_TITLE
    }

    test("long messages are truncated on a word boundary with an ellipsis") {
        val long = "Can you explain the entire late-submission policy for every assignment please"

        val title = ConversationTitle.fromFirstMessage(long)

        title shouldEndWith "…"
        (title.length - 1) shouldBeLessThanOrEqual ConversationTitle.MAX_LENGTH
        title.contains("  ") shouldBe false
    }

    test("a single very long word is hard-cut rather than losing everything") {
        val title = ConversationTitle.fromFirstMessage("x".repeat(80))

        title shouldEndWith "…"
        (title.length - 1) shouldBeLessThanOrEqual ConversationTitle.MAX_LENGTH
    }
})
