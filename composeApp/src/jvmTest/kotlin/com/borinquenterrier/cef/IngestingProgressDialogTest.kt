package com.borinquenterrier.cef

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * HARD-8: before a document large enough to route through the (slower, quota-heavier)
 * Gemini Files API, [IngestingProgressDialog] surfaces a dismissible notice — dismissing
 * it is purely cosmetic and never affects the ingestion the dialog is tracking.
 */
@OptIn(ExperimentalTestApi::class)
class IngestingProgressDialogTest {

    @Test
    fun noNoticeByDefault() = runComposeUiTest {
        setContent {
            IngestingProgressDialog(title = "Reading Document", message = "Extracting text...")
        }

        onNodeWithText("Extracting text...").assertExists()
        onNodeWithText(IngestionAgent.LARGE_DOCUMENT_NOTICE).assertDoesNotExist()
    }

    @Test
    fun showsNoticeWhenProvided() = runComposeUiTest {
        setContent {
            IngestingProgressDialog(
                title = "Reading Document",
                message = "Extracting text...",
                notice = IngestionAgent.LARGE_DOCUMENT_NOTICE
            )
        }

        onNodeWithText(IngestionAgent.LARGE_DOCUMENT_NOTICE).assertExists()
    }

    @Test
    fun dismissingTheNoticeHidesItButKeepsTheProgressIndicatorMessage() = runComposeUiTest {
        setContent {
            IngestingProgressDialog(
                title = "Reading Document",
                message = "Extracting text...",
                notice = IngestionAgent.LARGE_DOCUMENT_NOTICE
            )
        }

        onNodeWithText("Dismiss").performClick()

        onNodeWithText(IngestionAgent.LARGE_DOCUMENT_NOTICE).assertDoesNotExist()
        onNodeWithText("Extracting text...").assertExists()
    }
}
