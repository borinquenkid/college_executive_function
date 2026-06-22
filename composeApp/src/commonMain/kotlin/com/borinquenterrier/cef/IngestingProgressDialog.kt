@file:UiOnly
package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlin.time.Clock

@Composable
fun IngestingProgressDialog(title: String, message: String) {
    val holdUntil by GeminiRetryService.globalHoldState.collectAsState()
    var secondsRemaining by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(holdUntil) {
        if (holdUntil == null) {
            secondsRemaining = null
            return@LaunchedEffect
        }
        while (true) {
            secondsRemaining = RetryCountdown.secondsRemaining(
                holdUntilMs = holdUntil!!,
                nowMs = Clock.System.now().toEpochMilliseconds()
            )
            if (secondsRemaining == null) break
            kotlinx.coroutines.delay(1000)
        }
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                if (secondsRemaining != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Retrying in ${secondsRemaining}s…")
                        Text(
                            "The Gemini API is busy. First-time setup can take a few minutes — please leave this open.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text(message)
                }
            }
        },
        confirmButton = {}
    )
}
