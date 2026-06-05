package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import com.borinquenterrier.cef.db.SourceEntity
import com.russhwolf.settings.MapSettings
import kotlinx.datetime.Clock

class AgentHarnessTest : FunSpec({

    lateinit var ingestionAgent: IngestionAgent
    lateinit var eventAgent: EventAgent
    lateinit var contextAgent: ContextAgent
    lateinit var calendarAgent: CalendarAgent
    lateinit var driveService: GoogleDriveService
    lateinit var tokenRepository: GoogleTokenRepository
    lateinit var fileReader: LocalFileReader
    lateinit var sourceRepository: SourceRepository
    lateinit var settings: MapSettings
    lateinit var logger: Logger
    lateinit var harness: AgentHarness

    beforeEach {
        ingestionAgent = mockk(relaxed = true)
        eventAgent = mockk(relaxed = true)
        contextAgent = mockk(relaxed = true)
        calendarAgent = mockk(relaxed = true)
        driveService = mockk(relaxed = true)
        tokenRepository = mockk(relaxed = true)
        fileReader = mockk(relaxed = true)
        sourceRepository = mockk(relaxed = true)
        settings = MapSettings()
        logger = mockk(relaxed = true)
        
        harness = AgentHarness(
            ingestionAgent,
            eventAgent,
            contextAgent,
            calendarAgent,
            driveService,
            tokenRepository,
            fileReader,
            sourceRepository,
            settings,
            logger
        )
    }

    test("skips run if 24 hours have not passed and force is false") {
        val now = Clock.System.now().toEpochMilliseconds()
        harness.setLastPollTime(now - 10000) // 10 seconds ago

        harness.runHarness(force = false)

        coVerify(exactly = 0) { sourceRepository.getAllSources() }
    }

    test("runs if 24 hours have passed or force is true") {
        coEvery { sourceRepository.getAllSources() } returns emptyList()
        coEvery { tokenRepository.hasTokens() } returns false

        harness.runHarness(force = true)

        coVerify(exactly = 1) { sourceRepository.getAllSources() }
        (harness.getLastPollTime() > 0L) shouldBe true
    }

    test("processes new local files sequentially through the pipeline") {
        harness.setWatchedLocalDirectories(listOf("/watched/dir"))
        coEvery { fileReader.listFiles("/watched/dir") } returns listOf("/watched/dir/syllabus.pdf")
        coEvery { sourceRepository.getAllSources() } returns emptyList()

        val mockSourceItem = SourceItem("syllabus.pdf", listOf(SourceFragment("text")), SourceCategory.SYLLABUS)
        coEvery { ingestionAgent.addLocalFile("/watched/dir/syllabus.pdf") } returns mockSourceItem
        coEvery { tokenRepository.hasTokens() } returns false

        harness.runHarness(force = true)

        coVerifySequence {
            // Check files and ingest
            fileReader.listFiles("/watched/dir")
            ingestionAgent.addLocalFile("/watched/dir/syllabus.pdf")
            
            // Process sequentially
            contextAgent.analyzeSource(mockSourceItem)
            eventAgent.extractDeliverables(mockSourceItem)
            eventAgent.pushToCalendar()
            eventAgent.generateStudyPlan(mockSourceItem)
            eventAgent.pushToCalendar()
            
            // Calendar sync at the end
            calendarAgent.synchronize("default")
        }
    }

    test("skips already ingested local files") {
        harness.setWatchedLocalDirectories(listOf("/watched/dir"))
        coEvery { fileReader.listFiles("/watched/dir") } returns listOf("/watched/dir/syllabus.pdf")
        
        val mockSource = mockk<SourceEntity>(relaxed = true)
        every { mockSource.originUri } returns "/watched/dir/syllabus.pdf"
        coEvery { sourceRepository.getAllSources() } returns listOf(mockSource)
        coEvery { tokenRepository.hasTokens() } returns false

        harness.runHarness(force = true)

        coVerify(exactly = 0) { ingestionAgent.addLocalFile(any()) }
        coVerify(exactly = 1) { calendarAgent.synchronize("default") }
    }

    test("scans multiple local directories and drive folders concurrently and aggregates results") {
        harness.setWatchedLocalDirectories(listOf("/dir1", "/dir2"))
        harness.setWatchedGDriveFolders(listOf("folder1", "folder2"))
        
        coEvery { fileReader.listFiles("/dir1") } coAnswers {
            kotlinx.coroutines.delay(50)
            listOf("/dir1/file1.pdf")
        }
        coEvery { fileReader.listFiles("/dir2") } coAnswers {
            kotlinx.coroutines.delay(50)
            listOf("/dir2/file2.docx")
        }
        coEvery { tokenRepository.hasTokens() } returns true
        coEvery { driveService.listFiles(any()) } coAnswers {
            kotlinx.coroutines.delay(50)
            listOf(DriveFile("drive1", "notes.txt", "text/plain"))
        }
        coEvery { sourceRepository.getAllSources() } returns emptyList()

        val mockSource1 = SourceItem("file1.pdf", emptyList(), SourceCategory.READING_MATERIAL)
        val mockSource2 = SourceItem("file2.docx", emptyList(), SourceCategory.READING_MATERIAL)
        val mockSource3 = SourceItem("notes.txt", emptyList(), SourceCategory.READING_MATERIAL)

        coEvery { ingestionAgent.addLocalFile("/dir1/file1.pdf") } returns mockSource1
        coEvery { ingestionAgent.addLocalFile("/dir2/file2.docx") } returns mockSource2
        coEvery { ingestionAgent.addDriveFile(any()) } returns mockSource3

        harness.runHarness(force = true)

        coVerify(exactly = 1) { fileReader.listFiles("/dir1") }
        coVerify(exactly = 1) { fileReader.listFiles("/dir2") }
        coVerify(exactly = 2) { driveService.listFiles(any()) }

        coVerify(exactly = 1) { ingestionAgent.addLocalFile("/dir1/file1.pdf") }
        coVerify(exactly = 1) { ingestionAgent.addLocalFile("/dir2/file2.docx") }
        coVerify(exactly = 1) { ingestionAgent.addDriveFile(match { it.id == "drive1" }) }
    }
})
