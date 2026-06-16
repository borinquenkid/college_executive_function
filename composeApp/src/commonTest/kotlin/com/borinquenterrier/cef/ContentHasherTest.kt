package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ContentHasherTest : StringSpec({
    "same input produces same hash" {
        val fragments = listOf(
            SourceFragment(text = "Hello world", pageNumber = 1),
            SourceFragment(text = "Second page", pageNumber = 2)
        )
        val hash1 = ContentHasher.hash(fragments)
        val hash2 = ContentHasher.hash(fragments)
        hash1 shouldBe hash2
    }

    "different input produces different hash" {
        val fragments1 = listOf(
            SourceFragment(text = "Hello world", pageNumber = 1)
        )
        val fragments2 = listOf(
            SourceFragment(text = "Hello world modified", pageNumber = 1)
        )
        val hash1 = ContentHasher.hash(fragments1)
        val hash2 = ContentHasher.hash(fragments2)
        hash1 shouldNotBe hash2
    }

    "empty fragments produces non-empty stable hash" {
        val hash1 = ContentHasher.hash(emptyList())
        val hash2 = ContentHasher.hash(emptyList())
        hash1 shouldNotBe ""
        hash1 shouldBe hash2
    }
})
