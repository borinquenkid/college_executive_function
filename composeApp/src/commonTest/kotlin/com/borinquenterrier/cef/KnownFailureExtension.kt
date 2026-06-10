package com.borinquenterrier.cef

/**
 * Helper function for use with @KnownFailure tests.
 *
 * Usage in Kotest:
 * ```
 * @KnownFailure(
 *   issue = "https://github.com/borinquenkid/college_executive_function/issues/3",
 *   reason = "SchedulingAlgorithm not finding valid slots"
 * )
 * test("Priority Bump") {
 *   expectKnownFailure(
 *     issue = "issues/3",
 *     errorMessage = "Expected 3 events, got 2"
 *   ) {
 *     // Test code that's expected to fail
 *     dbEvents shouldHaveSize 3
 *   }
 * }
 * ```
 *
 * Strategy:
 * - Catches exceptions from @KnownFailure tests
 * - If test passes: throws error demanding annotation removal
 * - If test fails: passes silently (expected failure)
 * - Build succeeds but issue stays visible
 */
inline fun expectKnownFailure(
    issue: String,
    errorMessage: String = "",
    block: () -> Unit
) {
    try {
        block()
        // If we reach here, test PASSED when it should FAIL
        throw AssertionError(
            """
            ✓ TEST NOW PASSING - REMOVE @KnownFailure ANNOTATION!

            This test was expected to fail but now passes.

            Issue: $issue
            Expected error: $errorMessage

            ACTION: Remove @KnownFailure annotation and commit the fix.
            """.trimIndent()
        )
    } catch (e: AssertionError) {
        if (e.message?.contains("TEST NOW PASSING") == true) {
            throw e // Re-throw the "now passing" error
        }
        // Expected failure - swallow it (test passes)
    } catch (e: Exception) {
        // Any other exception is also expected - swallow it
    }
}
