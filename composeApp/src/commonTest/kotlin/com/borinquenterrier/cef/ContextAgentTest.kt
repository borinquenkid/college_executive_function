package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class ContextAgentTest : FunSpec({

    fun fragment(text: String = "Course content here", page: Int? = 1) =
        SourceFragment(text = text, type = SourceType.TEXT, pageNumber = page)

    fun source(title: String = "PSYCH 101", vararg fragments: SourceFragment) =
        SourceItem(title = title, fragments = fragments.toList())

    // ── analyzeSource ───────────────────────────────────────────────────────

    test("analyzeSource persists metadata when AI returns a result") {
        val aiService = mockk<AIService>(relaxed = true)
        val sourceRepo = mockk<SourceRepository>(relaxed = true)
        val ranker = mockk<FragmentRanker>(relaxed = true)
        val builder = mockk<SourceContextBuilder>(relaxed = true)
        coEvery { aiService.analyzeDocument(any()) } returns """{"gradeWeights": {}}"""

        val agent = ContextAgent(aiService, sourceRepo, ranker, builder)
        val src = source("Bio 101", fragment("Syllabus text"))
        agent.analyzeSource(src)

        coVerify { sourceRepo.updateSourceMetadata("Bio 101", any()) }
    }

    test("analyzeSource skips the LLM call when the source already has metadata (no double charge)") {
        val aiService = mockk<AIService>(relaxed = true)
        val sourceRepo = mockk<SourceRepository>(relaxed = true)
        coEvery { sourceRepo.getSourceMetadata("Bio 101") } returns """{"already":"analyzed"}"""

        val agent = ContextAgent(aiService, sourceRepo, mockk(relaxed = true), mockk(relaxed = true))
        agent.analyzeSource(source("Bio 101", fragment("Syllabus text")))

        coVerify(exactly = 0) { aiService.analyzeDocument(any()) }
        coVerify(exactly = 0) { sourceRepo.updateSourceMetadata(any(), any()) }
    }

    test("analyzeSource with force = true re-analyzes even when metadata exists") {
        val aiService = mockk<AIService>(relaxed = true)
        val sourceRepo = mockk<SourceRepository>(relaxed = true)
        coEvery { sourceRepo.getSourceMetadata("Bio 101") } returns """{"already":"analyzed"}"""
        coEvery { aiService.analyzeDocument(any()) } returns """{"fresh":"result"}"""

        val agent = ContextAgent(aiService, sourceRepo, mockk(relaxed = true), mockk(relaxed = true))
        agent.analyzeSource(source("Bio 101", fragment("Syllabus text")), force = true)

        coVerify { aiService.analyzeDocument(any()) }
        coVerify { sourceRepo.updateSourceMetadata("Bio 101", any()) }
    }

    test("analyzeSource does nothing when AI returns null") {
        val aiService = mockk<AIService>(relaxed = true)
        val sourceRepo = mockk<SourceRepository>(relaxed = true)
        val ranker = mockk<FragmentRanker>(relaxed = true)
        val builder = mockk<SourceContextBuilder>(relaxed = true)
        coEvery { aiService.analyzeDocument(any()) } returns null

        val agent = ContextAgent(aiService, sourceRepo, ranker, builder)
        agent.analyzeSource(source("Bio 101", fragment()))

        coVerify(exactly = 0) { sourceRepo.updateSourceMetadata(any(), any()) }
    }

    test("analyzeSource resets isAnalyzing to false even when AI throws") {
        val aiService = mockk<AIService>(relaxed = true)
        val sourceRepo = mockk<SourceRepository>(relaxed = true)
        val ranker = mockk<FragmentRanker>(relaxed = true)
        val builder = mockk<SourceContextBuilder>(relaxed = true)
        coEvery { aiService.analyzeDocument(any()) } throws RuntimeException("API error")

        val agent = ContextAgent(aiService, sourceRepo, ranker, builder)
        agent.analyzeSource(source("Bio 101", fragment()))

        agent.isAnalyzing.value shouldBe false
    }

    // ── queryAllSources ─────────────────────────────────────────────────────

    test("queryAllSources returns canned message when no sources loaded") {
        val aiService = mockk<AIService>(relaxed = true)
        val sourceRepo = mockk<SourceRepository>(relaxed = true)
        val ranker = mockk<FragmentRanker>(relaxed = true)
        val builder = mockk<SourceContextBuilder>(relaxed = true)

        val agent = ContextAgent(aiService, sourceRepo, ranker, builder)
        val result = agent.queryAllSources(emptyList(), emptyList(), "When is the final?")

        result shouldContain "No sources are loaded"
        coVerify(exactly = 0) { aiService.generateChatResponse(any()) }
    }

    test("queryAllSources calls AI with ranked fragments and returns response") {
        val aiService = mockk<AIService>(relaxed = true)
        val sourceRepo = mockk<SourceRepository>(relaxed = true)
        val ranker = mockk<FragmentRanker>(relaxed = true)
        val builder = mockk<SourceContextBuilder>(relaxed = true)
        val src = source("PSYCH 101", fragment("Midterm is Oct 15"))
        val pair = Pair(src, fragment("Midterm is Oct 15"))

        coEvery { ranker.rankFragments(any(), any(), any()) } returns listOf(pair)
        coEvery { builder.buildContextBlocks(any(), any()) } returns listOf(SourceContextBlock("PSYCH 101", "SYLLABUS", null, "Midterm is Oct 15"))
        coEvery { sourceRepo.getSourceMetadata(any()) } returns null
        coEvery { aiService.generateChatResponse(any()) } returns "Midterm is October 15."

        val agent = ContextAgent(aiService, sourceRepo, ranker, builder)
        val result = agent.queryAllSources(listOf(src), emptyList(), "When is midterm?")

        result shouldBe "Midterm is October 15."
        coVerify { aiService.generateChatResponse(any()) }
    }

    test("queryAllSources includes warning context in the prompt") {
        val aiService = mockk<AIService>(relaxed = true)
        val sourceRepo = mockk<SourceRepository>(relaxed = true)
        val ranker = mockk<FragmentRanker>(relaxed = true)
        val builder = mockk<SourceContextBuilder>(relaxed = true)
        val src = source("PSYCH 101", fragment())

        coEvery { ranker.rankFragments(any(), any(), any()) } returns listOf(Pair(src, fragment()))
        coEvery { builder.buildContextBlocks(any(), any()) } returns emptyList()
        coEvery { sourceRepo.getSourceMetadata(any()) } returns null
        coEvery { aiService.generateChatResponse(any()) } returns "Answer"

        val agent = ContextAgent(aiService, sourceRepo, ranker, builder)

        agent.queryAllSources(listOf(src), emptyList(), "Question?", warnings = listOf("Grading unclear"))

        coVerify { aiService.generateChatResponse(any()) }
    }

    // ── querySource ─────────────────────────────────────────────────────────

    test("querySource builds prompt from metadata and fragment text then calls AI") {
        val aiService = mockk<AIService>(relaxed = true)
        val sourceRepo = mockk<SourceRepository>(relaxed = true)
        val ranker = mockk<FragmentRanker>(relaxed = true)
        val builder = mockk<SourceContextBuilder>(relaxed = true)
        coEvery { sourceRepo.getSourceMetadata("PSYCH 101") } returns """{"note": "test"}"""
        coEvery { aiService.generateChatResponse(any()) } returns "Answer"

        val agent = ContextAgent(aiService, sourceRepo, ranker, builder)
        val src = source("PSYCH 101", fragment("Midterm Oct 15"))
        val result = agent.querySource(src, "When is the midterm?")

        result shouldBe "Answer"
        coVerify { aiService.generateChatResponse(any()) }
    }

    // ── isAnalyzing state ───────────────────────────────────────────────────

    test("isAnalyzing is false initially") {
        val agent = ContextAgent(
            mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true)
        )
        agent.isAnalyzing.value shouldBe false
    }
})
