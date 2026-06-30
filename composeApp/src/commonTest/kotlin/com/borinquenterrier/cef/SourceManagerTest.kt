package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay

class SourceManagerTest : FunSpec({

    test("loadSources restores persisted sources but never re-processes them") {
        val restored = SourceItem("restored.pdf", emptyList(), SourceCategory.SYLLABUS)
        val loader = mockk<SourceLoader>()
        coEvery { loader.loadSources() } returns listOf(restored)
        val adder = mockk<SourceAdder>(relaxed = true)
        val deleter = mockk<SourceDeleter>()
        val selector = SourceSelector()

        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        val manager = SourceManager(loader, adder, deleter, selector, GlobalScope)
        manager.loadSources()
        delay(300)

        manager.sourceItems.value.any { it.title == "restored.pdf" } shouldBe true
        // Reload is a pure DB read — processing stays once-per-source (on add), never on reload.
        coVerify(exactly = 0) { adder.addSource(any(), any()) }
    }

    test("loadSources merges with in-memory sources instead of clobbering them") {
        val added = SourceItem("just-added.pdf", emptyList(), SourceCategory.SYLLABUS)
        val persisted = SourceItem("persisted.pdf", emptyList(), SourceCategory.SYLLABUS)
        val loader = mockk<SourceLoader>()
        coEvery { loader.loadSources() } returns listOf(persisted)
        val adder = mockk<SourceAdder>(relaxed = true)

        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        val manager = SourceManager(loader, adder, mockk(), SourceSelector(), GlobalScope)
        manager.addSource(added)          // user adds one before reload completes
        manager.loadSources()
        delay(300)

        val titles = manager.sourceItems.value.map { it.title }
        titles.contains("just-added.pdf") shouldBe true   // not clobbered
        titles.contains("persisted.pdf") shouldBe true     // restored
    }
    test("should initialize with empty source items") {
        val loader = mockk<SourceLoader>()
        val adder = mockk<SourceAdder>()
        val deleter = mockk<SourceDeleter>()
        val selector = SourceSelector()

        val manager = SourceManager(loader, adder, deleter, selector, mockk())

        manager.sourceItems.value.shouldBeEmpty()
        manager.selectedSource.value.shouldBe(null)
    }

    test("should select source") {
        val loader = mockk<SourceLoader>()
        val adder = mockk<SourceAdder>()
        val deleter = mockk<SourceDeleter>()
        val selector = SourceSelector()

        val manager = SourceManager(loader, adder, deleter, selector, mockk())
        val source = SourceItem(
            title = "Syllabus.pdf",
            fragments = emptyList(),
            category = SourceCategory.SYLLABUS
        )

        manager.selectSource(source)

        manager.selectedSource.value shouldBe source
    }

    test("should add source to items") {
        val loader = mockk<SourceLoader>()
        val adder = mockk<SourceAdder>(relaxed = true)
        val deleter = mockk<SourceDeleter>()
        val selector = SourceSelector()

        val manager = SourceManager(loader, adder, deleter, selector, mockk())
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
        val loader = mockk<SourceLoader>()
        val adder = mockk<SourceAdder>(relaxed = true)
        val deleter = mockk<SourceDeleter>()
        val selector = SourceSelector()

        val manager = SourceManager(loader, adder, deleter, selector, mockk())
        val source = SourceItem(
            title = "Syllabus.pdf",
            fragments = emptyList(),
            category = SourceCategory.SYLLABUS
        )

        manager.addSource(source)

        manager.selectedSource.value shouldBe source
    }

    test("should not auto-select subsequent sources") {
        val loader = mockk<SourceLoader>()
        val adder = mockk<SourceAdder>(relaxed = true)
        val deleter = mockk<SourceDeleter>()
        val selector = SourceSelector()

        val manager = SourceManager(loader, adder, deleter, selector, mockk())
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
        val loader = mockk<SourceLoader>(relaxed = true)
        val adder = mockk<SourceAdder>(relaxed = true)
        val deleter = mockk<SourceDeleter>(relaxed = true)
        val selector = SourceSelector()

        val manager = SourceManager(loader, adder, deleter, selector, mockk())
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
