package com.borinquenterrier.cef

import io.ktor.http.*

/**
 * Categorizes and handles errors from Gemini API responses.
 * Determines if errors are fatal, structural, or transient.
 */
class GeminiErrorHandler(
    private val modelNegotiator: GeminiModelNegotiator,
    private val logger: Logger?
) {
    private val tag = "GeminiErrorHandler"

    sealed class ErrorType {
        object Unauthorized : ErrorType()
        object Forbidden : ErrorType()
        data class StructuralError(val reason: String) : ErrorType()
        object TransientServerError : ErrorType()
        object QuotaExhausted : ErrorType()
        data class TransientRateLimit(val delayMs: Long) : ErrorType()
        data class OtherError(val message: String) : ErrorType()
    }

    fun categorizeError(status: HttpStatusCode, body: String): ErrorType {
        return when {
            status == HttpStatusCode.Unauthorized -> ErrorType.Unauthorized
            status == HttpStatusCode.Forbidden -> ErrorType.Forbidden
            status == HttpStatusCode.NotFound -> {
                logger?.d(tag, "Model not found - marking as structural error")
                ErrorType.StructuralError("Model not found (404)")
            }
            status == HttpStatusCode.BadRequest && body.contains("response modalities") -> {
                logger?.d(tag, "Model does not support text responses - marking as structural error")
                ErrorType.StructuralError("Response modalities not supported")
            }
            status == HttpStatusCode.ServiceUnavailable || status.value >= 500 -> {
                logger?.d(tag, "Server error ${status.value} - transient")
                ErrorType.TransientServerError
            }
            status == HttpStatusCode.TooManyRequests -> {
                if (isQuotaExhausted(body)) {
                    logger?.e(tag, "Daily quota exhausted")
                    ErrorType.QuotaExhausted
                } else {
                    val delayMs = extractRetryAfter(body)
                    logger?.d(tag, "Rate limit hit, retry after ${delayMs}ms")
                    ErrorType.TransientRateLimit(delayMs)
                }
            }
            !status.isSuccess() -> {
                logger?.e(tag, "Unexpected status ${status.value}")
                ErrorType.OtherError("API Error (${status.value}): $body")
            }
            else -> ErrorType.OtherError("Unknown error")
        }
    }

    private fun isQuotaExhausted(body: String): Boolean {
        val hasRetryHint = body.contains("retry in", ignoreCase = true)
        val hasQuotaWord = body.contains("quota", ignoreCase = true)
        val hasExhaustionWord = body.contains("exhausted", ignoreCase = true) ||
            body.contains("exceeded", ignoreCase = true) ||
            body.contains("limit", ignoreCase = true)
        return !hasRetryHint && hasQuotaWord && hasExhaustionWord
    }

    private fun extractRetryAfter(body: String): Long {
        val regex = Regex("""retry[^0-9]*(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(body)
        return if (match != null) {
            match.groupValues[1].toLongOrNull()?.let { it * 1000 } ?: 1000L
        } else {
            1000L
        }
    }

    fun handleStructuralError(modelName: String) {
        modelNegotiator.blacklistModel(modelName)
        modelNegotiator.evictFromCache(modelName)
    }

    fun handleServerError(modelName: String) {
        modelNegotiator.blacklistModel(modelName)
        modelNegotiator.evictFromCache(modelName)
    }
}
