package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeEmpty

class TermNormalizerTest : FunSpec({
    val normalizer = TermNormalizer()

    test("extractQueryTerms removes stop words") {
        val result = normalizer.extractQueryTerms("what are the best study methods")

        result shouldContainExactlyInAnyOrder setOf("best", "study", "methods")
    }

    test("extractQueryTerms filters terms shorter than 3 characters") {
        val result = normalizer.extractQueryTerms("is it a good day")

        result shouldContainExactlyInAnyOrder setOf("good", "day")
    }

    test("extractQueryTerms keeps terms longer than 2 characters") {
        val result = normalizer.extractQueryTerms("calculus physics chemistry")

        result shouldContainExactlyInAnyOrder setOf("calculus", "physics", "chemistry")
    }

    test("extractQueryTerms handles mixed case") {
        val result = normalizer.extractQueryTerms("COMPUTER SCIENCE concepts")

        result shouldContainExactlyInAnyOrder setOf("computer", "science", "concepts")
    }

    test("extractQueryTerms handles punctuation and special characters") {
        val result = normalizer.extractQueryTerms("What's the relationship (cause & effect)?")

        result shouldContainExactlyInAnyOrder setOf("relationship", "cause", "effect")
    }

    test("tokenizeFragment splits text into words") {
        val result = normalizer.tokenizeFragment("machine learning algorithms")

        result shouldContainExactlyInAnyOrder listOf("machine", "learning", "algorithms")
    }

    test("tokenizeFragment handles punctuation") {
        val result = normalizer.tokenizeFragment("hello, world! how are you?")

        result shouldContainExactlyInAnyOrder listOf("hello", "world", "how", "are", "you")
    }

    test("tokenizeFragment converts to lowercase") {
        val result = normalizer.tokenizeFragment("UPPERCASE lowercase MiXeD")

        result shouldContainExactlyInAnyOrder listOf("uppercase", "lowercase", "mixed")
    }

    test("tokenizeFragment filters empty tokens") {
        val result = normalizer.tokenizeFragment("word1  word2   word3")

        result shouldContainExactlyInAnyOrder listOf("word1", "word2", "word3")
    }
})
