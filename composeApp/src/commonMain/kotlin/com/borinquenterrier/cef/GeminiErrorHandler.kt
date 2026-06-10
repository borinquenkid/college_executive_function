package com.borinquenterrier.cef

import io.ktor.http.HttpStatusCode

/**
 * Facade for error handling from Gemini API responses.
 * Delegates categorization to ErrorCategorizer and model handling to GeminiModelNegotiator.
 */
class GeminiErrorHandler(
    private val errorCategorizer: ErrorCategorizer,
    private val modelNegotiator: GeminiModelNegotiator,
    private val logger: Logger?
) {
    private val tag = "GeminiErrorHandler"

    typealias ErrorType = ErrorCategorizer.ErrorType

    fun categorizeError(status: HttpStatusCode, body: String): ErrorType {
        return errorCategorizer.categorizeError(status, body)
    }

    fun handleStructuralError(modelName: String) {
        logger?.d(tag, "Blacklisting model $modelName due to structural error")
        modelNegotiator.blacklistModel(modelName)
        modelNegotiator.evictFromCache(modelName)
    }

    fun handleServerError(modelName: String) {
        logger?.d(tag, "Blacklisting model $modelName due to server error")
        modelNegotiator.blacklistModel(modelName)
        modelNegotiator.evictFromCache(modelName)
    }
}
