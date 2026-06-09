package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CalendarErrorFormatterTest : FunSpec({

    context("format() detects 401 Unauthorized errors") {
        test("should format 401 error code") {
            val error = Exception("401 Unauthorized")
            CalendarErrorFormatter.format(error) shouldBe "Your Google session has expired. Please disconnect and reconnect your Google account."
        }

        test("should format 'Unauthorized' message (case-insensitive)") {
            val error = Exception("The request is unauthorized")
            CalendarErrorFormatter.format(error) shouldBe "Your Google session has expired. Please disconnect and reconnect your Google account."
        }

        test("should format 'invalid_grant' error") {
            val error = Exception("Invalid grant: error=invalid_grant")
            CalendarErrorFormatter.format(error) shouldBe "Your Google session has expired. Please disconnect and reconnect your Google account."
        }
    }

    context("format() detects 403 Forbidden errors") {
        test("should format 403 error code") {
            val error = Exception("403 Forbidden")
            CalendarErrorFormatter.format(error) shouldBe "Access denied. Please ensure the Google Calendar API is enabled in your Google Cloud Project."
        }

        test("should format 'Forbidden' message (case-insensitive)") {
            val error = Exception("The Calendar API is forbidden for this user")
            CalendarErrorFormatter.format(error) shouldBe "Access denied. Please ensure the Google Calendar API is enabled in your Google Cloud Project."
        }
    }

    context("format() detects Google API errors") {
        test("should extract message before newline and curly braces") {
            val error = Exception("Google API Error: Invalid calendar\n{\n  \"code\": 404\n}")
            CalendarErrorFormatter.format(error) shouldBe "Google API Error: Invalid calendar"
        }

        test("should extract message before opening brace when JSON block starts immediately") {
            val error = Exception("Calendar not found{\n  \"error\": \"notFound\"\n}")
            CalendarErrorFormatter.format(error) shouldBe "Calendar not found"
        }

        test("should fallback to generic message when extraction leaves empty string") {
            val error = Exception("{\n  \"error\": \"invalidResource\"\n}")
            CalendarErrorFormatter.format(error) shouldBe "Google API error. Please reconnect your account."
        }
    }

    context("format() handles generic errors") {
        test("should return raw message when no special pattern matches") {
            val error = Exception("Network timeout")
            CalendarErrorFormatter.format(error) shouldBe "Network timeout"
        }

        test("should return message when exception is null") {
            val error = Exception(null as String?)
            CalendarErrorFormatter.format(error) shouldBe "Unknown error"
        }
    }

    context("format() priority chain") {
        test("should prioritize 401 over other patterns") {
            val error = Exception("401 Error{\n  \"error\": \"forbidden\"\n}")
            CalendarErrorFormatter.format(error) shouldBe "Your Google session has expired. Please disconnect and reconnect your Google account."
        }

        test("should prioritize 403 over Google API pattern") {
            val error = Exception("403 Access Denied\nGoogle API Error")
            CalendarErrorFormatter.format(error) shouldBe "Access denied. Please ensure the Google Calendar API is enabled in your Google Cloud Project."
        }
    }

    context("format() with real error scenarios") {
        test("should handle OAuth2 token expiration message") {
            val error = Exception("invalid_grant: Token has been revoked")
            CalendarErrorFormatter.format(error) shouldBe "Your Google session has expired. Please disconnect and reconnect your Google account."
        }

        test("should handle calendar quota exceeded message") {
            val error = Exception("403 Forbidden: quotaExceeded")
            CalendarErrorFormatter.format(error) shouldBe "Access denied. Please ensure the Google Calendar API is enabled in your Google Cloud Project."
        }

        test("should handle API not enabled message") {
            val error = Exception("403 Forbidden: The Calendar API has not been used in project 123456")
            CalendarErrorFormatter.format(error) shouldBe "Access denied. Please ensure the Google Calendar API is enabled in your Google Cloud Project."
        }

        test("should handle network error gracefully") {
            val error = Exception("Connection refused: 127.0.0.1:443")
            CalendarErrorFormatter.format(error) shouldBe "Connection refused: 127.0.0.1:443"
        }
    }

})
