package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

expect class AIService {
    suspend fun generateResponse(prompt: String): String
}

@Composable
expect fun rememberAIService(): AIService
