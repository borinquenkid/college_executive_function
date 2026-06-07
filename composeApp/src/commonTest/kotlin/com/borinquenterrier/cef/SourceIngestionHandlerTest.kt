package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class SourceIngestionHandlerTest : FunSpec({

    val ingestionAgent = mockk<IngestionAgent>()
    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = TestScope(testDispatcher)
    
    val handler = SourceIngestionHandler(ingestionAgent, testScope)

    test("ingestLocalFile handles success flow") {
        val mockSource = mockk<SourceItem>()
        coEvery { ingestionAgent.addLocalFile("test-path") } returns mockSource

        var started = false
        var successSource: SourceItem? = null
        var failed = false
        var finished = false

        handler.ingestLocalFile(
            path = "test-path",
            onStart = { started = true },
            onSuccess = { successSource = it },
            onFailure = { failed = true },
            onFinish = { finished = true }
        )

        started shouldBe true
        successSource shouldBe mockSource
        failed shouldBe false
        finished shouldBe true
    }

    test("ingestLocalFile handles failure flow") {
        coEvery { ingestionAgent.addLocalFile("test-path") } throws Exception("Failed")

        var started = false
        var successSource: SourceItem? = null
        var failed = false
        var finished = false

        handler.ingestLocalFile(
            path = "test-path",
            onStart = { started = true },
            onSuccess = { successSource = it },
            onFailure = { failed = true },
            onFinish = { finished = true }
        )

        started shouldBe true
        successSource shouldBe null
        failed shouldBe true
        finished shouldBe true
    }

    test("ingestUrl handles success flow") {
        val mockSource = mockk<SourceItem>()
        coEvery { ingestionAgent.addUrl("test-url") } returns mockSource

        var started = false
        var successSource: SourceItem? = null
        var failed = false
        var finished = false

        handler.ingestUrl(
            url = "test-url",
            onStart = { started = true },
            onSuccess = { successSource = it },
            onFailure = { failed = true },
            onFinish = { finished = true }
        )

        started shouldBe true
        successSource shouldBe mockSource
        failed shouldBe false
        finished shouldBe true
    }

    test("ingestUrl handles blank url flow") {
        var started = false
        var successSource: SourceItem? = null
        var failed = false
        var finished = false

        handler.ingestUrl(
            url = "   ",
            onStart = { started = true },
            onSuccess = { successSource = it },
            onFailure = { failed = true },
            onFinish = { finished = true }
        )

        started shouldBe false
        successSource shouldBe null
        failed shouldBe true
        finished shouldBe false
    }

    test("GoogleDriveQueryBuilder builds correct query") {
        val query = GoogleDriveQueryBuilder.buildIngestibleFilesQuery()
        query.contains("application/pdf") shouldBe true
        query.contains("name contains '.ics'") shouldBe true
    }
})
