package com.borinquenterrier.cef

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ConversationsPanelTest {

    private fun sample() = listOf(
        Conversation.create(createdAt = 1L, title = "Financial Aid"),
        Conversation.create(createdAt = 2L, title = "Syllabus questions")
    )

    @Test
    fun rendersConversationTitles() = runComposeUiTest {
        val convos = sample()
        setContent {
            ConversationsPanel(convos, convos[0].id, {}, {}, { _, _ -> }, {})
        }
        onNodeWithText("Financial Aid").assertIsDisplayed()
        onNodeWithText("Syllabus questions").assertIsDisplayed()
    }

    @Test
    fun newChatButtonFiresOnNew() = runComposeUiTest {
        var newCalled = false
        val convos = sample()
        setContent {
            ConversationsPanel(convos, convos[0].id, {}, { newCalled = true }, { _, _ -> }, {})
        }
        onNodeWithTag("drawer_new_conversation_button").performClick()
        newCalled shouldBe true
    }

    @Test
    fun tappingRowFiresOnSelectWithThatId() = runComposeUiTest {
        var selected: String? = null
        val convos = sample()
        setContent {
            ConversationsPanel(convos, convos[0].id, { selected = it }, {}, { _, _ -> }, {})
        }
        onNodeWithText("Syllabus questions").performClick()
        selected shouldBe convos[1].id
    }

    @Test
    fun renameFlowFiresOnRenameWithTrimmedTitle() = runComposeUiTest {
        var renamedId: String? = null
        var renamedTitle: String? = null
        val convos = listOf(Conversation.create(createdAt = 1L, title = "Old"))
        setContent {
            ConversationsPanel(convos, convos[0].id, {}, {}, { id, t -> renamedId = id; renamedTitle = t }, {})
        }
        onNodeWithContentDescription("Chat options").performClick()
        onNodeWithText("Rename").performClick()
        onNodeWithTag("rename_conversation_field").performTextReplacement("  New Title  ")
        onNodeWithTag("confirm_rename_conversation").performClick()

        renamedId shouldBe convos[0].id
        renamedTitle shouldBe "New Title"
    }

    @Test
    fun deleteFlowRequiresConfirmThenFiresOnDelete() = runComposeUiTest {
        var deletedId: String? = null
        val convos = listOf(Conversation.create(createdAt = 1L, title = "Doomed"))
        setContent {
            ConversationsPanel(convos, convos[0].id, {}, {}, { _, _ -> }, { deletedId = it })
        }
        onNodeWithContentDescription("Chat options").performClick()
        onNodeWithText("Delete").performClick() // menu item -> opens confirm dialog

        deletedId shouldBe null // nothing deleted until confirmed

        onNodeWithTag("confirm_delete_conversation").performClick()
        deletedId shouldBe convos[0].id
    }
}
