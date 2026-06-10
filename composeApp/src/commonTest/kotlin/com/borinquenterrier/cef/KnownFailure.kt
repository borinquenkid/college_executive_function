package com.borinquenterrier.cef

import kotlin.annotation.AnnotationRetention

/**
 * Marks a test as a known failure that should be revisited when fixed.
 *
 * Usage:
 * ```
 * @KnownFailure(
 *   issue = "https://github.com/borinquenkid/college_executive_function/issues/3",
 *   reason = "SchedulingAlgorithm not finding valid slots for bumped events"
 * )
 * test("Priority Bump and Shift Cascade") { ... }
 * ```
 *
 * When the test is fixed:
 * 1. The test will PASS but the annotation will cause it to FAIL
 * 2. Error message: "Test marked @KnownFailure is now passing - remove the annotation!"
 * 3. Developer must explicitly remove the annotation
 * 4. This prevents silent fixes of known issues
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class KnownFailure(
    val issue: String,
    val reason: String
)
