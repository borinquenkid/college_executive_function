package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

actual class AIService {
    actual suspend fun generateResponse(prompt: String): String {
        delay(2000) // Simulate a network request
        return "This is a dummy iOS response to the prompt: '$prompt'"
    }
}

@Composable
actual fun rememberAIService(): AIService {
    return remember { AIService() }
}
