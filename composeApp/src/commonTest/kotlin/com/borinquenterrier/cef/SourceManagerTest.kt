package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class SourceManagerTest : FunSpec({
    test("should initialize with empty source items") {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val container = mockk<DependencyContainer>()
        val callback = mockk<(List<Event>) -> Unit>(relaxed = true)
        
        val manager = SourceManager(container, testScope, callback)
        
        manager.sourceItems.value.shouldBeEmpty()
        manager.selectedSource.value.shouldBe(null)
    }

    test("should select source") {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val container = mockk<DependencyContainer>()
        val callback = mockk<(List<Event>) -> Unit>(relaxed = true)
        
        val manager = SourceManager(container, testScope, callback)
        val source = SourceItem(
            title = "Syllabus.pdf",
            fragments = emptyList(),
            category = SourceCategory.SYLLABUS
        )
        
        manager.selectSource(source)
        
        manager.selectedSource.value shouldBe source
    }

    test("should add source to items") {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val container = mockk<DependencyContainer>()
        val logger = mockk<Logger>(relaxed = true)
        coEvery { container.logger } returns logger
        coEvery { container.aiService.isConfigured() } returns false
        val callback = mockk<(List<Event>) -> Unit>(relaxed = true)
        
        val manager = SourceManager(container, testScope, callback)
        val source = SourceItem(
            title = "Syllabus.pdf",
            fragments = emptyList(),
            category = SourceCategory.SYLLABUS
        )
        
        manager.addSource(source)
        
        manager.sourceItems.value.shouldHaveSize(1)
        manager.sourceItems.value[0].title shouldBe "Syllabus.pdf"
    }

    test("should auto-select first source when added") {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val container = mockk<DependencyContainer>()
        val logger = mockk<Logger>(relaxed = true)
        coEvery { container.logger } returns logger
        coEvery { container.aiService.isConfigured() } returns false
        val callback = mockk<(List<Event>) -> Unit>(relaxed = true)
        
        val manager = SourceManager(container, testScope, callback)
        val source = SourceItem(
            title = "Syllabus.pdf",
            fragments = emptyList(),
            category = SourceCategory.SYLLABUS
        )
        
        manager.addSource(source)
        
        manager.selectedSource.value shouldBe source
    }

    test("should not auto-select subsequent sources") {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val container = mockk<DependencyContainer>()
        val logger = mockk<Logger>(relaxed = true)
        coEvery { container.logger } returns logger
        coEvery { container.aiService.isConfigured() } returns false
        val callback = mockk<(List<Event>) -> Unit>(relaxed = true)
        
        val manager = SourceManager(container, testScope, callback)
        val source1 = SourceItem(
            title = "Syllabus.pdf",
            fragments = emptyList(),
            category = SourceCategory.SYLLABUS
        )
        val source2 = SourceItem(
            title = "Notes.pdf",
            fragments = emptyList(),
            category = SourceCategory.LECTURE_NOTES
        )
        
        manager.addSource(source1)
        manager.addSource(source2)
        
        manager.selectedSource.value shouldBe source1
        manager.sourceItems.value.shouldHaveSize(2)
    }

    test("should handle clearing selection") {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val container = mockk<DependencyContainer>()
        val logger = mockk<Logger>(relaxed = true)
        coEvery { container.logger } returns logger
        coEvery { container.aiService.isConfigured() } returns false
        val callback = mockk<(List<Event>) -> Unit>(relaxed = true)
        
        val manager = SourceManager(container, testScope, callback)
        val source = SourceItem(
            title = "Syllabus.pdf",
            fragments = emptyList(),
            category = SourceCategory.SYLLABUS
        )
        
        manager.addSource(source)
        manager.selectSource(null)
        
        manager.selectedSource.value.shouldBe(null)
    }
})
