package com.borinquenterrier.cef

/**
 * Marks a file as containing only Compose UI rendering code with no extractable domain logic.
 * Applied via `@file:UiOnly` so Kover excludes the file's synthetic class from the
 * coverage gate and report — these files cannot be meaningfully unit-tested without
 * a full Compose test harness.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FILE)
annotation class UiOnly
