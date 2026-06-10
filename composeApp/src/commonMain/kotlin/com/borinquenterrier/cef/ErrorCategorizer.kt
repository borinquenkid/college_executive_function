package com.borinquenterrier.cef

import io.ktor.http.*

/**
 * Categorizes HTTP errors from Gemini API into semantic error types.
 * Delegates specialized detection (quota, retry-after) to focused services.
 */
class ErrorCategorizer(
    private val quotaDetector: QuotaExhaustionDetector,
    private val retryAfterParser: RetryAfterParser,
    private val logger: Logger?
) {
    private val tag = "ErrorCategorizer"

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
                if (quotaDetector.isQuotaExhausted(body)) {
                    logger?.e(tag, "Daily quota exhausted")
                    ErrorType.QuotaExhausted
                } else {
                    val delayMs = retryAfterParser.extractRetryAfterMs(body)
                    logger?.d(tag, "Rate limit hit, retry after ${delayMs}ms")
                    ErrorType.TransientRateLimit(delayMs)
                }
            }
            !status.isSuccess() -> {
                logger?.e(tag, "Unexpected status ${status.value}: $body")
                ErrorType.OtherError("API Error (${status.value}): $body")
            }
            else -> {
                logger?.e(tag, "Categorization fell through. Status: ${status.value}, Body: $body")
                ErrorType.OtherError("Unknown error. Status: ${status.value}, Response: ${body.take(200)}")
            }
        }
    }
}
