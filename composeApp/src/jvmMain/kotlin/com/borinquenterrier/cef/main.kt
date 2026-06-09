package com.borinquenterrier.cef

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

import androidx.compose.ui.window.MenuBar

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "CollegeExecutiveFunction",
    ) {
        MenuBar {
            Menu("Edit") {
                Item("Cut", onClick = {}, shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.X, meta = true))
                Item("Copy", onClick = {}, shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.C, meta = true))
                Item("Paste", onClick = {}, shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.V, meta = true))
                Item("Select All", onClick = {}, shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.A, meta = true))
            }
        }
        App()
    }
}