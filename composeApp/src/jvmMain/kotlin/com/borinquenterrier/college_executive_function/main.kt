package com.borinquenterrier.college_executive_function

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "CollegeExecutiveFunction",
    ) {
        App()
    }
}