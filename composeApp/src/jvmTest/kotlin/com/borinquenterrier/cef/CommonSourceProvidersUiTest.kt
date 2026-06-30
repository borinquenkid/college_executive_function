package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class CommonSourceProvidersUiTest {

    @Test
    fun testLocalFileSourceProviderSelectorUI() = runComposeUiTest {
        val mockIngestionAgent = mockk<IngestionAgent>(relaxed = true)
        val mockAiService = mockk<AIService>(relaxed = true)

        var addedSource: SourceItem? = null
        var dismissed = false

        val mockFilePicker: @Composable (onFilesSelected: (List<String>) -> Unit) -> Unit =
            { onFilesSelected ->
                onFilesSelected(listOf("mock/path/syllabus.pdf"))
            }

        val provider = LocalFileSourceProvider(
            ingestionAgent = mockIngestionAgent,
            aiService = mockAiService,
            filePicker = mockFilePicker
        )

        val expectedSource = SourceItem("syllabus.pdf", emptyList())
        coEvery { mockIngestionAgent.addLocalFile("mock/path/syllabus.pdf") } returns expectedSource

        setContent {
            provider.SelectorUI(
                onSourceAdded = { addedSource = it },
                onDismiss = { dismissed = true }
            )
        }

        waitUntil(timeoutMillis = 5000L) {
            addedSource != null
        }

        addedSource shouldBe expectedSource
        coVerify(exactly = 1) { mockIngestionAgent.addLocalFile("mock/path/syllabus.pdf") }
    }

    @Test
    fun testLocalFileSourceProviderSelectorUIDismiss() = runComposeUiTest {
        val mockIngestionAgent = mockk<IngestionAgent>(relaxed = true)
        val mockAiService = mockk<AIService>(relaxed = true)

        var dismissed = false

        val mockFilePicker: @Composable (onFilesSelected: (List<String>) -> Unit) -> Unit =
            { onFilesSelected ->
                onFilesSelected(emptyList())
            }

        val provider = LocalFileSourceProvider(
            ingestionAgent = mockIngestionAgent,
            aiService = mockAiService,
            filePicker = mockFilePicker
        )

        setContent {
            provider.SelectorUI(
                onSourceAdded = {},
                onDismiss = { dismissed = true }
            )
        }

        dismissed shouldBe true
    }

    @Test
    fun testUrlSourceProviderSelectorUIFlow() = runComposeUiTest {
        val mockIngestionAgent = mockk<IngestionAgent>(relaxed = true)
        val mockAiService = mockk<AIService>(relaxed = true)

        val provider = UrlSourceProvider(mockIngestionAgent, mockAiService)

        var addedSource: SourceItem? = null
        var dismissed = false

        val expectedSource = SourceItem("CS101 Webpage", emptyList())
        coEvery { mockIngestionAgent.addUrl("https://example.com/cs101") } returns expectedSource

        setContent {
            provider.SelectorUI(
                onSourceAdded = { addedSource = it },
                onDismiss = { dismissed = true }
            )
        }

        // Fill URL in TextField and click Add
        onNode(hasText("") and hasSetTextAction()).performTextInput("https://example.com/cs101")
        onNodeWithText("Add").performClick()

        waitUntil(timeoutMillis = 5000L) {
            addedSource != null
        }

        addedSource shouldBe expectedSource
        coVerify(exactly = 1) { mockIngestionAgent.addUrl("https://example.com/cs101") }
    }

    @Test
    fun testGoogleDriveSourceProviderSelectorUINoToken() = runComposeUiTest {
        val mockIngestionAgent = mockk<IngestionAgent>(relaxed = true)
        val mockDriveService = mockk<GoogleDriveService>(relaxed = true)
        val mockTokenRepository = mockk<GoogleTokenRepository>(relaxed = true)

        every { mockTokenRepository.getAccessToken() } returns null

        val provider =
            GoogleDriveSourceProvider(mockIngestionAgent, mockDriveService, mockTokenRepository)
        var dismissed = false

        setContent {
            provider.SelectorUI(
                onSourceAdded = {},
                onDismiss = { dismissed = true }
            )
        }

        // Check dialog shows "Google Account Required"
        onNodeWithText("Google Account Required").assertExists()
        onNodeWithText("OK").performClick()

        dismissed shouldBe true
    }

    @Test
    fun testGoogleDriveSourceProviderSelectorUIFlow() = runComposeUiTest {
        val mockIngestionAgent = mockk<IngestionAgent>(relaxed = true)
        val mockDriveService = mockk<GoogleDriveService>(relaxed = true)
        val mockTokenRepository = mockk<GoogleTokenRepository>(relaxed = true)

        every { mockTokenRepository.getAccessToken() } returns "mock-token"

        val driveFile = DriveFile("drive-id-123", "SyllabusDrive.pdf", "application/pdf")
        coEvery { mockDriveService.listFiles(any()) } returns listOf(driveFile)

        val expectedSource = SourceItem("SyllabusDrive.pdf", emptyList())
        coEvery { mockIngestionAgent.addDriveFile(driveFile) } returns expectedSource

        val provider =
            GoogleDriveSourceProvider(mockIngestionAgent, mockDriveService, mockTokenRepository)
        var addedSource: SourceItem? = null

        setContent {
            provider.SelectorUI(
                onSourceAdded = { addedSource = it },
                onDismiss = {}
            )
        }

        // Wait for files to load in DrivePickerDialog and click the file
        onNodeWithText("Select from Google Drive").assertExists()

        waitUntil(timeoutMillis = 5000L) {
            try {
                onNodeWithText("SyllabusDrive.pdf").assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }

        // Multi-select: clicking a row toggles selection; "Add (N)" confirms the batch.
        onNodeWithText("SyllabusDrive.pdf").performClick()
        waitUntil(timeoutMillis = 5000L) {
            try { onNodeWithText("Add (1)").assertExists(); true } catch (e: AssertionError) { false }
        }
        onNodeWithText("Add (1)").performClick()

        waitUntil(timeoutMillis = 5000L) {
            addedSource != null
        }

        addedSource shouldBe expectedSource
        coVerify(exactly = 1) { mockIngestionAgent.addDriveFile(driveFile) }
    }
}
