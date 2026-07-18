package com.borinquenterrier.cef

import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import collegeexecutivefunction.composeapp.generated.resources.Res
import collegeexecutivefunction.composeapp.generated.resources.cef_icon
import org.jetbrains.compose.resources.painterResource

fun main() {
    installGlobalCrashHandler()
    Runtime.getRuntime().addShutdownHook(Thread { AppTracer.current.shutdown() })
    application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "College Executive Function",
        icon = painterResource(Res.drawable.cef_icon),
    ) {
        MenuBar {
            Menu("Edit") {
                Item(
                    "Cut",
                    onClick = {},
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(
                        androidx.compose.ui.input.key.Key.X,
                        meta = true
                    )
                )
                Item(
                    "Copy",
                    onClick = {},
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(
                        androidx.compose.ui.input.key.Key.C,
                        meta = true
                    )
                )
                Item(
                    "Paste",
                    onClick = {},
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(
                        androidx.compose.ui.input.key.Key.V,
                        meta = true
                    )
                )
                Item(
                    "Select All",
                    onClick = {},
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(
                        androidx.compose.ui.input.key.Key.A,
                        meta = true
                    )
                )
            }
        }
        App()
    }
    }
}