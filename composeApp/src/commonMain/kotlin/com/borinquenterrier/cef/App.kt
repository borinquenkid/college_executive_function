package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.borinquenterrier.cef.ui.theme.CollegeExecutiveFunctionTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.tooling.preview.Preview

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
            // Pre-trigger database initialization to ensure it happens off-thread
            c.database
            println("[App] Core services initialized off-thread.")
            c
        }
    }

    val container = containerState.value

    CollegeExecutiveFunctionTheme {
        if (container == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            AppContent(container)
        }
    }
}
