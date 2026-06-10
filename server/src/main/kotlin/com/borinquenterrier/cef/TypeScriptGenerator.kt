package com.borinquenterrier.cef

import kotlinx.serialization.serializer
import java.io.File

// ---------------------------------------------------------------------------
// Kotlin @Serializable → TypeScript interface generator
//
// Run via:  ./gradlew :server:generateTypescript
// Output:   web/src/cef-types.ts
//
// Single source of truth for shared API types. Never hand-edit the generated
// file; change the Kotlin @Serializable class instead and regenerate.
// ---------------------------------------------------------------------------

/** Generates a full TypeScript interface declaration for a CLASS-kind descriptor. */
internal fun generateTypescriptTypes(): String = buildString {
    appendLine("// AUTO-GENERATED — do not edit manually.")
    appendLine("// Source: Kotlin @Serializable classes in :composeApp and :server modules.")
    appendLine("// Regenerate: ./gradlew :server:generateTypescript")
    appendLine()

    val descriptors = listOf(
        serializer<StudyPreferences>().descriptor,
        serializer<RemoteCalendarMetadata>().descriptor,
        serializer<WebSettings>().descriptor,
    )

    for (desc in descriptors) {
        appendLine(desc.toTsInterface())
        appendLine()
    }
}

/** Entry point for the Gradle JavaExec task. */
fun main(args: Array<String>) {
    val outputDir = args.firstOrNull() ?: error("Usage: TypeScriptGenerator <output-dir>")
    val outFile = File(outputDir, "cef-types.ts")
    outFile.parentFile.mkdirs()
    outFile.writeText(generateTypescriptTypes())
    println("✅  Generated: ${outFile.absolutePath}")
}
