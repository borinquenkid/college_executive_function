@file:UiOnly
package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.borinquenterrier.cef.ui.theme.CollegeExecutiveFunctionTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    val settings = rememberSettings()
    val logger = rememberLogger()
    val driverFactory = rememberDriverFactory()
    val modelBasePath = rememberModelDirectoryPath()
    val fileReader = rememberLocalFileReader()
    val docxReader = rememberDocxReader()
    val pdfReader = rememberPdfReader()

    // Initialize the container off the main thread to prevent ANRs
    val containerState = produceState<DependencyContainer?>(
        initialValue = null,
        settings, logger, driverFactory, modelBasePath, fileReader, docxReader, pdfReader
    ) {
        value = withContext(Dispatchers.Default) {
            val c = DependencyContainer(
                settings,
                logger,
                driverFactory,
                modelBasePath,
                fileReader,
                docxReader,
                pdfReader
            )
            // Tracer must be live before anything else that could crash — otherwise a
            // DB-init failure happens while AppTracer.current is still NoopTracer.
            AppTracer.current = createTracer(settings, c.appEnv)
            AppTracer.current.span("app.startup") {}
            // Pre-trigger database initialization to ensure it happens off-thread
            c.database
            println("[App] Core services initialized off-thread.")
            c
        }
    }

    val container = containerState.value

    CollegeExecutiveFunctionTheme {
        if (container == null) {
            // Renders before AppContent's Scaffold exists, so it needs its own inset
            // handling — otherwise it draws under the status/nav bars during startup.
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            AppContent(container)
        }
    }
}
